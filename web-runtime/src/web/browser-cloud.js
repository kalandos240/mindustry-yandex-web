(() => {
    'use strict';

    const root = document.documentElement;
    const CLOUD_KEY = 'mindustryWebCloudV1';
    const SETTINGS_KEY = 'mindustry.web.settings.v1';
    const REVISION_KEY = 'mindustry.web.cloud.local-revision.v1';
    const MAX_PAYLOAD_BYTES = 190 * 1024;
    const MIN_UPLOAD_INTERVAL_MS = 4000;
    const DIRTY_UPLOAD_DELAY_MS = 5000;

    let player = null;
    let initPromise = null;
    let monitorTimer = 0;
    let uploadTimer = 0;
    let uploadPromise = null;
    let lastStorageGeneration = 0;
    let lastSettingsValue = '';
    let dirtyRevision = 0;
    let uploadedRevision = 0;
    let lastUploadAt = 0;

    function mark(name, value){
        root.setAttribute(name, String(value));
    }

    function localRevision(){
        try{
            return Math.max(0, Number(localStorage.getItem(REVISION_KEY)) || 0);
        }catch(_ignored){
            return 0;
        }
    }

    function storeLocalRevision(value){
        try{ localStorage.setItem(REVISION_KEY, String(value)); }catch(_ignored){}
    }

    function settingsValue(){
        try{ return localStorage.getItem(SETTINGS_KEY) || ''; }catch(_ignored){ return ''; }
    }

    function byteSize(text){
        return new TextEncoder().encode(text).byteLength;
    }

    function isCloudSave(path){
        const value = String(path || '');
        if(!value.startsWith('saves/') || !value.endsWith('.msav')) return false;
        return !value.slice('saves/'.length).startsWith('ci-');
    }

    function encodeBytes(raw){
        const bytes = raw instanceof Uint8Array
            ? raw
            : new Uint8Array(raw.buffer, raw.byteOffset || 0, raw.byteLength);
        let binary = '';
        const chunk = 0x8000;
        for(let offset = 0; offset < bytes.length; offset += chunk){
            binary += String.fromCharCode(...bytes.subarray(offset, Math.min(bytes.length, offset + chunk)));
        }
        return btoa(binary);
    }

    function decodeBytes(encoded){
        const binary = atob(String(encoded || ''));
        const bytes = new Int8Array(binary.length);
        for(let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes;
    }

    function relevantPaths(storage){
        const paths = storage.paths().filter(isCloudSave);
        paths.sort((a, b) => {
            const delta = storage.updatedAt(b) - storage.updatedAt(a);
            if(delta) return delta;
            if(a === 'saves/web-local-survival.msav') return -1;
            if(b === 'saves/web-local-survival.msav') return 1;
            return a.localeCompare(b);
        });
        return paths;
    }

    function buildSnapshot(revision){
        const storage = globalThis.__mindustryStorage;
        const payload = {
            v: 1,
            updatedAt: revision,
            settings: settingsValue(),
            files: []
        };

        let skipped = 0;
        const envelope = value => JSON.stringify({[CLOUD_KEY]: value});
        if(byteSize(envelope(payload)) > MAX_PAYLOAD_BYTES){
            mark('data-yandex-cloud-state', 'oversized-settings');
            throw new Error('Mindustry cloud settings exceed the 190 KiB safety budget');
        }

        for(const path of relevantPaths(storage)){
            const data = storage.get(path);
            if(!data) continue;
            const entry = {
                p: path,
                t: storage.updatedAt(path),
                d: encodeBytes(data)
            };
            payload.files.push(entry);
            if(byteSize(envelope(payload)) > MAX_PAYLOAD_BYTES){
                payload.files.pop();
                skipped++;
            }
        }

        const bytes = byteSize(envelope(payload));
        mark('data-yandex-cloud-payload-bytes', bytes);
        mark('data-yandex-cloud-files', payload.files.length);
        mark('data-yandex-cloud-skipped-files', skipped);
        return payload;
    }

    function scheduleUpload(delay = DIRTY_UPLOAD_DELAY_MS){
        if(!player || dirtyRevision <= uploadedRevision) return;
        if(uploadTimer) clearTimeout(uploadTimer);
        uploadTimer = setTimeout(() => {
            uploadTimer = 0;
            upload(false).catch(error => {
                mark('data-yandex-cloud-state', 'error');
                console.warn('Mindustry cloud save failed:', error);
            });
        }, Math.max(0, delay));
    }

    function touch(reason){
        const now = Date.now();
        dirtyRevision = Math.max(dirtyRevision, now);
        storeLocalRevision(dirtyRevision);
        mark('data-yandex-cloud-dirty', reason || 'changed');
        scheduleUpload();
    }

    function scan(){
        const storage = globalThis.__mindustryStorage;
        if(!storage) return;

        const generation = storage.generation();
        const settings = settingsValue();
        let changed = false;
        if(generation !== lastStorageGeneration){
            lastStorageGeneration = generation;
            changed = true;
        }
        if(settings !== lastSettingsValue){
            lastSettingsValue = settings;
            changed = true;
        }
        if(changed) touch('local-change');
    }

    async function upload(flush){
        if(uploadPromise) return uploadPromise;
        if(!player || typeof player.setData !== 'function') return false;

        scan();
        const revision = dirtyRevision;
        if(!revision || revision <= uploadedRevision) return false;

        const sinceLast = Date.now() - lastUploadAt;
        if(sinceLast < MIN_UPLOAD_INTERVAL_MS){
            scheduleUpload(MIN_UPLOAD_INTERVAL_MS - sinceLast);
            return false;
        }

        let payload;
        try{
            payload = buildSnapshot(revision);
        }catch(error){
            mark('data-yandex-cloud-error', 'payload-too-large');
            return false;
        }

        mark('data-yandex-cloud-state', 'syncing');
        uploadPromise = Promise.resolve(player.setData({[CLOUD_KEY]: payload}, Boolean(flush)))
            .then(() => {
                uploadedRevision = revision;
                lastUploadAt = Date.now();
                mark('data-yandex-cloud-state', 'synced');
                mark('data-yandex-cloud-updated-at', revision);
                if(dirtyRevision > uploadedRevision) scheduleUpload();
                return true;
            })
            .catch(error => {
                mark('data-yandex-cloud-state', 'error');
                mark('data-yandex-cloud-error', String(error && error.name ? error.name : 'set-data'));
                throw error;
            })
            .finally(() => {
                uploadPromise = null;
            });
        return uploadPromise;
    }

    async function restore(payload){
        const storage = globalThis.__mindustryStorage;
        const records = [];
        for(const entry of Array.isArray(payload.files) ? payload.files : []){
            if(!entry || !isCloudSave(entry.p) || typeof entry.d !== 'string') continue;
            try{
                records.push({
                    path: entry.p,
                    updated: Math.max(0, Number(entry.t) || 0),
                    data: decodeBytes(entry.d)
                });
            }catch(error){
                console.warn('Skipping corrupt Mindustry cloud save entry:', entry && entry.p, error);
            }
        }

        if(typeof payload.settings === 'string' && payload.settings.startsWith('MWS1|')){
            try{ localStorage.setItem(SETTINGS_KEY, payload.settings); }catch(_ignored){}
        }
        const imported = await storage.importRecords(records);
        const revision = Math.max(0, Number(payload.updatedAt) || 0);
        storeLocalRevision(revision);
        dirtyRevision = revision;
        uploadedRevision = revision;
        mark('data-yandex-cloud-state', 'restored');
        mark('data-yandex-cloud-files', imported);
        mark('data-yandex-cloud-updated-at', revision);
    }

    function beginMonitoring(){
        const storage = globalThis.__mindustryStorage;
        lastStorageGeneration = storage ? storage.generation() : 0;
        lastSettingsValue = settingsValue();
        if(monitorTimer) clearInterval(monitorTimer);
        monitorTimer = setInterval(scan, 5000);
    }

    async function hydrate(){
        if(initPromise) return initPromise;
        initPromise = (async () => {
            mark('data-yandex-cloud-policy', 'settings-recent-msav-190k');
            const platform = globalThis.__mindustryYandex;
            const storage = globalThis.__mindustryStorage;
            if(!platform || !platform.available || !storage){
                mark('data-yandex-cloud-state', 'unavailable');
                beginMonitoring();
                return false;
            }

            try{
                player = await platform.getPlayer();
            }catch(error){
                mark('data-yandex-cloud-state', 'unavailable');
                mark('data-yandex-cloud-error', String(error && error.name ? error.name : 'get-player'));
                beginMonitoring();
                return false;
            }
            if(!player || typeof player.getData !== 'function' || typeof player.setData !== 'function'){
                mark('data-yandex-cloud-state', 'unavailable');
                beginMonitoring();
                return false;
            }

            mark('data-yandex-cloud-state', 'loading');
            let data = {};
            try{
                data = await player.getData([CLOUD_KEY]) || {};
            }catch(error){
                mark('data-yandex-cloud-state', 'error');
                mark('data-yandex-cloud-error', String(error && error.name ? error.name : 'get-data'));
                beginMonitoring();
                return false;
            }

            const remote = data[CLOUD_KEY];
            const remoteRevision = remote && remote.v === 1
                ? Math.max(0, Number(remote.updatedAt) || 0) : 0;
            const revision = localRevision();
            const localFiles = relevantPaths(storage);
            const hasLocal = Boolean(settingsValue()) || localFiles.length > 0;

            if(remoteRevision > 0 && remote && remote.v === 1){
                if(!hasLocal || (revision > 0 && remoteRevision > revision)){
                    await restore(remote);
                }else{
                    dirtyRevision = revision;
                    uploadedRevision = remoteRevision;
                    if(revision > remoteRevision){
                        mark('data-yandex-cloud-state', 'local-newer');
                        scheduleUpload(0);
                    }else if(revision === remoteRevision && revision > 0){
                        mark('data-yandex-cloud-state', 'current');
                    }else{
                        // A pre-cloud local profile has no trustworthy revision. Preserve
                        // it rather than replacing unknown local progress with remote data.
                        mark('data-yandex-cloud-state', 'legacy-local');
                    }
                }
            }else{
                uploadedRevision = 0;
                dirtyRevision = revision;
                if(hasLocal){
                    mark('data-yandex-cloud-state', 'local-only');
                    touch('initial-backup');
                }else{
                    mark('data-yandex-cloud-state', 'empty');
                }
            }

            beginMonitoring();
            return true;
        })().catch(error => {
            mark('data-yandex-cloud-state', 'error');
            mark('data-yandex-cloud-error', String(error && error.name ? error.name : 'hydrate'));
            console.warn('Mindustry cloud hydrate failed:', error);
            beginMonitoring();
            return false;
        });
        return initPromise;
    }

    async function flush(reason){
        scan();
        mark('data-yandex-cloud-flush', String(reason || 'manual'));
        return upload(true);
    }

    globalThis.__mindustryCloud = {hydrate, flush, touch};

    document.addEventListener('visibilitychange', () => {
        if(document.visibilityState === 'hidden') flush('visibility-hidden').catch(() => {});
    }, {passive: true});
    window.addEventListener('pagehide', () => flush('pagehide').catch(() => {}), {passive: true});
})();

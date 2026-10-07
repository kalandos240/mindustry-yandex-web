(() => {
    'use strict';

    const root = document.documentElement;
    const state = {
        ysdk: null,
        initialized: false,
        available: false,
        locale: '',
        deviceType: '',
        deviceSource: '',
        paused: false,
        loadingReadySent: false,
        gameplayActive: false,
        adResumeGameplay: false,
        adWaitingForResume: false,
        adInFlight: false,
        initPromise: null,
        playerPromise: null,
        cloudSyncPromise: null,
        cloudLastPayload: '',
        fullscreenButton: null
    };

    const cloudKey = 'mindustryWebCheckpointV1';
    const settingsKey = 'mindustry.web.settings.v1';
    const cloudBudgetBytes = 190 * 1024;
    const cloudFilePattern = /^saves\/(?:web-local-survival|sector-(?:serpulo|erekir)-\d+)\.msav$/;

    function mark(name, value){
        root.setAttribute(name, value);
    }

    function dispatch(name){
        window.dispatchEvent(new CustomEvent(name));
    }

    function installSdkScript(){
        if(globalThis.YaGames) return Promise.resolve();

        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = '/sdk.js';
            script.async = true;
            script.onload = () => resolve();
            script.onerror = () => reject(new Error('Yandex Games SDK loader is unavailable'));
            document.head.appendChild(script);
        });
    }

    function normalizeLocale(value){
        const lang = String(value || '').toLowerCase();
        return lang.startsWith('ru') ? 'ru' : 'en';
    }

    function normalizeDeviceType(info){
        const type = String(info && info.type || '').toLowerCase();
        if(type === 'desktop' || type === 'mobile' || type === 'tablet' || type === 'tv') return type;
        try{
            if(info && typeof info.isMobile === 'function' && info.isMobile()) return 'mobile';
            if(info && typeof info.isTablet === 'function' && info.isTablet()) return 'tablet';
            if(info && typeof info.isDesktop === 'function' && info.isDesktop()) return 'desktop';
            if(info && typeof info.isTV === 'function' && info.isTV()) return 'tv';
        }catch(_ignored){}
        return '';
    }

    function onPlatformPause(){
        state.paused = true;
        mark('data-yandex-game-state', 'paused');

        // Start the IndexedDB durability barrier before Arc freezes. This is critical
        // on mobile where game_api_pause may be followed by tab/app suspension.
        const storage = globalThis.__mindustryStorage;
        if(storage && typeof storage.lifecycleFlush === 'function'){
            storage.lifecycleFlush('yandex-pause').then(durable => {
                if(durable) syncCloudCheckpoint('yandex-pause', true);
            });
        }

        dispatch('mindustry:yandex-pause');
    }

    function onPlatformResume(){
        state.paused = false;

        // showFullscreenAdv() may close while game_api_pause is still active. Because
        // this wrapper explicitly stopped GameplayAPI before opening the ad, Yandex
        // will not implicitly restart that manually-stopped gameplay on resume.
        if(state.adResumeGameplay && state.adWaitingForResume){
            state.adResumeGameplay = false;
            state.adWaitingForResume = false;
            state.adInFlight = false;
            gameplayStart();
            mark('data-yandex-ad-resume', 'restarted-after-platform-resume');
        }

        mark('data-yandex-game-state', state.gameplayActive ? 'playing' : 'ready');
        dispatch('mindustry:yandex-resume');
    }

    async function init(){
        if(state.initPromise) return state.initPromise;

        state.initPromise = (async () => {
            mark('data-yandex-sdk', 'loading');
            try{
                await installSdkScript();
                if(!globalThis.YaGames || typeof globalThis.YaGames.init !== 'function'){
                    throw new Error('Yandex Games SDK did not expose YaGames.init');
                }

                const ysdk = await globalThis.YaGames.init();
                state.ysdk = ysdk;
                state.available = true;
                state.initialized = true;
                state.locale = normalizeLocale(ysdk && ysdk.environment && ysdk.environment.i18n && ysdk.environment.i18n.lang);

                let deviceInfo = null;
                if(ysdk && typeof ysdk.deviceInfo === 'function'){
                    try{
                        deviceInfo = await Promise.resolve(ysdk.deviceInfo());
                    }catch(error){
                        console.info('Yandex deviceInfo unavailable:', error && error.message ? error.message : error);
                    }
                }
                state.deviceType = normalizeDeviceType(deviceInfo);
                state.deviceSource = state.deviceType ? 'yandex-sdk' : 'browser-fallback';

                if(ysdk && typeof ysdk.on === 'function'){
                    ysdk.on('game_api_pause', onPlatformPause);
                    ysdk.on('game_api_resume', onPlatformResume);
                }

                mark('data-yandex-sdk', 'ready');
                mark('data-yandex-locale', state.locale);
                mark('data-yandex-device-type', state.deviceType || 'unknown');
                mark('data-yandex-device-source', state.deviceSource);
                mark('data-yandex-game-state', 'ready');
                installFullscreenControl();
                return state;
            }catch(error){
                // Local development/CI may run outside Yandex where /sdk.js does not exist.
                // The release archive still contains the mandatory relative loader path and
                // the same code is exercised with a CI-provided SDK stub.
                state.initialized = true;
                state.available = false;
                state.locale = normalizeLocale(navigator.language || navigator.userLanguage || 'en');
                state.deviceType = '';
                state.deviceSource = 'browser-fallback';
                mark('data-yandex-sdk', 'unavailable');
                mark('data-yandex-locale', state.locale);
                mark('data-yandex-device-type', 'unknown');
                mark('data-yandex-device-source', state.deviceSource);
                console.info('Yandex SDK unavailable in this environment:', error && error.message ? error.message : error);
                return state;
            }
        })();

        return state.initPromise;
    }

    function fullscreenApi(){
        return state.ysdk && state.ysdk.screen && state.ysdk.screen.fullscreen
            ? state.ysdk.screen.fullscreen : null;
    }

    function fullscreenStatus(){
        const api = fullscreenApi();
        if(api && typeof api.status === 'string'){
            return api.status === (api.STATUS_ON || 'on') || api.status === 'on' ? 'on' : 'off';
        }
        return document.fullscreenElement ? 'on' : 'off';
    }

    function syncFullscreenState(){
        const status = fullscreenStatus();
        mark('data-yandex-fullscreen-state', status);
        if(state.fullscreenButton){
            const ru = state.locale === 'ru';
            const label = status === 'on'
                ? (ru ? 'Выйти из полноэкранного режима' : 'Exit fullscreen')
                : (ru ? 'Полный экран' : 'Fullscreen');
            state.fullscreenButton.setAttribute('aria-pressed', status === 'on' ? 'true' : 'false');
            state.fullscreenButton.setAttribute('aria-label', label);
            state.fullscreenButton.title = label;
        }
        return status;
    }

    async function toggleFullscreen(){
        const api = fullscreenApi();
        const active = fullscreenStatus() === 'on';
        const action = active ? 'exit' : 'request';
        mark('data-yandex-fullscreen-action', action + '-pending');
        try{
            if(api && typeof api[action] === 'function'){
                await api[action]();
            }else if(active && typeof document.exitFullscreen === 'function'){
                await document.exitFullscreen();
            }else if(!active && document.documentElement && typeof document.documentElement.requestFullscreen === 'function'){
                await document.documentElement.requestFullscreen();
            }else{
                mark('data-yandex-fullscreen-action', 'unsupported');
                return false;
            }
            syncFullscreenState();
            mark('data-yandex-fullscreen-action', action + '-ready');
            return true;
        }catch(error){
            mark('data-yandex-fullscreen-action', action + '-error');
            console.info('Fullscreen request failed:', error && error.message ? error.message : error);
            return false;
        }
    }

    function installFullscreenControl(){
        if(state.fullscreenButton) return;
        const api = fullscreenApi();
        const browserApi = document.documentElement && typeof document.documentElement.requestFullscreen === 'function';
        if(!api && !browserApi){
            mark('data-yandex-fullscreen-control', 'unsupported');
            return;
        }

        const button = document.createElement('button');
        button.id = 'mindustry-fullscreen-toggle';
        button.type = 'button';
        button.textContent = '⛶';
        button.setAttribute('aria-label', 'Fullscreen');
        button.style.cssText = 'position:fixed;right:148px;top:12px;z-index:49;width:48px;height:44px;font:24px sans-serif;';
        button.onclick = () => { void toggleFullscreen(); };
        document.body.appendChild(button);
        state.fullscreenButton = button;
        mark('data-yandex-fullscreen-control', 'ready');
        syncFullscreenState();
        document.addEventListener('fullscreenchange', syncFullscreenState, {passive:true});
    }

    function loadingReady(){
        if(!state.available || !state.ysdk || state.loadingReadySent) return false;
        const api = state.ysdk.features && state.ysdk.features.LoadingAPI;
        if(!api || typeof api.ready !== 'function') return false;
        api.ready();
        state.loadingReadySent = true;
        mark('data-yandex-loading-ready', 'sent');
        return true;
    }

    function gameplayStart(){
        if(state.gameplayActive) return false;
        state.gameplayActive = true;
        if(!state.paused) mark('data-yandex-game-state', 'playing');
        const api = state.ysdk && state.ysdk.features && state.ysdk.features.GameplayAPI;
        if(api && typeof api.start === 'function') api.start();
        return true;
    }

    function gameplayStop(){
        if(!state.gameplayActive) return false;
        state.gameplayActive = false;
        if(!state.paused) mark('data-yandex-game-state', 'ready');
        const api = state.ysdk && state.ysdk.features && state.ysdk.features.GameplayAPI;
        if(api && typeof api.stop === 'function') api.stop();
        return true;
    }

    function finishFullscreenAdv(){
        if(!state.adResumeGameplay){
            state.adInFlight = false;
            return;
        }

        if(state.paused){
            state.adWaitingForResume = true;
            mark('data-yandex-ad-resume', 'waiting-platform-resume');
            return;
        }

        state.adResumeGameplay = false;
        state.adWaitingForResume = false;
        state.adInFlight = false;
        gameplayStart();
        mark('data-yandex-ad-resume', 'restarted-after-callback');
    }

    function showFullscreenAdv(callbacks = {}){
        if(state.adInFlight){
            const error = new Error('Yandex fullscreen ad is already in progress');
            mark('data-yandex-ad-state', 'busy');
            if(typeof callbacks.onError === 'function') callbacks.onError(error);
            return false;
        }
        if(!state.ysdk || !state.ysdk.adv || typeof state.ysdk.adv.showFullscreenAdv !== 'function'){
            if(typeof callbacks.onError === 'function') callbacks.onError(new Error('Yandex fullscreen ads unavailable'));
            return false;
        }

        // Only restore gameplay if it was actually active before the ad. Opening an
        // ad from a menu must not fabricate a playing GameplayAPI state afterwards.
        state.adResumeGameplay = state.gameplayActive;
        state.adWaitingForResume = false;
        state.adInFlight = true;
        if(state.adResumeGameplay) gameplayStop();

        let finalized = false;
        const finalize = (kind, payload) => {
            if(finalized) return;
            finalized = true;
            mark('data-yandex-ad-state', kind);
            // Restore lifecycle state even if a caller-provided callback throws.
            try{
                if(kind === 'closed' && typeof callbacks.onClose === 'function'){
                    callbacks.onClose(Boolean(payload));
                }else if(kind === 'error' && typeof callbacks.onError === 'function'){
                    callbacks.onError(payload);
                }
            }finally{
                finishFullscreenAdv();
            }
        };

        try{
            const result = state.ysdk.adv.showFullscreenAdv({
                callbacks: {
                    onOpen: () => {
                        mark('data-yandex-ad-state', 'open');
                        if(typeof callbacks.onOpen === 'function') callbacks.onOpen();
                    },
                    onClose: (wasShown) => finalize('closed', wasShown),
                    onError: (error) => finalize('error', error)
                }
            });
            // The current SDK reports completion through callbacks, but accepting a
            // thenable here also prevents a future/replaced SDK from leaving gameplay
            // stopped on an uncaught asynchronous rejection.
            if(result && typeof result.then === 'function'){
                result.catch(error => finalize('error', error));
            }
        }catch(error){
            finalize('error', error);
            return false;
        }
        return true;
    }

    async function showMenuFullscreenAdv(){
        // This path is called only after the user's explicit Back action has already
        // saved/reset the Mindustry world. Keep GameplayAPI stopped for the menu instead
        // of restoring the pre-ad gameplay state when game_api_resume arrives.
        if(state.gameplayActive) gameplayStop();

        const storage = globalThis.__mindustryStorage;
        if(storage && typeof storage.lifecycleFlush === 'function'){
            mark('data-yandex-menu-ad-storage', 'pending');
            const durable = await storage.lifecycleFlush('before-menu-ad');
            mark('data-yandex-menu-ad-storage', durable ? 'ready' : 'error');
            if(!durable) return false;
        }

        await syncCloudCheckpoint('menu-transition', true);

        if(!state.ysdk || !state.ysdk.adv || typeof state.ysdk.adv.showFullscreenAdv !== 'function'){
            mark('data-yandex-menu-ad-state', 'unavailable');
            return false;
        }

        // The durability barrier is asynchronous. If the player already started another
        // sector/map while it was completing, this is no longer a menu transition and
        // the interstitial must not interrupt the newly resumed gameplay.
        if(state.gameplayActive){
            mark('data-yandex-menu-ad-state', 'cancelled-gameplay-resumed');
            return false;
        }

        mark('data-yandex-menu-ad-state', 'requested');
        return showFullscreenAdv({
            onOpen: () => mark('data-yandex-menu-ad-state', 'open'),
            onClose: wasShown => mark('data-yandex-menu-ad-state', wasShown ? 'closed-shown' : 'closed-not-shown'),
            onError: () => mark('data-yandex-menu-ad-state', 'error')
        });
    }

    async function getPlayer(){
        if(!state.ysdk || typeof state.ysdk.getPlayer !== 'function') return null;
        if(!state.playerPromise) state.playerPromise = state.ysdk.getPlayer();
        return state.playerPromise;
    }

    function withTimeout(promise, milliseconds, label){
        let timer = 0;
        return Promise.race([
            Promise.resolve(promise),
            new Promise((_, reject) => {
                timer = setTimeout(() => reject(new Error(label + ' timed out')), milliseconds);
            })
        ]).finally(() => clearTimeout(timer));
    }

    function settingsValue(payload, wanted){
        if(typeof payload !== 'string' || !payload.startsWith('MWS1|')) return '';
        let cursor = 5;
        const part = () => {
            const colon = payload.indexOf(':', cursor);
            if(colon < 0) throw new Error('Malformed browser settings length');
            const length = Number(payload.slice(cursor, colon));
            const start = colon + 1;
            const end = start + length;
            if(!Number.isInteger(length) || length < 0 || end > payload.length){
                throw new Error('Malformed browser settings field');
            }
            cursor = end;
            return payload.slice(start, end);
        };
        while(cursor < payload.length){
            cursor++; // value type
            const key = part();
            const value = part();
            if(key === wanted) return value;
        }
        return '';
    }

    function bytesToBase64(bytes){
        // TeaVM exposes Java byte[] as Int8Array, so values >= 0x80 arrive as
        // negative numbers. String.fromCharCode(-1) becomes U+FFFF, which btoa()
        // rejects because it only accepts Latin-1 code units. Reinterpret the exact
        // same backing bytes as unsigned before constructing the binary string.
        const unsigned = bytes instanceof Uint8Array
            ? bytes
            : new Uint8Array(bytes.buffer, bytes.byteOffset || 0, bytes.byteLength);
        let binary = '';
        const chunk = 0x4000;
        for(let offset = 0; offset < unsigned.length; offset += chunk){
            binary += String.fromCharCode(...unsigned.subarray(offset, Math.min(unsigned.length, offset + chunk)));
        }
        return btoa(binary);
    }

    function base64ToBytes(value){
        const binary = atob(String(value || ''));
        const out = new Uint8Array(binary.length);
        for(let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i) & 0xff;
        return out;
    }

    function encodedBytes(value){
        return new TextEncoder().encode(JSON.stringify(value)).byteLength;
    }

    function buildCloudCheckpoint(){
        const storage = globalThis.__mindustryStorage;
        if(!storage) return null;

        let settings = '';
        try{ settings = localStorage.getItem(settingsKey) || ''; }catch(_ignored){}
        if(settings && !settings.startsWith('MWS1|')) settings = '';

        let lastSector = '';
        try{ lastSector = settingsValue(settings, 'last-sector-save'); }catch(_ignored){}
        const campaignPath = /^sector-(?:serpulo|erekir)-\d+$/.test(lastSector)
            ? 'saves/' + lastSector + '.msav' : '';
        const localPath = 'saves/web-local-survival.msav';
        const files = Object.create(null);

        const add = path => {
            if(!path || !storage.exists(path)) return;
            const bytes = storage.get(path);
            if(bytes && bytes.byteLength >= 128) files[path] = bytesToBase64(bytes);
        };
        add(campaignPath);
        add(localPath);

        const compact = () => ({schema: 1, settings, files});
        let stable = compact();
        if(encodedBytes(stable) > cloudBudgetBytes && files[localPath]){
            delete files[localPath];
            stable = compact();
        }
        if(encodedBytes(stable) > cloudBudgetBytes && campaignPath && files[campaignPath]){
            delete files[campaignPath];
            stable = compact();
        }
        const bytes = encodedBytes(stable);
        if(bytes > cloudBudgetBytes){
            mark('data-yandex-cloud-state', 'too-large');
            mark('data-yandex-cloud-bytes', String(bytes));
            return null;
        }

        const canonical = JSON.stringify(stable);
        return {
            canonical,
            bytes,
            snapshot: {
                schema: 1,
                savedAt: Date.now(),
                settings,
                files
            }
        };
    }

    async function syncCloudCheckpoint(reason, flush = true){
        if(!state.available){
            mark('data-yandex-cloud-state', 'unavailable');
            return false;
        }
        if(state.cloudSyncPromise) return state.cloudSyncPromise;

        state.cloudSyncPromise = (async () => {
            const built = buildCloudCheckpoint();
            if(!built) return false;
            if(built.canonical === state.cloudLastPayload){
                mark('data-yandex-cloud-state', 'unchanged');
                return true;
            }

            const player = await withTimeout(getPlayer(), 2500, 'Yandex player');
            if(!player || typeof player.setData !== 'function'){
                mark('data-yandex-cloud-state', 'player-unavailable');
                return false;
            }

            mark('data-yandex-cloud-state', 'saving');
            await withTimeout(player.setData({[cloudKey]: built.snapshot}, Boolean(flush)), 3500, 'Yandex cloud save');
            state.cloudLastPayload = built.canonical;
            mark('data-yandex-cloud-state', 'saved');
            mark('data-yandex-cloud-reason', String(reason || 'unknown'));
            mark('data-yandex-cloud-bytes', String(built.bytes));
            mark('data-yandex-cloud-files', String(Object.keys(built.snapshot.files).length));
            return true;
        })().catch(error => {
            mark('data-yandex-cloud-state', 'error');
            mark('data-yandex-cloud-error', String(error && error.message ? error.message : error));
            console.info('Yandex cloud checkpoint save unavailable:', error && error.message ? error.message : error);
            return false;
        }).finally(() => {
            state.cloudSyncPromise = null;
        });

        return state.cloudSyncPromise;
    }

    async function restoreCloudCheckpoint(){
        if(!state.available){
            mark('data-yandex-cloud-restore', 'unavailable');
            return false;
        }

        const storage = globalThis.__mindustryStorage;
        if(!storage){
            mark('data-yandex-cloud-restore', 'storage-unavailable');
            return false;
        }

        if(storage.paths().some(path => cloudFilePattern.test(path))){
            mark('data-yandex-cloud-restore', 'skipped-local-present');
            return false;
        }

        try{
            const player = await withTimeout(getPlayer(), 2500, 'Yandex player');
            if(!player || typeof player.getData !== 'function'){
                mark('data-yandex-cloud-restore', 'player-unavailable');
                return false;
            }

            mark('data-yandex-cloud-restore', 'loading');
            const data = await withTimeout(player.getData([cloudKey]), 3500, 'Yandex cloud load');
            const snapshot = data && data[cloudKey];
            if(!snapshot || snapshot.schema !== 1){
                mark('data-yandex-cloud-restore', 'empty');
                return false;
            }

            if(snapshot.settings){
                if(typeof snapshot.settings !== 'string' || !snapshot.settings.startsWith('MWS1|')){
                    throw new Error('Invalid cloud settings payload');
                }
                localStorage.setItem(settingsKey, snapshot.settings);
            }

            let restoredFiles = 0;
            const files = snapshot.files && typeof snapshot.files === 'object' ? snapshot.files : {};
            for(const [path, encoded] of Object.entries(files)){
                if(!cloudFilePattern.test(path)) continue;
                const bytes = base64ToBytes(encoded);
                if(bytes.byteLength < 128) continue;
                storage.put(path, bytes, bytes.byteLength);
                restoredFiles++;
            }
            await storage.flush();
            mark('data-yandex-cloud-restore', 'ready');
            mark('data-yandex-cloud-restored-files', String(restoredFiles));
            mark('data-yandex-cloud-restored-settings', snapshot.settings ? 'yes' : 'no');
            return true;
        }catch(error){
            mark('data-yandex-cloud-restore', 'error');
            mark('data-yandex-cloud-restore-error', String(error && error.message ? error.message : error));
            console.info('Yandex cloud checkpoint restore unavailable:', error && error.message ? error.message : error);
            return false;
        }
    }

    globalThis.__mindustryYandex = Object.assign(state, {
        init,
        loadingReady,
        gameplayStart,
        gameplayStop,
        toggleFullscreen,
        showFullscreenAdv,
        showMenuFullscreenAdv,
        getPlayer,
        syncCloudCheckpoint,
        restoreCloudCheckpoint
    });
})();

(() => {
    'use strict';

    const DB_NAME = 'mindustry-web-files-v1';
    const STORE = 'files';
    const memory = Object.create(null);
    let db = null;
    let initPromise = null;
    let writeGeneration = 0;
    let flushGeneration = -1;
    let flushPromise = null;
    let flushTransactions = 0;
    let mutationTx = null;
    let mutationStoreRef = null;
    let mutationTransactions = 0;

    function markStage(stage){
        document.documentElement.setAttribute('data-mindustry-storage', stage);
    }

    function normalize(path){
        let value = String(path == null ? '' : path).replaceAll('\\', '/');
        while(value.startsWith('/')) value = value.slice(1);
        while(value.includes('//')) value = value.replaceAll('//', '/');
        if(value === '..' || value.startsWith('../') || value.includes('/../')){
            throw new Error('Parent traversal is not allowed in browser storage: ' + path);
        }
        if(value === '.') return '';
        if(value.startsWith('./')) value = value.slice(2);
        return value;
    }

    function copyBytes(raw, logicalLength){
        if(raw == null) return new Int8Array(0);
        const available = raw.byteLength != null ? raw.byteLength :
            (raw.length != null ? raw.length : undefined);
        const length = logicalLength == null ? available :
            Math.max(0, Math.min(Number(logicalLength) || 0, available == null ? Number(logicalLength) || 0 : available));

        if(raw instanceof Int8Array) return raw.slice(0, length);
        if(ArrayBuffer.isView(raw)){
            const bytes = length == null ? raw.byteLength : length;
            return new Int8Array(raw.buffer.slice(raw.byteOffset, raw.byteOffset + bytes));
        }
        if(raw instanceof ArrayBuffer) return new Int8Array(raw.slice(0, length));
        const value = new Int8Array(raw);
        return length == null || length === value.byteLength ? value : value.slice(0, length);
    }

    function openDatabase(){
        return new Promise((resolve, reject) => {
            markStage('opening');
            const request = indexedDB.open(DB_NAME, 1);
            request.onupgradeneeded = () => {
                const database = request.result;
                if(!database.objectStoreNames.contains(STORE)) database.createObjectStore(STORE, {keyPath: 'path'});
            };
            request.onblocked = () => markStage('blocked');
            request.onsuccess = () => {
                markStage('opened');
                resolve(request.result);
            };
            request.onerror = () => reject(request.error || new Error('IndexedDB open failed'));
        });
    }

    function transaction(mode){
        if(!db) throw new Error('Mindustry persistent storage is not initialized');
        return db.transaction(STORE, mode).objectStore(STORE);
    }

    function clearMutationTransaction(tx){
        if(mutationTx !== tx) return;
        mutationTx = null;
        mutationStoreRef = null;
    }

    function mutationStore(){
        if(!db) throw new Error('Mindustry persistent storage is not initialized');
        if(mutationStoreRef) return mutationStoreRef;

        const tx = db.transaction(STORE, 'readwrite');
        mutationTx = tx;
        mutationStoreRef = tx.objectStore(STORE);
        mutationTransactions++;
        root.setAttribute('data-mindustry-storage-write-policy', 'task-coalesced-readwrite');
        root.setAttribute('data-mindustry-storage-write-transactions', String(mutationTransactions));

        tx.oncomplete = () => clearMutationTransaction(tx);
        tx.onerror = () => clearMutationTransaction(tx);
        tx.onabort = () => clearMutationTransaction(tx);
        return mutationStoreRef;
    }

    function mutate(action){
        let store = mutationStore();
        try{
            return action(store);
        }catch(error){
            if(!error || error.name !== 'TransactionInactiveError') throw error;
            mutationTx = null;
            mutationStoreRef = null;
            store = mutationStore();
            return action(store);
        }
    }

    async function init(){
        if(initPromise) return initPromise;
        initPromise = (async () => {
            db = await openDatabase();
            markStage('hydrating');
            await new Promise((resolve, reject) => {
                const request = transaction('readonly').getAll();
                request.onsuccess = () => {
                    for(const record of request.result || []){
                        const path = normalize(record.path);
                        memory[path] = copyBytes(record.data);
                    }
                    resolve();
                };
                request.onerror = () => reject(request.error || new Error('IndexedDB hydration failed'));
            });
            markStage('ready');
            return api;
        })().catch(error => {
            markStage('error');
            document.documentElement.setAttribute('data-mindustry-storage-error', String(error && error.name ? error.name : 'storage-error'));
            throw error;
        });
        return initPromise;
    }

    function get(path){
        const key = normalize(path);
        const value = memory[key];
        return value ? value.slice() : null;
    }

    function put(path, bytes, logicalLength){
        const key = normalize(path);
        const value = copyBytes(bytes, logicalLength);
        memory[key] = value;
        writeGeneration++;
        const request = mutate(store => store.put({path: key, data: value}));
        request.onerror = () => console.error('Mindustry IndexedDB write failed:', request.error);
        return true;
    }

    function remove(path){
        const key = normalize(path);
        const existed = Object.prototype.hasOwnProperty.call(memory, key);
        delete memory[key];
        writeGeneration++;
        const request = mutate(store => store.delete(key));
        request.onerror = () => console.error('Mindustry IndexedDB delete failed:', request.error);
        return existed;
    }

    function removeTree(path){
        const key = normalize(path);
        const prefix = key ? key + '/' : '';
        const keys = Object.keys(memory).filter(candidate => candidate === key || candidate.startsWith(prefix));
        for(const candidate of keys) delete memory[candidate];
        if(keys.length){
            writeGeneration++;
            mutate(store => {
                for(const candidate of keys) store.delete(candidate);
            });
        }
        return keys.length > 0;
    }

    function exists(path){
        return Object.prototype.hasOwnProperty.call(memory, normalize(path));
    }

    function hasChildren(path){
        const key = normalize(path);
        const prefix = key ? key + '/' : '';
        return Object.keys(memory).some(candidate => candidate.startsWith(prefix) && candidate.length > prefix.length);
    }

    function paths(){
        return Object.keys(memory).sort();
    }

    function byteLength(path){
        const value = memory[normalize(path)];
        return value ? value.byteLength : 0;
    }

    function flush(){
        if(!db) return Promise.resolve();

        const targetGeneration = writeGeneration;
        if(flushPromise){
            // Share an in-flight durability barrier when no new write transaction was
            // created. If writes appeared afterwards, chain one more barrier behind it.
            if(flushGeneration >= targetGeneration) return flushPromise;
            return flushPromise.then(() => flush());
        }

        flushGeneration = targetGeneration;
        flushTransactions++;
        document.documentElement.setAttribute('data-mindustry-storage-flush-policy', 'generation-coalesced');
        document.documentElement.setAttribute('data-mindustry-storage-flush-transactions', String(flushTransactions));

        const pending = new Promise((resolve, reject) => {
            const tx = db.transaction(STORE, 'readonly');
            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error || new Error('IndexedDB flush failed'));
            tx.onabort = () => reject(tx.error || new Error('IndexedDB flush aborted'));
            tx.objectStore(STORE).count();
        });

        flushPromise = pending.then(
            value => {
                flushPromise = null;
                return value;
            },
            error => {
                flushPromise = null;
                throw error;
            }
        );
        return flushPromise;
    }

    let lifecycleFlushCount = 0;
    function lifecycleFlush(reason){
        const label = String(reason || 'unknown');
        const root = document.documentElement;
        root.setAttribute('data-mindustry-storage-lifecycle-flush', label + '-pending');
        return flush().then(() => {
            lifecycleFlushCount++;
            root.setAttribute('data-mindustry-storage-lifecycle-flush', label + '-ready');
            root.setAttribute('data-mindustry-storage-lifecycle-flush-count', String(lifecycleFlushCount));
            return true;
        }).catch(error => {
            root.setAttribute('data-mindustry-storage-lifecycle-flush', label + '-error');
            root.setAttribute('data-mindustry-storage-lifecycle-flush-error',
                String(error && error.name ? error.name : 'storage-flush-error'));
            console.error('Mindustry lifecycle storage flush failed:', error);
            return false;
        });
    }

    const api = {init, get, put, remove, removeTree, exists, hasChildren, paths, byteLength, flush, lifecycleFlush};
    globalThis.__mindustryStorage = api;

    // Browser/Yandex lifecycle boundaries can freeze or destroy a mobile page before
    // a later application frame runs. Kick an IndexedDB barrier as soon as the page
    // becomes hidden or is being discarded. Yandex game_api_pause calls the same API
    // explicitly from yandex-platform.js.
    document.addEventListener('visibilitychange', () => {
        if(document.visibilityState === 'hidden') lifecycleFlush('visibility-hidden');
    }, {passive: true});
    window.addEventListener('pagehide', () => lifecycleFlush('pagehide'), {passive: true});
})();

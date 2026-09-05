(() => {
    'use strict';

    const DB_NAME = 'mindustry-web-files-v1';
    const STORE = 'files';
    const memory = Object.create(null);
    let db = null;
    let initPromise = null;

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

    function copyBytes(raw){
        if(raw == null) return new Int8Array(0);
        if(raw instanceof Int8Array) return raw.slice();
        if(ArrayBuffer.isView(raw)) return new Int8Array(raw.buffer.slice(raw.byteOffset, raw.byteOffset + raw.byteLength));
        if(raw instanceof ArrayBuffer) return new Int8Array(raw.slice(0));
        return new Int8Array(raw);
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

    function put(path, bytes){
        const key = normalize(path);
        const value = copyBytes(bytes);
        memory[key] = value;
        const request = transaction('readwrite').put({path: key, data: value});
        request.onerror = () => console.error('Mindustry IndexedDB write failed:', request.error);
        return true;
    }

    function remove(path){
        const key = normalize(path);
        const existed = Object.prototype.hasOwnProperty.call(memory, key);
        delete memory[key];
        const request = transaction('readwrite').delete(key);
        request.onerror = () => console.error('Mindustry IndexedDB delete failed:', request.error);
        return existed;
    }

    function removeTree(path){
        const key = normalize(path);
        const prefix = key ? key + '/' : '';
        const keys = Object.keys(memory).filter(candidate => candidate === key || candidate.startsWith(prefix));
        for(const candidate of keys) delete memory[candidate];
        if(keys.length){
            const store = transaction('readwrite');
            for(const candidate of keys) store.delete(candidate);
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
        return new Promise((resolve, reject) => {
            const tx = db.transaction(STORE, 'readonly');
            tx.oncomplete = () => resolve();
            tx.onerror = () => reject(tx.error || new Error('IndexedDB flush failed'));
            tx.onabort = () => reject(tx.error || new Error('IndexedDB flush aborted'));
            tx.objectStore(STORE).count();
        });
    }

    const api = {init, get, put, remove, removeTree, exists, hasChildren, paths, byteLength, flush};
    globalThis.__mindustryStorage = api;
})();

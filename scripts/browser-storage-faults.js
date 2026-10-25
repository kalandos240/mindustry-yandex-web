// Runs against real IndexedDB, isolated from the game by a dedicated profile.
(async () => {
    const root = document.documentElement;
    const check = (condition, message) => { if(!condition) throw new Error(message); };
    const api = globalThis.__mindustryStorage;
    const params = new URLSearchParams(location.search);
    const bytes = value => new Int8Array(value);
    const matches = (actual, expected) => actual && Array.from(actual).join(',') === expected.join(',');
    const persisted = async path => {
        const db = await new Promise((resolve, reject) => {
            const request = indexedDB.open('mindustry-web-files-v1', 1);
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
        try{
            return await new Promise((resolve, reject) => {
                const request = db.transaction('files', 'readonly').objectStore('files').get(path);
                request.onsuccess = () => resolve(request.result && request.result.data);
                request.onerror = () => reject(request.error);
            });
        }finally{ db.close(); }
    };
    const rejects = async (promise, message) => {
        let failed = false;
        try{ await promise; }catch(error){ failed = true; }
        check(failed, message);
    };
    await api.init();
    if(params.has('cold')){
        check(matches(api.get('saves/live.msav'), [7,8,9]), 'Cold load must hydrate the repaired latest checkpoint');
        check(!api.exists('deleted/a.msav') && !api.exists('deleted/b.msav'), 'Cold load must preserve tree deletion');
        check(!api.exists('saves/removed.msav'), 'Cold load must preserve single deletion');
        check(matches(api.get('settings/test.mws1'), [11,12]), 'Cold load must retain repaired settings');
    }else{
        api.put('saves/live.msav', bytes([1,2,3]));
        api.put('saves/removed.msav', bytes([4]));
        api.put('deleted/a.msav', bytes([5]));
        api.put('deleted/b.msav', bytes([6]));
        await api.flush();
        check(matches(await persisted('saves/live.msav'), [1,2,3]), 'Baseline is durably written');

        const nativePut = IDBObjectStore.prototype.put;
        IDBObjectStore.prototype.put = function(record){
            const request = nativePut.call(this, record);
            this.transaction.abort();
            return request;
        };
        try{
            api.put('saves/live.msav', bytes([7,8,9]));
            if(params.has('legacy')){
                await api.flush();
                check(matches(await persisted('saves/live.msav'), [1,2,3]), 'Legacy reproducer must prove acknowledged data was not written');
                root.setAttribute('data-storage-fault-test', 'legacy-reproduced');
                return;
            }
            await rejects(api.flush(), 'An aborted save and failed retry must not report flush success');
            await rejects(api.flush(), 'A second flush must not bypass the failed generation');
            check(await api.lifecycleFlush('fault') === false, 'Lifecycle must report failed persistence');
            check(root.getAttribute('data-mindustry-storage-lifecycle-flush') === 'fault-error', 'Lifecycle failure is visible');
            check(matches(api.get('saves/live.msav'), [7,8,9]), 'The newest checkpoint remains recoverable in memory');
            check(matches(await persisted('saves/live.msav'), [1,2,3]), 'Failed transaction preserves previous disk checkpoint');
        }finally{ IDBObjectStore.prototype.put = nativePut; }
        await api.flush();
        check(matches(await persisted('saves/live.msav'), [7,8,9]), 'Later flush retries the failed checkpoint');

        // Abort a transaction containing both deletion forms and a settings write.
        IDBObjectStore.prototype.put = function(record){
            const request = nativePut.call(this, record);
            if(record.path === 'settings/test.mws1') this.transaction.abort();
            return request;
        };
        try{
            api.remove('saves/removed.msav');
            api.removeTree('deleted');
            api.put('settings/test.mws1', bytes([10]));
            await rejects(api.flush(), 'Aborted mixed settings/deletion batch must remain unconfirmed');
            check(await persisted('deleted/a.msav'), 'Aborted deletion leaves old durable data intact');
        }finally{ IDBObjectStore.prototype.put = nativePut; }
        // A newer successful write must win over the old failed payload on repair.
        api.put('settings/test.mws1', bytes([11,12]));
        const first = api.flush();
        check(first === api.flush(), 'Unchanged flushes share their pending barrier');
        await first;
        check(!(await persisted('saves/removed.msav')), 'Repair replays single deletion');
        check(!(await persisted('deleted/a.msav')) && !(await persisted('deleted/b.msav')), 'Repair replays the whole deleted tree');
        check(matches(await persisted('settings/test.mws1'), [11,12]), 'Repair uses latest settings instead of the aborted payload');
        check(await api.lifecycleFlush('recovered') === true, 'Successful recovery restores lifecycle confirmation');
        check(root.getAttribute('data-mindustry-storage-write-state') === 'ready', 'Write error clears after confirmed recovery');
    }
    root.setAttribute('data-storage-fault-test', 'passed');
})().catch(error => {
    document.documentElement.setAttribute('data-storage-fault-test', 'failed');
    document.documentElement.setAttribute('data-storage-fault-error', String(error));
    console.error(error);
});

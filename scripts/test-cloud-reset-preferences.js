const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const baseline=process.argv[2];
const source=fs.readFileSync(baseline||__dirname+'/../web-runtime/src/web/yandex-platform.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const part=(type,key,value)=>type+key.length+':'+key+String(value).length+':'+value;
const setting=(key,value,type='s')=>part(type,key,value);
const resetKey='web-campaign-reset-at',intentKey='mindustry.web.reset.intent.v1',settingsKey='mindustry.web.settings.v1';
const settingsPath='settings/'+settingsKey+'.mws1',savePath='saves/sector-serpulo-170.msav';
const bytes=()=>new Uint8Array(128).fill(7),encoded=Buffer.alloc(128,7).toString('base64');
const remoteSettings='MWS1|'+setting('last-sector-save','sector-serpulo-170')+setting('serpulo-s-170-info','remote-inventory')+setting('copper-unlocked','1','b')+setting('fpscap','240','i');
const remote=()=>({schema:1,settings:remoteSettings,files:{[savePath]:encoded}});
const resetSettings=()=> 'MWS1|'+setting(resetKey,'1792880000000','l')+setting('fpscap','30','i');
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return{promise,resolve,reject};}
const cloneMap=map=>new Map(Array.from(map||[],([key,value])=>[key,new Uint8Array(value)]));
async function harness(options={}){
    const durable=options.durable||new Map(),memory=cloneMap(durable),local=options.local||new Map();
    const attrs=new Map([['data-mindustry-storage','ready']]),uploads=[],reads=[],removed=[],messages=[];
    let value=options.remote===undefined?remote():options.remote,failFlush=!!options.failFlush,flushGate=options.flushGate,uploadGate=options.uploadGate;
    const storage={get:path=>memory.get(path),exists:path=>memory.has(path),paths:()=>Array.from(memory.keys()),
        put:(path,data)=>memory.set(path,new Uint8Array(data)),remove:path=>{removed.push(path);memory.delete(path);},
        flush:async()=>{if(failFlush)throw Error('disk failed');if(flushGate)await flushGate.promise;
            durable.clear();for(const [key,data] of memory)durable.set(key,new Uint8Array(data));},
        lifecycleFlush:async()=>{try{await storage.flush();return true;}catch{return false;}}};
    const player={getData:async()=>{reads.push(true);return{mindustryWebCheckpointV1:typeof value==='function'?await value():value};},
        setData:async(data,flush)=>{uploads.push({snapshot:data.mindustryWebCheckpointV1,flush});if(uploadGate)await uploadGate.promise;}};
    const sdk={getPlayer:async()=>player};
    const timers=new Map();let timerId=0;
    const c={document:{documentElement:{getAttribute:key=>attrs.get(key),setAttribute:(key,value)=>attrs.set(key,value)}},
        navigator:{language:'ru'},YaGames:{init:async()=>sdk},__mindustryStorage:storage,
        localStorage:{getItem:key=>local.get(key)||null,setItem:(key,value)=>local.set(key,value),removeItem:key=>local.delete(key)},
        TextEncoder,TextDecoder,Uint8Array,Int8Array,btoa,atob,
        setTimeout:options.clock?(fn,ms)=>{timers.set(++timerId,{fn,ms});return timerId;}:setTimeout,
        clearTimeout:options.clock?id=>timers.delete(id):clearTimeout,Date,performance,
        console:{info:(...m)=>messages.push(m),error:(...m)=>messages.push(m)}};
    vm.createContext(c);vm.runInContext(source,c);await c.__mindustryYandex.init();
    return{api:c.__mindustryYandex,storage,memory,durable,local,attrs,uploads,reads,removed,messages,timers,
        fail:flag=>failFlush=flag,releaseUpload:()=>uploadGate=null,
        expire:ms=>{const timer=Array.from(timers.values()).find(t=>t.ms===ms);assert(timer,'pending '+ms+'ms deadline');timer.fn();},
        payload:()=>memory.has(settingsPath)?new TextDecoder().decode(memory.get(settingsPath)):'',
        putSettings:payload=>{const data=new TextEncoder().encode(payload);memory.set(settingsPath,data);local.set(settingsKey,payload);}};
}
(async()=>{
    if(baseline){
        const h=await harness();h.putSettings(resetSettings());
        assert.equal(await h.api.restoreCloudCheckpoint(),true);assert(h.memory.has(savePath));
        assert(!h.payload().includes(resetKey));
        console.log('Previous reset bug REPRODUCED: stale cloud revives cleared campaign and replaces reset/settings marker');return;
    }
    const durable=new Map([[savePath,bytes()],[savePath+'.lost-backup',bytes()],[settingsPath,new TextEncoder().encode(remoteSettings)]]);
    const local=new Map(),gate=deferred();const first=await harness({durable,local,flushGate:gate});
    first.memory.delete(savePath);first.memory.delete(savePath+'.lost-backup');first.putSettings(resetSettings());
    first.api.onSettingsStored(resetSettings());await tick();
    assert(local.has(intentKey),'Confirmed reset writes synchronous reload intent');
    assert(durable.has(savePath),'Old IndexedDB tree still exists before held flush');
    assert.equal(first.uploads.length,0,'No cloud reset before durability');
    const reload=await harness({durable,local});assert.equal(await reload.api.restoreCloudCheckpoint(),false);
    assert(!reload.memory.has(savePath));assert(!reload.durable.has(savePath));assert(!reload.memory.has(savePath+'.lost-backup'));
    assert.equal(reload.reads.length,0,'Reset replay never fetches old cloud');assert(!local.has(intentKey));
    assert(reload.payload().includes(setting(resetKey,'1792880000000','l')));
    assert.equal(reload.uploads.length,1);assert.deepEqual(Object.keys(reload.uploads[0].snapshot.files),[]);

    const failed=await harness({durable:new Map([[savePath,bytes()]]),failFlush:true});
    failed.memory.delete(savePath);failed.putSettings(resetSettings());
    assert.equal(await failed.api.onSettingsStored(resetSettings()),false);
    assert(failed.local.has(intentKey));assert.equal(failed.uploads.length,0);assert.equal(failed.attrs.get('data-yandex-reset-state'),'error');
    assert.equal(await failed.api.syncCloudCheckpoint('must-not-upload-failed-reset'),false);
    assert.equal(failed.uploads.length,0);failed.fail(false);
    assert(await failed.api.onSettingsStored(resetSettings()));assert(!failed.local.has(intentKey));assert.equal(failed.uploads.length,1);

    const h=await harness();const localPrefs='MWS1|'+setting('uiscale','150','i')+setting('fpscap','30','i')+setting('locale','ru')+
        setting('serpulo-s-170-info','old-local-inventory')+setting('copper-unlocked','0','b')+setting('req-router-copper','99','i')+
        setting('erekir-campaign-stats','old-stats')+setting('last-sector-save','sector-erekir-10');
    h.putSettings(localPrefs);assert.equal(await h.api.restoreCloudCheckpoint(),true);
    assert(h.payload().includes(setting('uiscale','150','i')));assert(h.payload().includes(setting('fpscap','30','i')));
    assert(h.payload().includes(setting('locale','ru')));assert(h.payload().includes(setting('serpulo-s-170-info','remote-inventory')));
    assert(h.payload().includes(setting('copper-unlocked','1','b')));assert(!h.payload().includes('old-local-inventory'));
    assert(!h.payload().includes('req-router-copper'));assert(!h.payload().includes('old-stats'));
    assert(h.payload().includes(setting('last-sector-save','sector-serpulo-170')));assert(h.memory.has(savePath));
    const noRemoteSettings=await harness({remote:{schema:1,settings:'',files:{[savePath]:encoded}}});
    noRemoteSettings.putSettings(localPrefs);assert(await noRemoteSettings.api.restoreCloudCheckpoint());
    assert(noRemoteSettings.payload().includes(setting('uiscale','150','i')));
    assert(!noRemoteSettings.payload().includes('old-local-inventory'));assert(!noRemoteSettings.payload().includes('req-router-copper'));

    const authoritative=await harness({durable:new Map([[savePath,new Uint8Array(128).fill(9)]])});
    assert.equal(await authoritative.api.restoreCloudCheckpoint(),false);assert.equal(authoritative.reads.length,0);
    assert.equal(authoritative.memory.get(savePath)[0],9);

    const download=deferred(),race=await harness({remote:()=>download.promise});
    const restoring=race.api.restoreCloudCheckpoint();await tick();race.putSettings(resetSettings());
    await race.api.onSettingsStored(resetSettings());download.resolve(remote());
    assert.equal(await restoring,false);assert(!race.memory.has(savePath));assert(race.payload().includes(resetKey));

    const upload=deferred(),queued=await harness({durable:new Map([[savePath,bytes()],[settingsPath,new TextEncoder().encode(remoteSettings)]]),uploadGate:upload});
    queued.api.cloudRestoreReady=true;const oldUpload=queued.api.syncCloudCheckpoint('before-reset');await tick();assert.equal(queued.uploads.length,1);
    queued.memory.delete(savePath);queued.putSettings(resetSettings());const resetting=queued.api.onSettingsStored(resetSettings());await tick();
    queued.releaseUpload();upload.resolve();await oldUpload;await resetting;
    assert.equal(queued.uploads.length,2);assert(queued.uploads[1].snapshot.settings.includes(resetKey));
    assert.deepEqual(Object.keys(queued.uploads[1].snapshot.files),[]);
    const lateGate=deferred(),late=await harness({clock:true,durable:new Map([[savePath,bytes()],[settingsPath,new TextEncoder().encode(remoteSettings)]]),uploadGate:lateGate});
    late.api.cloudRestoreReady=true;const lateOld=late.api.syncCloudCheckpoint('old-timeout');await tick();
    late.memory.delete(savePath);late.putSettings(resetSettings());const latest=late.api.onSettingsStored(resetSettings());await tick();
    late.releaseUpload();late.expire(3500);await lateOld;await latest;assert.equal(late.uploads.length,2);
    lateGate.resolve();await tick();if(late.api.cloudSyncPromise)await late.api.cloudSyncPromise;
    assert.equal(late.uploads.length,3,'Late pre-reset SDK acknowledgement reasserts latest reset despite dedupe');
    assert.deepEqual(Object.keys(late.uploads[2].snapshot.files),[]);assert.equal(late.timers.size,0);
    console.log('Cloud reset/preferences: synchronous reload intent, durable replay/deletion, failed-flush retry, local prefs/remote progress merge, existing-save authority and in-flight restore/upload isolation PASS');
})().catch(error=>{console.error(error);process.exitCode=1;});

const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const baseline = process.argv[2];
const source = fs.readFileSync(baseline || __dirname + '/../web-runtime/src/web/yandex-platform.js','utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));
const settingsPath = 'settings/mindustry.web.settings.v1.mws1';
const settingsKey = 'mindustry.web.settings.v1';
const savePath = 'saves/sector-serpulo-0.msav';
const part = (key,value) => 's'+key.length+':'+key+value.length+':'+value;
const settings = tag => 'MWS1|'+part('last-sector-save','sector-serpulo-0')+part('tag',tag);
const encoded = Buffer.alloc(128, 7).toString('base64');
const snapshot = () => ({schema:1,settings:settings('cloud'),files:{[savePath]:encoded}});

async function harness(remote=snapshot()){
    const attrs = new Map([['data-mindustry-storage','opening']]);
    const memory = new Map(), events = {}, uploads = [], puts = [], mirrors = [];
    let remoteValue = remote, reads = 0, flushes = 0, failFlush = false;
    let mirror = settings('local');
    const player = {
        getData:async () => { reads++; return {mindustryWebCheckpointV1:await (typeof remoteValue === 'function' ? remoteValue() : remoteValue)}; },
        setData:async data => { uploads.push(data.mindustryWebCheckpointV1); }
    };
    const sdk = {getPlayer:async () => player, environment:{i18n:{lang:'ru'}},
        on:(name,fn) => { events[name] = fn; }};
    const root = {setAttribute:(key,value) => attrs.set(key,value), getAttribute:key => attrs.get(key)};
    const context = {
        document:{documentElement:root}, window:{dispatchEvent:() => {}},
        CustomEvent:class {}, navigator:{language:'ru'},
        YaGames:{init:async () => sdk},
        localStorage:{getItem:() => mirror, setItem:(key,value) => { assert.equal(key,settingsKey); mirrors.push(value); mirror=value; }},
        __mindustryStorage:{
            get:path => memory.get(path), exists:path => memory.has(path), paths:() => Array.from(memory.keys()),
            put:(path,bytes) => { puts.push(path); memory.set(path,bytes.slice()); },
            flush:async () => { flushes++; if(failFlush) throw new Error('disk full'); },
            lifecycleFlush:async () => !failFlush
        }, TextEncoder,TextDecoder,Int8Array,Uint8Array,btoa,atob,
        console:{info:() => {}},setTimeout,clearTimeout,Date,performance
    };
    vm.createContext(context); vm.runInContext(source,context);
    const api = context.__mindustryYandex; await api.init();
    return {api,attrs,memory,events,uploads,puts,mirrors,
        setRemote:value => { remoteValue=value; }, failFlush:() => { failFlush=true; },
        reads:() => reads, flushes:() => flushes, mirror:() => mirror};
}

(async () => {
    const h = await harness();
    h.events.game_api_pause(); await tick();
    if(baseline){
        assert.equal(h.uploads.length,1);
        assert.equal(Object.keys(h.uploads[0].files).length,0);
        const broken=snapshot(); broken.files['saves/sector-erekir-0.msav']='invalid!';
        const partial=await harness(broken); partial.attrs.set('data-mindustry-storage','ready');
        assert.equal(await partial.api.restoreCloudCheckpoint(),false);
        assert(partial.puts.includes(settingsPath) && partial.puts.includes(savePath));
        assert.equal(partial.mirror(),settings('cloud'));
        console.log('Legacy startup bugs REPRODUCED: early SDK pause uploads incomplete progress; invalid second save changes settings and first save before restore fails');
        return;
    }
    assert.equal(h.uploads.length,0,'Early SDK pause cannot overwrite remote progress');
    for(const phase of ['opening','hydrating','error']){
        h.attrs.set('data-mindustry-storage',phase);
        assert.equal(await h.api.syncCloudCheckpoint('startup'),false);
    }
    h.attrs.set('data-mindustry-storage','ready');
    let finishLoad;
    h.setRemote(() => new Promise(resolve => { finishLoad=resolve; }));
    const loading=h.api.restoreCloudCheckpoint(); await tick();
    h.events.game_api_pause(); await tick();
    assert.equal(h.uploads.length,0,'Pause during cloud download cannot replace the pending remote snapshot');
    finishLoad(snapshot()); assert.equal(await loading,true);
    assert.equal(await h.api.syncCloudCheckpoint('restored'),true);
    assert.equal(h.uploads.length,1);
    assert.equal(h.uploads[0].files[savePath],encoded);
    assert.equal(h.mirror(),settings('cloud'));

    const badSnapshots=[];
    for(const value of ['invalid!',Buffer.alloc(10).toString('base64'),42]){
        const bad=snapshot(); bad.files['saves/sector-erekir-0.msav']=value; badSnapshots.push(bad);
    }
    badSnapshots.push({...snapshot(),files:[]});
    badSnapshots.push({...snapshot(),settings:'MWS1|'+ 'x'.repeat(200000)});
    badSnapshots.push({...snapshot(),settings:'MWS1|broken'});
    badSnapshots.push({...snapshot(),settings:'MWS1|s:5:hello'});
    badSnapshots.push({...snapshot(),settings:'MWS1|z3:key3:val'});
    for(const bad of badSnapshots){
        const b=await harness(bad); b.attrs.set('data-mindustry-storage','ready');
        b.memory.set(settingsPath,new TextEncoder().encode(settings('local')));
        assert.equal(await b.api.restoreCloudCheckpoint(),false);
        assert.equal(b.puts.length,0,'Invalid snapshot must not partially replace local files');
        assert.equal(b.flushes(),0);
        assert.equal(b.mirrors.length,0);
        assert.equal(new TextDecoder().decode(b.memory.get(settingsPath)),settings('local'));
        assert.equal(await b.api.syncCloudCheckpoint('after-invalid-restore'),false);
        assert.equal(b.uploads.length,0,'Failed restore cannot become an empty cloud overwrite');
    }
    const offline=await harness(() => { throw new Error('cloud offline'); });
    offline.attrs.set('data-mindustry-storage','ready');
    assert.equal(await offline.api.restoreCloudCheckpoint(),false);
    assert.equal(await offline.api.syncCloudCheckpoint('offline'),false);
    offline.setRemote(snapshot());
    assert.equal(await offline.api.restoreCloudCheckpoint(),true);
    assert.equal(await offline.api.syncCloudCheckpoint('recovered'),true);

    const empty=await harness(undefined); empty.setRemote(undefined);
    empty.attrs.set('data-mindustry-storage','ready');
    assert.equal(await empty.api.restoreCloudCheckpoint(),false);
    assert.equal(await empty.api.syncCloudCheckpoint('new-player'),true,'Confirmed empty account can start saving');
    const local=await harness(); local.attrs.set('data-mindustry-storage','ready');
    local.memory.set(savePath,new Uint8Array(128).fill(9));
    local.memory.set(settingsPath,new TextEncoder().encode(settings('local')));
    assert.equal(await local.api.restoreCloudCheckpoint(),false);
    assert.equal(local.reads(),0,'Existing local campaign is authoritative');
    assert.equal(await local.api.syncCloudCheckpoint('local'),true);

    const disk=await harness(); disk.attrs.set('data-mindustry-storage','ready'); disk.failFlush();
    assert.equal(await disk.api.restoreCloudCheckpoint(),false);
    assert.equal(disk.mirrors.length,0,'Settings mirror changes only after confirmed file persistence');
    console.log('Cloud startup/restore: early pause, hydration, in-flight restore, complete validation, offline recovery, existing local progress, new player and failed disk flush PASS');
})().catch(error => {console.error(error);process.exitCode=1;});

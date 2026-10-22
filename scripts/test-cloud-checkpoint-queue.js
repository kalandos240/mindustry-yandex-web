// Exercise the complete production platform wrapper with a controlled SDK.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const baseline = process.argv[2];
const source = fs.readFileSync(baseline || __dirname + '/../web-runtime/src/web/yandex-platform.js', 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));

async function harness(playerFailures=0){
    const attrs = new Map(), files = new Map(), calls = [], events = {};
    const settingsKey = 'mindustry.web.settings.v1';
    const settingsPath = 'settings/' + settingsKey + '.mws1';
    const savePath = 'saves/sector-serpulo-0.msav';
    const key = 'last-sector-save', value = 'sector-serpulo-0';
    files.set(settingsPath, new TextEncoder().encode('MWS1|s'+key.length+':'+key+value.length+':'+value));
    const set = n => files.set(savePath, new Int8Array(128).fill(n));
    set(1);
    const player = {setData:(data, flush) => new Promise((resolve, reject) => calls.push({data, flush, resolve, reject}))};
    const sdk = {getPlayer:async () => {
        if(playerFailures-- > 0) throw new Error('Player service temporarily offline');
        return player;
    }, environment:{i18n:{lang:'ru'}},
        deviceInfo:() => ({type:'desktop'}), on:(name, fn) => { events[name] = fn; }};
    const root = {setAttribute:(key,value) => attrs.set(key,value), getAttribute:key => attrs.get(key)};
    const context = {
        document:{documentElement:root}, window:{dispatchEvent:() => {}},
        CustomEvent:class {constructor(name){this.name=name;}}, navigator:{language:'ru'},
        YaGames:{init:async () => sdk}, localStorage:{getItem:() => null},
        __mindustryStorage:{get:path => files.get(path), exists:path => files.has(path), lifecycleFlush:async () => true},
        TextEncoder, TextDecoder, Int8Array, Uint8Array, btoa, atob,
        console:{info:() => {}}, setTimeout, clearTimeout, Date, performance
    };
    vm.createContext(context); vm.runInContext(source, context);
    const api = context.__mindustryYandex;
    await api.init();
    return {api,calls,set,events,attrs};
}

(async () => {
    const {api,calls,set,events,attrs} = await harness();
    const payload = i => calls[i].data.mindustryWebCheckpointV1.files['saves/sector-serpulo-0.msav'];
    const expected = n => Buffer.from(new Uint8Array(128).fill(n)).toString('base64');
    const first = api.syncCloudCheckpoint('first', false);
    await tick();
    assert.equal(calls.length, 1);
    assert.equal(payload(0), expected(1));
    const queued = [];
    for(let n=2;n<=101;n++){
        set(n);
        queued.push(api.syncCloudCheckpoint('new-checkpoint-'+n, n===2));
    }
    calls[0].resolve(); await tick();
    if(baseline){
        assert.equal(calls.length,1);
        await Promise.all([first,...queued]);
        assert.notEqual(payload(0), expected(101));
        console.log('Legacy cloud race REPRODUCED: 100 newer checkpoints acknowledged by the first stale upload');
        return;
    }
    assert.equal(calls.length, 2, 'Many requests coalesce to one newest follow-up upload');
    assert.equal(payload(1), expected(101));
    assert.equal(calls[1].flush, true, 'A queued durable request survives later non-durable requests');
    calls[1].resolve();
    assert((await Promise.all([first,...queued])).every(Boolean));
    assert.equal(await api.syncCloudCheckpoint('unchanged'), true);
    assert.equal(calls.length, 2, 'Identical confirmed data does not cause another network write');

    // A true flush requested during a non-flushing upload must upgrade durability.
    set(7);
    const soft = api.syncCloudCheckpoint('soft', false); await tick();
    const hard = api.syncCloudCheckpoint('hard', true);
    calls[2].resolve(); await tick();
    assert.equal(calls.length, 4);
    assert.equal(payload(2), payload(3));
    assert.equal(calls[3].flush, true);
    calls[3].resolve(); assert(await soft); assert(await hard);

    // Failure of an old upload must not discard a queued newer snapshot.
    set(8);
    const failedOld = api.syncCloudCheckpoint('old'); await tick();
    set(9);
    const newAfterFailure = api.syncCloudCheckpoint('new');
    calls[4].reject(new Error('offline')); await tick();
    assert.equal(payload(5), expected(9));
    calls[5].resolve(); assert(await failedOld); assert(await newAfterFailure);

    // No automatic retry loop on persistent failure; an explicit later call retries.
    set(10);
    const offline = api.syncCloudCheckpoint('offline'); await tick();
    calls[6].reject(new Error('offline')); assert.equal(await offline,false);
    await tick(); assert.equal(calls.length,7);
    const retry = api.syncCloudCheckpoint('retry'); await tick();
    calls[7].resolve(); assert(await retry);

    // The actual SDK pause hook must flush a checkpoint that changed mid-upload.
    set(11);
    const ongoing = api.syncCloudCheckpoint('playing'); await tick();
    set(12); events.game_api_pause(); await tick();
    calls[8].resolve(); await tick();
    assert.equal(payload(9), expected(12));
    assert.equal(calls[9].flush,true);
    calls[9].resolve(); assert(await ongoing);
    assert.equal(attrs.get('data-yandex-cloud-reason'),'yandex-pause');
    assert.equal(api.cloudSyncPromise,null);
    assert.equal(api.cloudSyncQueued,null);
    const temporaryPlayerFailure = await harness(1);
    assert.equal(await temporaryPlayerFailure.api.syncCloudCheckpoint('player-offline'), false);
    assert.equal(temporaryPlayerFailure.calls.length,0);
    const recovered = temporaryPlayerFailure.api.syncCloudCheckpoint('player-recovered');
    await tick();
    assert.equal(temporaryPlayerFailure.calls.length,1,'A rejected player lookup must be retried');
    temporaryPlayerFailure.calls[0].resolve();
    assert.equal(await recovered,true);
    console.log('Cloud checkpoint queue: latest snapshot, 100-call coalescing, durability upgrade, upload/player-service failure recovery, unchanged deduplication and real SDK pause hook PASS');
})().catch(error => { console.error(error); process.exitCode=1; });

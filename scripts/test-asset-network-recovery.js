// Run the production boot/warmup functions with deterministic network faults.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync(__dirname + '/../web-runtime/src/web/index.html', 'utf8');
function section(start, end){
    const a = html.indexOf(start), b = html.indexOf(end, a);
    assert(a >= 0 && b > a);
    return html.slice(a, b);
}
const loader = section('            async function fetchAssetBytes(url){', '            const campaignRequests');
const warmup = section('            async function warmCampaignMaps(){', '            if(deferredCampaignManifest.length){');

async function testStartup(){
    const timers = new Map();
    let id = 0, mode = 'ok', signal, late;
    const c = {AbortController, Int8Array, Promise, Error,
        setTimeout:(fn, ms) => { assert.equal(ms, 30000); timers.set(++id, fn); return id; },
        clearTimeout:key => timers.delete(key),
        fetch:async (url, options) => {
            signal = options.signal;
            if(mode === 'headers') return new Promise(resolve => { late = resolve; });
            return {ok:mode !== 'http', status:503, arrayBuffer:() => mode === 'body'
                ? new Promise(resolve => { late = resolve; }) : Promise.resolve(new ArrayBuffer(256))};
        }};
    vm.createContext(c); vm.runInContext(loader, c);
    assert.equal((await c.fetchAssetBytes('assets/font.png')).byteLength, 256);
    assert.equal(timers.size, 0);
    mode = 'http';
    await assert.rejects(c.fetchAssetBytes('assets/font.png'), /503/);
    assert.equal(timers.size, 0);
    for(mode of ['headers', 'body']){
        const pending = c.fetchAssetBytes('assets/font.png');
        await Promise.resolve();
        timers.values().next().value();
        await assert.rejects(pending, /timed out/);
        assert(signal.aborted, 'Deadline aborts the actual network request');
        late(mode === 'body' ? new ArrayBuffer(256) : {ok:true, arrayBuffer:async () => new ArrayBuffer(256)});
        await new Promise(resolve => setImmediate(resolve));
        assert.equal(timers.size, 0);
    }
    mode = 'ok';
    assert.equal((await c.fetchAssetBytes('assets/font.png')).byteLength, 256, 'Reload path can retry');
}

async function testWarmup(mobile, broken){
    const paths = Array.from({length:44}, (_, i) => 'maps/serpulo/map' + i + '.msav');
    const attrs = new Map(), cache = Object.create(null), seen = new Set();
    let active = 0, maxActive = 0, warnings = 0;
    const c = {touchMobile:mobile, deferredCampaignManifest:paths, cache,
        root:{setAttribute:(k,v) => attrs.set(k,v)}, console:{warn:() => warnings++},
        __mindustryPrepareCampaignMap:async path => {
            assert(!seen.has(path)); seen.add(path);
            maxActive = Math.max(maxActive, ++active);
            await new Promise(resolve => setImmediate(resolve));
            active--;
            if(broken.has(paths.indexOf(path))) throw new Error('HTTP 503');
            cache['assets/' + path] = new Int8Array(256);
        }};
    vm.createContext(c); vm.runInContext(warmup, c);
    await c.warmCampaignMaps();
    assert.equal(seen.size, 44, 'Every queued map is attempted despite earlier failures');
    assert.equal(maxActive, mobile ? 2 : 4);
    assert.equal(warnings, broken.size);
    assert.equal(attrs.get('data-mindustry-campaign-assets-count'), String(44 - broken.size));
    assert.equal(attrs.get('data-mindustry-campaign-assets-failed'), String(broken.size));
    assert.equal(attrs.get('data-mindustry-campaign-assets-bytes'), String((44 - broken.size) * 256));
    assert.equal(attrs.get('data-mindustry-campaign-assets'), broken.size ? 'partial' : 'ready');
}

(async () => {
    await testStartup();
    for(const mobile of [false, true]){
        await testWarmup(mobile, new Set());
        await testWarmup(mobile, new Set([0,1,2,3,17,43]));
        await testWarmup(mobile, new Set(Array.from({length:44}, (_, i) => i)));
    }
    console.log('Asset recovery: header/body deadlines, abort, retry, all 44 maps, partial/all failures and mobile/desktop concurrency PASS');
})().catch(error => { console.error(error); process.exitCode = 1; });

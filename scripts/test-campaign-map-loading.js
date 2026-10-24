// Exercise the production loader with a slow network, failures and retries.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync(__dirname + '/../web-runtime/src/web/index.html', 'utf8');
const start = html.indexOf('            const campaignRequests = new Map();');
const end = html.indexOf("\n            // Arc's synchronous Fi bridge", start);
assert(start >= 0 && end > start);
const source = html.slice(start, end);

(async () => {
    const path = 'maps/serpulo/frozenForest.msav';
    const cache = Object.create(null);
    let calls = 0, resolveFetch, lastSignal, timerId=0;
    const timers=new Map();
    let response = {ok:true, arrayBuffer:async () => new ArrayBuffer(256)};
    const context = {cache, Int8Array, Map, Promise, Error, AbortController,
        setTimeout:(fn,ms)=>{assert.equal(ms,20000);timers.set(++timerId,fn);return timerId;},
        clearTimeout:id=>timers.delete(id),
        __mindustryAssetSet:new Set([path]),
        fetch:(_url,options) => { calls++;lastSignal=options.signal; return new Promise(resolve => { resolveFetch = resolve; }); }};
    vm.createContext(context);
    vm.runInContext(source, context);
    const load = context.__mindustryPrepareCampaignMap;
    const background = load(path);
    const foreground = load(path);
    assert.equal(calls, 1, 'Launch must reuse the in-flight background request');
    assert.equal(cache['assets/' + path], undefined, 'A slow request must not expose incomplete bytes');
    resolveFetch(response);
    await Promise.all([background, foreground]);
    assert.equal(cache['assets/' + path].byteLength, 256);
    await load(path);
    assert.equal(calls, 1, 'Cached map must not be fetched again');
    delete cache['assets/' + path];

    const failed = load(path);
    resolveFetch({ok:false, status:503});
    await assert.rejects(failed, /503/);
    assert.equal(cache['assets/' + path], undefined);
    const truncated = load(path);
    resolveFetch({ok:true, arrayBuffer:async () => new ArrayBuffer(10)});
    await assert.rejects(truncated, /Incomplete/);
    const retry = load(path);
    resolveFetch(response);
    await retry;
    assert.equal(cache['assets/' + path].byteLength, 256, 'Retry must restore the requested map');
    await assert.rejects(load('maps/unknown.msav'), /Unpackaged/);
    assert.equal(calls, 4, 'Unknown paths must not issue network requests');
    delete cache['assets/' + path];
    const stalled=load(path);
    const lateResponse=resolveFetch;
    timers.values().next().value();
    await assert.rejects(stalled,/timed out/);
    assert.equal(lastSignal.aborted,true);
    lateResponse(response);
    await new Promise(resolve=>setImmediate(resolve));
    assert.equal(cache['assets/'+path],undefined,'A timed-out response cannot publish stale bytes');
    const retryAfterTimeout=load(path);
    resolveFetch(response);
    await retryAfterTimeout;
    assert.equal(cache['assets/'+path].byteLength,256);
    assert.equal(timers.size,0,'No deadline timers may leak');
    console.log('Campaign map loading: shared slow request, cache, HTTP failure, truncated map, hung request abort, stale-response isolation and retry PASS');
})().catch(error => { console.error(error); process.exitCode = 1; });

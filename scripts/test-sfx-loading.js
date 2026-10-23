const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const baseline=process.argv[2];
const source=fs.readFileSync(baseline||__dirname+'/../web-runtime/src/web/browser-audio.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return{promise,resolve,reject};}
function harness(){
    const attrs=new Map([['data-mindustry-input-mode','mobile']]),timers=new Map(),fetches=[],contexts=[];
    let timerId=0;
    class Context{
        constructor(){this.decoded=[];this.started=0;this.destination={};contexts.push(this);}
        resume(){return Promise.resolve();} suspend(){return Promise.resolve();} close(){return Promise.resolve();}
        decodeAudioData(bytes){this.decoded.push(bytes);return this.decodeGate?this.decodeGate.promise:Promise.resolve({duration:2});}
        createBufferSource(){const self=this;return{playbackRate:{},connect(){},start(){self.started++;},stop(){}};}
        createGain(){return{gain:{},connect(){}};}
    }
    const c={document:{documentElement:{setAttribute:(k,v)=>attrs.set(k,v),getAttribute:k=>attrs.get(k)}},
        AudioContext:Context,Map,Set,Promise,AbortController,
        console:{warn:()=>{}},addEventListener(){},removeEventListener(){},
        setTimeout:(fn,ms)=>{assert.equal(ms,20000);timers.set(++timerId,fn);return timerId;},clearTimeout:id=>timers.delete(id),
        fetch:(url,options)=>{const d=deferred();fetches.push({url,options,...d});return d.promise;}};
    c.window=c;vm.createContext(c);vm.runInContext(source,c);
    const api=c.__mindustryAudioApi,state=c.__mindustryAudio;api.install();
    return{api,state,attrs,fetches,timers,contexts,expire:()=>Array.from(timers.values()).forEach(fn=>fn())};
}
const response=()=>({ok:true,arrayBuffer:async()=>new ArrayBuffer(32)});
(async()=>{
    if(baseline){
        const h=harness();h.api.playSound('sound.ogg',1,1,0,false);await tick();
        assert.equal(h.timers.size,0);assert.equal(h.api.activeVoiceCount(),1);assert(h.state.buffers.has('sound.ogg'));
        const old=h.state.ctx;h.api.dispose();h.api.install();
        h.api.playSound('sound.ogg',1,1,0,false);await tick();
        h.fetches[0].reject(new Error('old request failed'));await tick();
        assert(!h.state.buffers.has('sound.ogg'),'Old cleanup deletes the replacement request');
        h.fetches[1].resolve(response());await tick();
        assert.equal(old.decoded.length,0);
        console.log('Previous SFX bugs REPRODUCED: unbounded request retains voice/cache, disposed request cleanup deletes fresh same-URL cache');
        return;
    }
    const shared=harness();shared.api.playSound('shared.ogg',1,1,0,false);shared.api.playSound('shared.ogg',0.5,1,0,false);
    await tick();assert.equal(shared.fetches.length,1);shared.fetches[0].resolve(response());await tick();
    assert.equal(shared.state.ctx.decoded.length,1);assert.equal(shared.state.ctx.started,2);assert.equal(shared.timers.size,0);
    assert.equal(shared.state.decodeRequests.size,0);
    for(const phase of ['headers','body','decode']){
        const h=harness();const body=deferred();const codec=deferred();
        h.api.playSound('fault.ogg',1,1,0,false);await tick();
        if(phase==='body')h.fetches[0].resolve({ok:true,arrayBuffer:()=>body.promise});
        if(phase==='decode'){h.state.ctx.decodeGate=codec;h.fetches[0].resolve(response());}
        await tick();h.expire();await tick();
        assert(h.fetches[0].options.signal.aborted,'Deadline aborts the network request');
        assert.equal(h.api.activeVoiceCount(),0,phase+' releases pending voice');
        assert.equal(h.state.buffers.size,0);assert.equal(h.timers.size,0);
        delete h.state.ctx.decodeGate;
        h.api.playSound('fault.ogg',1,1,0,false);await tick();
        const current=h.state.buffers.get('fault.ogg');
        h.fetches[1].resolve(response());await tick();
        if(phase==='headers')h.fetches[0].resolve(response());
        if(phase==='body')body.resolve(new ArrayBuffer(32));
        if(phase==='decode')codec.resolve({duration:999});
        await tick();assert.equal(h.state.buffers.get('fault.ogg'),current,'Late result cannot replace or delete the retry');
        assert.equal(h.api.soundLength('fault.ogg'),2);assert.equal(h.state.ctx.started,1);
        assert.equal(h.state.decodeRequests.size,0);assert.equal(h.timers.size,0);
    }
    for(const pendingCodec of [false,true]){
        const h=harness();const codec=deferred();const old=h.state.ctx;
        h.api.verify('replace.ogg');await tick();
        if(pendingCodec){old.decodeGate=codec;h.fetches[0].resolve(response());await tick();}
        h.api.dispose();h.api.install();h.api.playSound('replace.ogg',1,1,0,false);await tick();
        const current=h.state.buffers.get('replace.ogg');h.fetches[1].resolve(response());await tick();
        if(pendingCodec)codec.reject(new Error('old codec failure'));
        else h.fetches[0].reject(new Error('old fetch failure'));
        await tick();assert.equal(h.state.buffers.get('replace.ogg'),current);
        assert.equal(h.state.ctx.started,1);assert.notEqual(h.attrs.get('data-mindustry-audio'),'error','Stale verification cannot mark a replacement context failed');
        assert.equal(h.timers.size,0);
    }
    const pressure=harness();for(let i=0;i<48;i++)pressure.api.playSound('pending-'+i+'.ogg',1,1,0,false);
    await tick();assert.equal(pressure.api.activeVoiceCount(),48);pressure.expire();await tick();
    assert.equal(pressure.api.activeVoiceCount(),0,'All mobile channels are released after stalled loads');
    assert.equal(pressure.state.buffers.size,0);assert.equal(pressure.state.decodeRequests.size,0);assert.equal(pressure.timers.size,0);
    const http=harness();http.api.playSound('http.ogg',1,1,0,false);await tick();
    http.fetches[0].resolve({ok:false,status:503});await tick();
    assert.equal(http.api.activeVoiceCount(),0);assert.equal(http.timers.size,0);
    console.log('SFX loading: shared decode, header/body/codec deadlines, abort, retry, stale result isolation, dispose/reinstall, HTTP errors and 48 mobile channels PASS');
})().catch(error=>{console.error(error);process.exitCode=1;});

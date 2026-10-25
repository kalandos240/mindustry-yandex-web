const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const baseline=process.argv[2];
const source=fs.readFileSync(baseline||__dirname+'/../web-runtime/src/web/browser-audio.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function harness(){
    const attrs=new Map(),listeners=new Map();
    class Context{
        constructor(){this.state='suspended';this.events=new Set();}
        addEventListener(name,fn){assert.equal(name,'statechange');this.events.add(fn);}
        emit(){for(const fn of this.events)fn();}
        resume(){
            if(this.rejectResume)return Promise.reject(new Error('Browser denied resume'));
            if(this.deferResume)return new Promise((resolve,reject)=>{this.resolveResume=resolve;this.rejectPending=reject;});
            if(!this.holdState)this.state='running';this.emit();return Promise.resolve();
        }
        suspend(){this.state='suspended';this.emit();return Promise.resolve();}
        close(){this.state='closed';this.emit();return Promise.resolve();}
    }
    const c={document:{documentElement:{getAttribute:k=>attrs.get(k),setAttribute:(k,v)=>attrs.set(k,v)}},
        AudioContext:Context,Map,Set,Promise,console,
        addEventListener:(name,fn)=>{if(!listeners.has(name))listeners.set(name,new Set());listeners.get(name).add(fn);},
        removeEventListener:(name,fn)=>listeners.get(name)?.delete(fn)};
    c.window=c;vm.createContext(c);vm.runInContext(source,c);
    const api=c.__mindustryAudioApi,state=c.__mindustryAudio;
    const gesture=()=>Array.from(listeners.get('pointerdown')||[]).forEach(fn=>fn());
    return{api,state,attrs,listeners,gesture};
}
async function ready(){const h=harness();h.api.install();h.gesture();await tick();assert(h.state.unlocked);return h;}
(async()=>{
    for(const status of ['suspended','interrupted']){
        const h=await ready();h.state.ctx.state=status;h.state.ctx.emit();
        if(baseline){assert(h.state.unlocked);assert.equal(h.listeners.get('pointerdown').size,0);}
        else{
            assert(!h.state.unlocked,status+' waits for trusted input');
            assert.equal(h.attrs.get('data-mindustry-audio-unlocked'),'false');
            h.state.ctx.emit();assert.equal(h.listeners.get('pointerdown').size,1);
            h.gesture();await tick();assert(h.state.unlocked);assert.equal(h.state.ctx.state,'running');
            assert.equal(h.listeners.get('pointerdown').size,0);
        }
    }
    const denied=await ready();denied.api.platformPause(true);denied.state.ctx.rejectResume=true;
    denied.api.platformPause(false);await tick();
    if(baseline){assert(denied.state.unlocked);assert.equal(denied.listeners.get('pointerdown').size,0);
        console.log('Previous audio interruption bugs REPRODUCED: system suspension and denied platform resume leave no trusted-input recovery');return;}
    assert(!denied.state.unlocked);denied.gesture();await tick();assert(!denied.state.unlocked);
    denied.state.ctx.rejectResume=false;denied.gesture();await tick();assert(denied.state.unlocked);

    const paused=await ready();paused.api.platformPause(true);assert(paused.state.unlocked);
    assert.equal(paused.listeners.get('pointerdown').size,0,'Expected platform pause does not rearm input');
    paused.api.platformPause(false);await tick();assert(paused.state.unlocked);
    // Browsers may deliver the suspend statechange after platform resume starts.
    const delayed=await ready(),pending=delayed.state.ctx;delayed.api.platformPause(true);
    pending.deferResume=true;delayed.api.platformPause(false);pending.emit();
    assert(!delayed.state.unlocked);pending.state='running';pending.resolveResume();await tick();
    assert(delayed.state.unlocked,'Successful resume recovers from delayed suspend events');
    assert.equal(delayed.listeners.get('pointerdown').size,0);
    const stale=await ready(),old=stale.state.ctx;stale.api.platformPause(true);old.deferResume=true;
    stale.api.platformPause(false);stale.api.dispose();stale.api.install();stale.gesture();await tick();
    old.rejectPending(new Error('Old resume failed'));old.state='interrupted';old.emit();await tick();
    assert(stale.state.unlocked,'Disposed context cannot lock the replacement');
    assert.equal(stale.listeners.get('pointerdown').size,0);
    const held=harness();held.api.install();held.state.ctx.holdState=true;held.gesture();await tick();
    assert(!held.state.unlocked,'Resolved resume without running state is not an unlock');
    assert.equal(held.listeners.get('pointerdown').size,1);
    held.state.ctx.holdState=false;held.gesture();await tick();assert(held.state.unlocked);
    console.log('Audio interruption: system suspend/interrupted, denied resume, trusted retry, paused state, stale context and non-running resolution PASS');
})().catch(error=>{console.error(error);process.exitCode=1;});

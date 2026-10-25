const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const baseline=process.argv[2];
const source=fs.readFileSync(baseline||__dirname+'/../web-runtime/src/web/browser-audio.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function harness(){
    const attrs=new Map(),listeners=new Map(),contexts=[];
    class Context{
        constructor(){this.state='suspended';this.resumes=0;contexts.push(this);}
        resume(){this.resumes++;if(this.deferResume)return new Promise(resolve=>{this.finishResume=resolve;});this.state='running';return Promise.resolve();}
        suspend(){this.state='suspended';return Promise.resolve();}
        close(){this.state='closed';return Promise.resolve();}
    }
    class Media{
        constructor(){this.paused=true;this.plays=0;this.currentTime=0;}
        play(){this.plays++;this.paused=false;if(this.deferPlay)return new Promise((resolve,reject)=>{this.resolvePlay=resolve;this.rejectPlay=reject;});return Promise.resolve();}
        pause(){this.paused=true;}
        removeAttribute(){}
        load(){}
    }
    const c={document:{documentElement:{setAttribute:(k,v)=>attrs.set(k,v),getAttribute:k=>attrs.get(k)}},
        AudioContext:Context,Audio:Media,Map,Set,Promise,console,
        addEventListener:(name,fn)=>{if(!listeners.has(name))listeners.set(name,new Set());listeners.get(name).add(fn);},
        removeEventListener:(name,fn)=>listeners.get(name)?.delete(fn)};
    c.window=c;vm.createContext(c);vm.runInContext(source,c);
    const api=c.__mindustryAudioApi,state=c.__mindustryAudio;
    const gesture=()=>Array.from(listeners.get('pointerdown')||[]).forEach(fn=>fn());
    return{api,state,contexts,gesture,listeners};
}
async function ready(){const h=harness();h.api.install();h.gesture();await tick();return h;}
async function play(h,id=1){h.api.musicPlay(id,'music.ogg',0.6,1,0,true);await tick();return h.state.music.get(id).element;}
(async()=>{
    const duplicate=await ready(),a=await play(duplicate);
    duplicate.api.platformPause(true);duplicate.api.platformPause(true);duplicate.api.platformPause(false);await tick();
    if(baseline)assert(a.paused);else assert(!a.paused,'Repeated pause must retain resume intent');

    const explicit=await ready(),b=await play(explicit);
    explicit.api.platformPause(true);explicit.api.musicPause(1,true);explicit.api.platformPause(false);await tick();
    if(baseline)assert(!b.paused);else assert(b.paused,'An explicit music pause must survive platform resume');

    const queued=await ready();queued.api.platformPause(true);
    const c=await play(queued,2);assert(c.paused);
    queued.api.musicVolume(2,0.37);queued.api.musicLoop(2,false);
    queued.api.platformPause(false);await tick();
    if(baseline)assert(c.paused);else{assert(!c.paused,'Music requested during pause starts on resume');assert.equal(c.volume,0.37);assert.equal(c.loop,false);}

    const stopped=await ready();stopped.api.musicPrepare(3,'music.ogg');
    const d=stopped.state.music.get(3).element;d.deferPlay=true;
    stopped.api.musicPlay(3,'music.ogg',0.5,1,0,false);stopped.api.musicStop(3);
    d.rejectPlay(new Error('late play rejection'));await tick();
    stopped.api.platformPause(true);stopped.api.platformPause(false);await tick();
    if(baseline)assert(!d.paused);else{assert(d.paused,'Late rejection cannot requeue a stopped track');assert.equal(d.plays,1);assert.equal(d.currentTime,0);}

    const reinstalled=await ready();reinstalled.api.dispose();reinstalled.api.install();
    if(baseline){assert(reinstalled.state.unlocked);assert.equal(reinstalled.state.ctx.state,'suspended');}
    else{assert(!reinstalled.state.unlocked);reinstalled.gesture();await tick();assert.equal(reinstalled.state.ctx.state,'running');}
    if(baseline){console.log('Previous audio bugs REPRODUCED: duplicate pause loses music; explicit pause ignored; new paused track stuck; stopped track requeued; reinstalled context cannot unlock');return;}

    // Regression: Arc's SoundControl observes Music.isPlaying() on every
    // frame. A queued HTMLAudio play remains logically alive while waiting
    // for a real user gesture; otherwise it reselects dozens of songs
    // before browser autoplay permission resolves.
    const waiting=harness();waiting.api.install();
    waiting.api.musicPrepare(6,'music.ogg');
    waiting.api.musicPlay(6,'music.ogg',0.75,1,0,true);
    assert(waiting.state.music.get(6).pendingPlay);
    assert(waiting.state.music.get(6).element.paused);
    assert(waiting.api.musicPlaying(6),'Queued browser track must not be discarded as stopped');
    waiting.api.musicPause(6,true);
    assert(!waiting.api.musicPlaying(6),'Explicit pause must cancel queued play');
    waiting.api.musicPlay(6,'music.ogg',0.75,1,0,true);
    assert(waiting.api.musicPlaying(6));
    waiting.api.musicStop(6);
    assert(!waiting.api.musicPlaying(6),'Explicit stop must cancel queued play');

    const stale=harness();stale.api.install();const old=stale.state.ctx;old.deferResume=true;
    stale.gesture();stale.api.dispose();stale.api.install();old.finishResume();await tick();
    assert(!stale.state.unlocked,'Disposed context cannot unlock its replacement');
    assert.equal(stale.listeners.get('pointerdown').size,1);
    stale.gesture();await tick();assert(stale.state.unlocked);
    assert.equal(stale.listeners.get('pointerdown').size,0);
    const early=harness();early.api.platformPause(true);early.api.install();early.gesture();await tick();
    assert(!early.state.unlocked,'Gesture during platform pause cannot start audio');
    early.api.platformPause(false);early.gesture();await tick();assert(early.state.unlocked);

    const superseded=await ready();superseded.api.musicPrepare(4,'music.ogg');
    const e=superseded.state.music.get(4).element;e.deferPlay=true;
    superseded.api.musicPlay(4,'music.ogg',0.5,1,0,true);const oldResolve=e.resolvePlay;
    superseded.api.musicPlay(4,'music.ogg',0.4,1,0,true);const newReject=e.rejectPlay;
    newReject(new Error('new play rejected'));oldResolve();await tick();
    assert(superseded.state.music.get(4).pendingPlay,'Older success cannot clear the newest retry');
    superseded.api.musicDispose(4);assert(!superseded.state.music.has(4));
    const retry=await ready();retry.api.musicPrepare(5,'music.ogg');
    const f=retry.state.music.get(5).element;f.deferPlay=true;
    retry.api.musicPlay(5,'music.ogg',0.5,1,0,true);f.paused=true;f.rejectPlay(new Error('temporary autoplay denial'));await tick();
    f.deferPlay=false;retry.api.platformPause(false);await tick();
    assert(!f.paused && f.plays===2,'An extra resume event can retry denied pending playback');
    console.log('Audio lifecycle: duplicate pause, explicit pause/stop, queued music, late play callbacks, volume/loop, early pause and context reinstallation PASS');
})().catch(error=>{console.error(error);process.exitCode=1;});

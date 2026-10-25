// Run actual platform code with a hung SDK lookup and deterministic deadlines.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const baseline=process.argv[2];
const source=fs.readFileSync(baseline||__dirname+'/../web-runtime/src/web/yandex-platform.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
async function harness(){
    const attrs=new Map([['data-mindustry-storage','ready']]),timers=new Map();
    let nextTimer=0,calls=0,resolveLate,rejectLate;
    const fresh={setData:async()=>{}},stale={stale:true};
    const sdk={getPlayer:()=>{
        calls++;
        return calls===1?new Promise((resolve,reject)=>{resolveLate=resolve;rejectLate=reject;}):Promise.resolve(fresh);
    }};
    const c={document:{documentElement:{setAttribute:(k,v)=>attrs.set(k,v),getAttribute:k=>attrs.get(k)}},
        navigator:{language:'en'},YaGames:{init:async()=>sdk},
        __mindustryStorage:{get:()=>new TextEncoder().encode('MWS1|'),exists:()=>false,paths:()=>['saves/sector-serpulo-0.msav']},
        localStorage:{getItem:()=>null},console:{info:()=>{}},TextEncoder,TextDecoder,Uint8Array,Int8Array,btoa,atob,
        setTimeout:(fn,ms)=>{timers.set(++nextTimer,{fn,ms});return nextTimer;},clearTimeout:id=>timers.delete(id),Date,performance};
    vm.createContext(c);vm.runInContext(source,c);
    const api=c.__mindustryYandex;await api.init();
    return{api,timers,fresh,calls:()=>calls,late:fail=>fail?rejectLate(new Error('late SDK rejection')):resolveLate(stale),
        finish:()=>resolveLate(fresh),expire:()=>{const timer=Array.from(timers.values()).find(t=>t.ms===2500);assert(timer);timer.fn();}};
}
(async()=>{
    if(baseline){
        const h=await harness();
        const first=h.api.syncCloudCheckpoint('hung');await tick();h.expire();assert.equal(await first,false);
        const retry=h.api.syncCloudCheckpoint('retry');await tick();
        assert.equal(h.calls(),1,'Previous code keeps using the hung lookup after an outer timeout');
        h.finish();assert(await retry);assert.equal(h.timers.size,0);
        console.log('Previous SDK timeout bug REPRODUCED: later cloud attempt reuses the hung cached player lookup');
        return;
    }
    for(const rejectLate of [false,true]){
        const h=await harness();
        const first=h.api.getPlayer(),second=h.api.getPlayer();
        const rejected=Promise.all([assert.rejects(first,/timed out/),assert.rejects(second,/timed out/)]);
        await tick();assert.equal(h.calls(),1,'Concurrent lookups share one SDK request');
        assert.equal(h.timers.size,1);h.expire();await rejected;
        assert.equal(h.api.playerPromise,null,'Timeout releases the cached promise');
        assert.equal(h.timers.size,0);
        assert.equal(await h.api.getPlayer(),h.fresh);
        assert.equal(h.calls(),2);
        h.late(rejectLate);await tick();
        assert.equal(await h.api.getPlayer(),h.fresh,'Late SDK completion cannot replace the recovered player');
        assert.equal(h.calls(),2);assert.equal(h.timers.size,0,'Deadlines do not leak');
    }
    console.log('SDK player lookup: shared request, deadline, fresh retry, stale success/rejection isolation and timer cleanup PASS');
})().catch(error=>{console.error(error);process.exitCode=1;});

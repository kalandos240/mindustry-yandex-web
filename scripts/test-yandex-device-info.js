const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const baseline=process.argv[2];
const source=fs.readFileSync(baseline||__dirname+'/../web-runtime/src/web/yandex-platform.js','utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});return{promise,resolve,reject};}
async function harness(device,throwsGetter=false){
    const attrs=new Map(),events={},messages=[];let readyCalls=0;
    const sdk={environment:{i18n:{lang:'ru'}},on:(name,fn)=>events[name]=fn,
        features:{LoadingAPI:{ready:()=>readyCalls++}}};
    if(throwsGetter)Object.defineProperty(sdk,'deviceInfo',{get(){throw Error('device accessor failed');}});
    else sdk.deviceInfo=typeof device==='function'?function(){return device.call(this,sdk);}:device;
    const c={document:{documentElement:{setAttribute:(k,v)=>attrs.set(k,v),getAttribute:k=>attrs.get(k)}},
        navigator:{language:'en'},YaGames:{init:async()=>sdk},console:{info:(...args)=>messages.push(args)},
        setTimeout,clearTimeout,performance,Date};
    vm.createContext(c);vm.runInContext(source,c);await c.__mindustryYandex.init();
    return{api:c.__mindustryYandex,attrs,events,messages,ready:()=>readyCalls};
}
function clockHarness(initResult=sdk=>sdk,loader=false){
    const attrs=new Map(),events={},timers=new Map(),scripts=[],dispatched=[];
    let timerId=0,calls=0;
    const sdk={environment:{i18n:{lang:'ru'}},on:(name,callback)=>events[name]=callback,
        features:{LoadingAPI:{ready(){}}},deviceInfo:{type:'desktop'}};
    const ya={init:()=>{calls++;return initResult(sdk);}};
    const c={document:{documentElement:{setAttribute:(k,v)=>attrs.set(k,v),getAttribute:k=>attrs.get(k)},
            createElement:()=>({}),head:{appendChild:script=>scripts.push(script)}},
        navigator:{language:'en'},console:{info(){}},CustomEvent:class{constructor(type){this.type=type;}},
        dispatchEvent:event=>dispatched.push(event.type),
        setTimeout:(fn,ms)=>{timers.set(++timerId,{fn,ms});return timerId;},clearTimeout:id=>timers.delete(id),performance,Date};
    c.window=c;if(!loader)c.YaGames=ya;
    vm.createContext(c);vm.runInContext(source,c);
    return{api:c.__mindustryYandex,attrs,events,timers,sdk,dispatched,calls:()=>calls,
        expire:ms=>{const found=Array.from(timers.values()).filter(timer=>timer.ms===ms);assert.equal(found.length,1,'one '+ms+'ms deadline');found[0].fn();},
        finishLoader:()=>{assert.equal(scripts.length,1);c.YaGames=ya;scripts[0].onload();}};
}
(async()=>{
    if(baseline){
        const object=await harness({type:'mobile'});
        assert.equal(object.api.deviceType,'');assert.equal(object.attrs.get('data-yandex-device-source'),'browser-fallback');
        const accessor=await harness(null,true);assert.equal(accessor.api.available,false);
        console.log('Previous SDK device bugs REPRODUCED: object info ignored; optional throwing device accessor disables SDK');return;
    }
    const cases=[
        ['object mobile',{type:'mobile'},'mobile'],
        ['object tablet',{type:'TABLET'},'tablet'],
        ['promise info',Promise.resolve({type:'tv'}),'tv'],
        ['bound method',function(sdk){assert.equal(this,sdk);return{type:'desktop'};},'desktop'],
        ['async bound method',async function(sdk){assert.equal(this,sdk);return{type:'mobile'};},'mobile'],
        ['predicate object',{isMobile(){return true;}},'mobile'],
        ['null info',null,''],['absent info',undefined,''],['invalid info',{type:'invalid'},''],
        ['throwing callable',()=>{throw Error('device method failed');},''],
        ['rejected callable',()=>Promise.reject(Error('device lookup rejected')),''],
        ['throwing predicate',{isMobile(){throw Error('predicate failed');}},'']
    ];
    for(const [label,input,expected] of cases){
        const h=await harness(input);
        assert(h.api.available,label+' keeps the initialized SDK available');
        assert.equal(h.api.deviceType,expected,label);
        assert.equal(h.attrs.get('data-yandex-device-type'),expected||'unknown',label);
        assert.equal(h.attrs.get('data-yandex-device-source'),expected?'yandex-sdk':'browser-fallback',label);
        assert.equal(typeof h.events.game_api_pause,'function',label+' registers lifecycle pause');
        assert.equal(typeof h.events.game_api_resume,'function',label+' registers lifecycle resume');
        assert.equal(h.api.loadingReady(),true,label+' retains Game Ready API');assert.equal(h.ready(),1);
    }
    const getter=await harness(null,true);assert(getter.api.available);assert.equal(getter.api.deviceType,'');
    assert.equal(getter.messages.length,1);assert.equal(getter.api.loadingReady(),true);
    const typeGetter=await harness({get type(){throw Error('device type getter failed');}});
    assert(typeGetter.api.available);assert.equal(typeGetter.api.deviceType,'');assert.equal(typeGetter.api.loadingReady(),true);

    for(const rejectLate of [false,true]){
        const gate=deferred(),h=clockHarness(sdk=>{sdk.deviceInfo=()=>gate.promise;return sdk;});
        const first=h.api.init(),second=h.api.init();await tick();assert.equal(h.calls(),1);
        assert.equal(typeof h.events.game_api_pause,'function','Lifecycle installed before optional lookup completes');
        h.events.game_api_pause();assert(h.api.paused);assert(h.dispatched.includes('mindustry:yandex-pause'));
        h.expire(1000);await first;await second;
        assert(h.api.available);assert.equal(h.api.deviceType,'');assert.equal(h.attrs.get('data-yandex-game-state'),'paused');
        assert.equal(h.timers.size,0);
        if(rejectLate)gate.reject(Error('late device reject'));else gate.resolve({type:'mobile'});
        await tick();assert.equal(h.api.deviceType,'','Late device response cannot replace fallback');assert(h.api.available);
        h.events.game_api_resume();assert(!h.api.paused);assert.equal(h.attrs.get('data-yandex-game-state'),'ready');
        assert.equal(h.api.loadingReady(),true);assert.equal(h.timers.size,0);
    }
    for(const rejectLate of [false,true]){
        const gate=deferred(),h=clockHarness(()=>gate.promise);
        const pending=h.api.init();await tick();assert.equal(h.calls(),1);h.expire(5000);await pending;
        assert(h.api.initialized);assert(!h.api.available);assert.equal(h.api.ysdk,null);assert.equal(h.timers.size,0);
        if(rejectLate)gate.reject(Error('late SDK reject'));else gate.resolve(h.sdk);
        await tick();assert(!h.api.available);assert.equal(h.api.ysdk,null);assert.equal(Object.keys(h.events).length,0);
        await h.api.init();assert.equal(h.calls(),1,'Timed-out initialization remains one settled fallback');
        assert.equal(h.timers.size,0);
    }
    const loading=clockHarness(sdk=>sdk,true);const pending=loading.api.init();await tick();
    loading.expire(5000);await pending;assert(!loading.api.available);assert.equal(loading.calls(),0);
    loading.finishLoader();await tick();assert(!loading.api.available);assert.equal(loading.calls(),0,'Late script load must not start SDK after fallback');
    assert.equal(loading.timers.size,0);
    console.log('SDK bootstrap/device info: object/function compatibility, optional getter failure, loader/init/device deadlines, early lifecycle, late-result isolation and timer cleanup PASS');
})().catch(error=>{console.error(error);process.exitCode=1;});

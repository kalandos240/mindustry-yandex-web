#!/usr/bin/env node
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const script=fs.readFileSync('web-runtime/src/web/sdk.js','utf8');

async function run(initialAudio){
 const attrs={},events={},messages=[],cloud=new Map();
 let visibility='visible',shows=0,nowMs=1000000,observers=[];
 let sdk=null,lastPlacement='';
 const bridge={
  EVENT_NAME:{INTERSTITIAL_STATE_CHANGED:'ad',PAUSE_STATE_CHANGED:'pause',AUDIO_STATE_CHANGED:'audio'},
  initialize:async()=>true,
  platform:{
   language:'ru',isAudioEnabled:initialAudio,
   on(event,fn){events[event]=fn},
   sendMessage(msg){messages.push(msg);return Promise.resolve()}
  },
  storage:{
   async get(keys){return keys.map(key=>cloud.get(key)||null)},
   async set(keys,values){keys.forEach((key,i)=>cloud.set(key,values[i]))}
  },
  advertisement:{
   isInterstitialSupported:true,isBannerSupported:false,
   on(event,fn){events[event]=fn},
   showInterstitial(placement){
    assert.ok(['menu_return','pause'].includes(placement));
    lastPlacement=placement;
    shows++;
    setTimeout(()=>events.ad('opened'),0);
    setTimeout(()=>events.ad('closed'),2);
   }
  }
 };
 const root={
   setAttribute(k,v){attrs[k]=String(v);if(k.startsWith('data-mindustry-'))observers.forEach(cb=>cb())},
   getAttribute(k){return Object.prototype.hasOwnProperty.call(attrs,k)?attrs[k]:null}
 };
 class MockMutationObserver {
   constructor(fn){this.fn=fn}
   observe(){observers.push(this.fn)}
   disconnect(){observers=observers.filter(fn=>fn!==this.fn)}
 }
 const document={
  documentElement:root,
  get visibilityState(){return visibility},
  addEventListener(name,fn){events['document:'+name]=fn},
  createElement(){throw Error('CDN must not load when Bridge is already provided')},
  head:{appendChild(){throw Error('no unexpected SDK script')}}
 };
 const ctx={document,navigator:{language:'en'},bridge,console,Promise,
   Date:{now:()=>nowMs},MutationObserver:MockMutationObserver,
   Error,Math,String,JSON,setTimeout,clearTimeout};
 ctx.globalThis=ctx;
 vm.runInNewContext(script,ctx);
 sdk=await ctx.__mindustryPlaygamaSdk.init();
 assert.equal(attrs['data-playgama-interstitial-supported'],'true');
 assert.equal(sdk.environment.i18n.lang,'ru');
 let paused=0,resumed=0;
 sdk.on('game_api_pause',()=>paused++);
 sdk.on('game_api_resume',()=>resumed++);
 if(!initialAudio){
   await new Promise(setImmediate);
   assert.equal(paused,1,'initial platform mute must be applied to game callbacks');
   events.audio(true);
 }
 sdk.features.LoadingAPI.ready();
 sdk.features.LoadingAPI.ready();
 assert.equal(messages.filter(x=>x==='game_ready').length,1);
 let player=await sdk.getPlayer();
 const saved={schema:1,files:{'saves/sector-serpulo-0.msav':'QUJD'}};
 await player.setData({mindustryWebCheckpointV1:saved});
 const loaded=await player.getData(['mindustryWebCheckpointV1']);
 assert.deepEqual(JSON.parse(JSON.stringify(loaded.mindustryWebCheckpointV1)),saved);
 let opens=0,closes=0,firstDelay=0;
 await sdk.adv.showFullscreenAdv({callbacks:{onError(){firstDelay++}}});
 assert.equal(firstDelay,1);
 assert.equal(attrs['data-playgama-interstitial-state'],'first-ad-wait');
 assert.equal(shows,0,'Must not show advertisements immediately on launch');
 nowMs+=61000;
 await sdk.adv.showFullscreenAdv({callbacks:{
   onOpen(){opens++;assert.equal(ctx.__mindustryPlaygamaBridge.state.paused,true)},
   onClose(){closes++}
 }});
 assert.equal(opens,1);assert.equal(closes,1);assert.equal(shows,1);
 assert.equal(lastPlacement,'menu_return');
 assert.equal(attrs['data-playgama-interstitial-state'],'closed');
 assert.equal(ctx.__mindustryPlaygamaBridge.state.paused,false);
 events.pause(true);assert.equal(ctx.__mindustryPlaygamaBridge.state.paused,true);
 events.audio(false);events.pause(false);
 assert.equal(ctx.__mindustryPlaygamaBridge.state.paused,true);
 events.audio(true);assert.equal(ctx.__mindustryPlaygamaBridge.state.paused,false);
 visibility='hidden';events['document:visibilitychange']();
 assert.equal(ctx.__mindustryPlaygamaBridge.state.paused,true);
 visibility='visible';events['document:visibilitychange']();
 assert.equal(ctx.__mindustryPlaygamaBridge.state.paused,false);
 let cooldown=0;
 await sdk.adv.showFullscreenAdv({callbacks:{onError(){cooldown++}}});
 assert.equal(cooldown,1);assert.equal(shows,1,'ad must not spam');
 // Open the ACTUAL Mindustry pause marker after the 90-second cooldown.
 // Trigger from an observed natural gameplay pause, not a direct SDK call.
 ctx.__mindustryYandex={showFullscreenAdv(){
   return sdk.adv.showFullscreenAdv({placement:ctx.__mindustryPlaygamaBridge.state.intentPlacement});
 }};
 nowMs+=91000;
 root.setAttribute('data-mindustry-campaign-state','playing');
 root.setAttribute('data-mindustry-pause-dialog-visible','yes');
 await new Promise(resolve=>setTimeout(resolve,25));
 assert.equal(shows,2,'opening stock Pause during gameplay must request ad');
 assert.equal(lastPlacement,'pause');
 assert.equal(ctx.__mindustryPlaygamaBridge.state.paused,false,'resume after ad close');
 root.setAttribute('data-mindustry-pause-dialog-visible','no');
 root.setAttribute('data-mindustry-pause-dialog-visible','yes');
 await new Promise(resolve=>setTimeout(resolve,15));
 assert.equal(shows,2,'pause spam must not request a third ad');
 root.setAttribute('data-mindustry-pause-dialog-visible','no');
 assert.equal((await sdk.adv.showBannerAdv()).stickyAdvIsShowing,false);
 assert.equal(attrs['data-playgama-sdk'],'ready');
 assert.equal(attrs['data-playgama-game-ready'],'sent');
 assert.equal(attrs['data-playgama-cloud-save'],'completed');
 return {passed:true,initialAudio,paused,resumed};
}

(async()=>{
 const cases=[await run(true),await run(false)];
 console.log('PASS Playgama Bridge adapter:',JSON.stringify(cases));
})().catch(error=>{console.error(error);process.exitCode=1});

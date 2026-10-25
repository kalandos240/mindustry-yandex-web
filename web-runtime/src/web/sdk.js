/* Mindustry Playgama Bridge v2 adapter.
 * Keeps the existing TeaVM platform ABI; never loads the Yandex SDK.
 * Ads and cloud saves use only the official Playgama Bridge.
 */
(function(global){
'use strict';
const root=document.documentElement;
const bridgeUrl='https://bridge.playgama.com/v2/stable/playgama-bridge.js';
const listeners={game_api_pause:[],game_api_resume:[]};
const s={bridge:null,loading:null,hostPaused:false,hostAudio:true,adPaused:false,
  hidden:document.visibilityState==='hidden',paused:false,ready:false,
  pendingAd:false,lastAdAttempt:0,adCount:0,gameReady:false,currentAdHandler:null,
  sessionStartedAt:Date.now(),pauseWasOpen:false,adsObserver:null,intentPlacement:''};
const mark=(key,value)=>root.setAttribute('data-playgama-'+key,String(value));
function emit(name){for(const fn of listeners[name]||[]){try{fn()}catch(e){console.error(e)}}}
function updatePause(){
  const paused=s.hostPaused||!s.hostAudio||s.adPaused||s.hidden;
  if(paused===s.paused)return;
  s.paused=paused;mark('paused',paused);
  emit(paused?'game_api_pause':'game_api_resume');
}
function message(name){
  try{const p=s.bridge?.platform;
    if(p?.sendMessage)Promise.resolve(p.sendMessage(name)).catch(e=>console.info('Playgama lifecycle:',e));
  }catch(e){console.info('Playgama lifecycle:',e)}
}
function subscribe(group,name,callback){
  const e=s.bridge?.EVENT_NAME?.[name];
  if(e&&s.bridge?.[group]?.on)s.bridge[group].on(e,callback);
}
function timeout(promise,ms){
  let timer;
  return Promise.race([Promise.resolve(promise),new Promise((_,reject)=>{
    timer=setTimeout(()=>reject(new Error('Playgama SDK timeout')),ms);
  })]).finally(()=>clearTimeout(timer));
}
function loadBridge(){
  if(global.bridge?.initialize)return Promise.resolve(global.bridge);
  if(s.loading)return s.loading;
  s.loading=new Promise((resolve,reject)=>{
    const el=document.createElement('script');
    el.src=bridgeUrl;el.async=true;
    el.onload=()=>global.bridge?.initialize?resolve(global.bridge):reject(new Error('Bridge global missing'));
    el.onerror=()=>reject(new Error('Playgama Bridge SDK download failed'));
    document.head.appendChild(el);
  });
  return s.loading;
}
async function getData(keys){
  const store=s.bridge?.storage;
  if(!store?.get)return {};
  const data=await timeout(store.get(keys),3000);
  const out={};
  keys.forEach((key,i)=>{
    const raw=Array.isArray(data)?data[i]:data?.[key];
    if(raw==null){out[key]=null;return;}
    try{out[key]=typeof raw==='string'?JSON.parse(raw):raw}
    catch(_ignored){out[key]=null}
  });
  mark('cloud-load','completed');
  return out;
}
async function setData(record){
  const store=s.bridge?.storage;
  if(!store?.set)throw new Error('Playgama Bridge Storage unavailable');
  const keys=Object.keys(record),values=keys.map(k=>JSON.stringify(record[k]));
  await timeout(store.set(keys,values),3200);
  mark('cloud-save','completed');
  return true;
}
const FIRST_AD_DELAY_MS=60000;
const AD_COOLDOWN_MS=90000;
function showAd(request){
  const callbacks=request?.callbacks||{},ad=s.bridge?.advertisement;
  const placement=s.intentPlacement||request?.placement||'menu_return';
  s.intentPlacement='';
  const deny=(reason)=>{
    mark('interstitial-state',reason);
    try{callbacks.onError?.(new Error(reason))}catch(error){console.error(error)}
    return Promise.resolve(false);
  };
  if(!s.ready||!ad||typeof ad.showInterstitial!=='function')return deny('sdk-unavailable');
  if(ad.isInterstitialSupported!==true)return deny('not-supported-by-platform');
  if(s.pendingAd)return deny('already-open');
  if(Date.now()-s.sessionStartedAt<FIRST_AD_DELAY_MS)return deny('first-ad-wait');
  if(s.lastAdAttempt&&Date.now()-s.lastAdAttempt<AD_COOLDOWN_MS)return deny('cooldown');
  s.pendingAd=true;s.lastAdAttempt=Date.now();
  mark('interstitial-placement',placement);
  mark('interstitial-state','requested');
  mark('interstitial-attempt',++s.adCount);
  return new Promise(resolve=>{
    let done=false,opened=false,waitTimer,openTimer;
    const finish=shown=>{
      if(done)return;done=true;
      clearTimeout(waitTimer);clearTimeout(openTimer);
      s.pendingAd=false;s.currentAdHandler=null;
      try{
        if(!shown&&!opened)callbacks.onError?.(new Error('Interstitial no-fill or unavailable'));
        else callbacks.onClose?.(Boolean(shown||opened));
      }catch(e){console.error(e)}
      finally{s.adPaused=false;updatePause();resolve(Boolean(shown||opened))}
    };
    s.currentAdHandler=raw=>{
      const state=String(raw||'').toLowerCase();
      mark('interstitial-state',state);
      if(state==='opened'){
        if(opened)return;
        opened=true;clearTimeout(waitTimer);
        s.adPaused=true;updatePause();
        try{callbacks.onOpen?.()}catch(e){console.error(e)}
        openTimer=setTimeout(()=>finish(true),180000);
      }else if(state==='closed')finish(opened);
      else if(state==='failed')finish(false);
    };
    waitTimer=setTimeout(()=>finish(false),12000);
    try{Promise.resolve(ad.showInterstitial(placement)).catch(()=>finish(false))}
    catch(e){finish(false)}
  });
}
function installNaturalPauseAd(){
  // Ordinary user path: open Mindustry's ORIGINAL Pause panel after playing.
  // A full-screen ad is safe here, but must never interrupt an active battle.
  // The existing native Back->menu path remains supported independently.
  if(typeof MutationObserver!=='function'||!root.getAttribute)return;
  const onChange=()=>{
    const shown=root.getAttribute('data-mindustry-pause-dialog-visible')==='yes';
    if(!shown){s.pauseWasOpen=false;return}
    if(s.pauseWasOpen)return;
    s.pauseWasOpen=true;
    const active=root.getAttribute('data-mindustry-campaign-state')==='playing'
       ||root.getAttribute('data-mindustry-local-map-state')==='playing';
    if(!active||!s.gameReady)return;
    if(Date.now()-s.sessionStartedAt<FIRST_AD_DELAY_MS){
      mark('interstitial-state','first-ad-wait');
      return;
    }
    if(s.pendingAd||(s.lastAdAttempt&&Date.now()-s.lastAdAttempt<AD_COOLDOWN_MS))return;
    // Give the native Pause dialog a frame to settle; use the same game
    // lifecycle wrapper used by the real native exit button.
    const request=()=>{
      if(root.getAttribute('data-mindustry-pause-dialog-visible')!=='yes')return;
      if(!globalThis.__mindustryYandex?.showFullscreenAdv)return;
      s.intentPlacement='pause';
      const accepted=globalThis.__mindustryYandex.showFullscreenAdv();
      if(accepted===false)s.intentPlacement='';
    };
    if(typeof requestAnimationFrame==='function')requestAnimationFrame(request);
    else Promise.resolve().then(request);
  };
  s.adsObserver=new MutationObserver(onChange);
  s.adsObserver.observe(root,{attributes:true,attributeFilter:[
    'data-mindustry-pause-dialog-visible','data-mindustry-campaign-state','data-mindustry-local-map-state']});
  onChange();
}
async function initialize(){
  if(s.initPromise)return s.initPromise;
  s.initPromise=(async()=>{
    mark('sdk','loading');
    const bridge=await timeout(loadBridge(),3400);
    await timeout(bridge.initialize(),3400);
    s.bridge=bridge;
    s.hostAudio=bridge.platform?.isAudioEnabled!==false;
    subscribe('platform','PAUSE_STATE_CHANGED',paused=>{s.hostPaused=!!paused;updatePause()});
    subscribe('platform','AUDIO_STATE_CHANGED',enabled=>{s.hostAudio=!!enabled;updatePause()});
    subscribe('advertisement','INTERSTITIAL_STATE_CHANGED',state=>s.currentAdHandler?.(state));
    updatePause();s.ready=true;
    mark('sdk','ready');
    mark('locale',bridge.platform?.language||navigator.language||'en');
    mark('cloud-storage',bridge.storage?.get?'bridge':'unavailable');
    mark('interstitial-supported',String(bridge.advertisement?.isInterstitialSupported===true));
    mark('interstitial-state','idle');
    // Playgama Bridge has its OWN interstitial throttle. Configure it with
    // the documented setter, in addition to our gameplay timing policy.
    try{bridge.advertisement?.setMinimumDelayBetweenInterstitial?.(90)}
    catch(error){console.info('Playgama SDK ad throttle:',error)}
    installNaturalPauseAd();
    // Bridge requires GAME_READY after the actual TeaVM canvas is playable.
    // The old Yandex LoadingAPI callback is optional in this port, so watch
    // the real engine boot marker and emit once from a rendered frame.
    if(typeof MutationObserver==='function' && root.getAttribute){
      let observer;
      const check=()=>{
        if(root.getAttribute('data-mindustry-web')!=='ready')return;
        if(observer)observer.disconnect();
        if(typeof requestAnimationFrame==='function'){
          requestAnimationFrame(()=>sdk.features.LoadingAPI.ready());
        }else{
          sdk.features.LoadingAPI.ready();
        }
      };
      observer=new MutationObserver(check);
      observer.observe(root,{attributes:true,attributeFilter:['data-mindustry-web']});
      check();
    }
    return sdk;
  })().catch(error=>{
    s.ready=false;mark('sdk','unavailable');mark('error',String(error?.message||error));
    throw error; // Mindustry retains tested IndexedDB fallback outside the host
  });
  return s.initPromise;
}
const sdk={
  environment:{i18n:{get lang(){return s.bridge?.platform?.language||navigator.language||'en'}}},
  on(name,fn){
    if(!listeners[name])return;
    listeners[name].push(fn);
    if(name==='game_api_pause'&&s.paused)Promise.resolve().then(fn);
  },
  deviceInfo:{get type(){
    const device=s.bridge?.device;
    return device?.type||(device?.isMobile?'mobile':'desktop');
  }},
  features:{
    LoadingAPI:{ready(){
      if(s.gameReady)return;s.gameReady=true;message('game_ready');mark('game-ready','sent');
    }},
    GameplayAPI:{start(){message('level_started')},stop(){message('level_paused')}}
  },
  adv:{
    showFullscreenAdv:showAd,
    async showBannerAdv(){
      // Playgama's own portal does not support Bridge banners; never obscure the HUD.
      mark('banner','disabled-no-safe-layout');
      return {stickyAdvIsShowing:false,reason:'banner-disabled'};
    },
    async hideBannerAdv(){
      try{if(s.bridge?.advertisement?.isBannerSupported)await s.bridge.advertisement.hideBanner()}catch(_ignored){}
      return {stickyAdvIsShowing:false};
    }
  },
  async getPlayer(){return {getData,setData}}
};
document.addEventListener('visibilitychange',()=>{
  s.hidden=document.visibilityState==='hidden';updatePause();
});
global.__mindustryPlaygamaSdk={init:initialize};
global.__mindustryPlaygamaBridge={state:s,initialize,displayInterstitial:showAd,refreshPause:updatePause};
mark('sdk','adapter-ready');
})(globalThis);

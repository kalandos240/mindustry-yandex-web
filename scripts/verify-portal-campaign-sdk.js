/* Test-only SDK protocol implementation. Never shipped in a game package. */
(() => {
    const events=new Map();
    const state={ready:0,starts:0,stops:0,pauses:0,resumes:0,saves:0,banner:false,ad:null,cloud:{}};
    const emit=name=>{state[name==='game_api_pause'?'pauses':'resumes']++;for(const callback of events.get(name)||[])callback();};
    globalThis.__portalSdkSnapshot=()=>({ready:state.ready,starts:state.starts,stops:state.stops,
        pauses:state.pauses,resumes:state.resumes,saves:state.saves,banner:state.banner,ad:!!state.ad});
    addEventListener('message',event=>{
        if(event.source!==parent || event.data?.kind!=='portal-sdk-control')return;
        if(event.data.action==='pause')emit('game_api_pause');
        if(event.data.action==='resume')emit('game_api_resume');
        if(event.data.action==='ad-close'){
            const callbacks=state.ad;state.ad=null;if(callbacks?.onClose)callbacks.onClose(true);
        }
        if(event.data.action==='ad-error'){
            const callbacks=state.ad;state.ad=null;if(callbacks?.onError)callbacks.onError(new Error('Controlled portal ad callback error'));
        }
    });
    globalThis.YaGames={init:async()=>({
        environment:{i18n:{lang:'ru'}},
        deviceInfo:{type:new URLSearchParams(location.search).get('portalDevice')||'desktop'},
        on(name,callback){if(!events.has(name))events.set(name,[]);events.get(name).push(callback);},
        features:{LoadingAPI:{ready(){state.ready++;}},GameplayAPI:{start(){state.starts++;},stop(){state.stops++;}}},
        getPlayer:async()=>({getData:async()=>JSON.parse(JSON.stringify(state.cloud)),
            setData:async(value)=>{state.cloud=JSON.parse(JSON.stringify(value));state.saves++;}}),
        adv:{
            getBannerAdvStatus:async()=>({stickyAdvIsShowing:state.banner}),
            showBannerAdv:async()=>{state.banner=true;return{stickyAdvIsShowing:true};},
            hideBannerAdv:async()=>{state.banner=false;return{stickyAdvIsShowing:false};},
            showFullscreenAdv({callbacks}={}){
                if(state.ad)throw new Error('Overlapping test interstitial');
                state.ad=callbacks||{};if(callbacks?.onOpen)callbacks.onOpen();emit('game_api_pause');
            }
        }
    })};
})();

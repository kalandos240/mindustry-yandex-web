(() => {
    'use strict';
    // Loaded by both builds. The Playgama SDK is supplied by the Playgama
    // packaging entry point; this adapter never loads ads in Yandex Games.
    const root = document.documentElement;
    const active = new URLSearchParams(location.search).get('platform') === 'playgama'
        || /(^|\.)playgama\.com$/i.test(location.hostname);
    let loader = null;
    function loadSdk(){
        if(globalThis.bridge) return Promise.resolve(globalThis.bridge);
        if(!loader){
            loader = new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = 'https://bridge.playgama.com/v2/stable/playgama-bridge.js';
                script.onload = () => globalThis.bridge ? resolve(globalThis.bridge) : reject(new Error('Playgama Bridge missing'));
                script.onerror = () => reject(new Error('Playgama Bridge load failed'));
                document.head.appendChild(script);
            });
        }
        return loader;
    }
    const minGapMs = 90000;
    const initialDelayMs = 30000;
    const startedAt = performance.now();
    let lastRequestAt = -Infinity;
    let busy = false;
    let ready = null;

    function initialize(candidate){
        if(!ready){
            ready = Promise.resolve()
                .then(() => candidate.initialize())
                .catch(error => {
                    ready = null;
                    root.setAttribute('data-mindustry-playgama-ads', 'sdk-error');
                    throw error;
                });
        }
        return ready;
    }

    async function request(placement){
        if(!active) return false;
        const now = performance.now();
        if(busy || now - startedAt < initialDelayMs || now - lastRequestAt < minGapMs) return false;
        busy = true;
        lastRequestAt = now;
        root.setAttribute('data-mindustry-playgama-placement', placement);
        try{
            const candidate = await loadSdk();
            await initialize(candidate);
            const ads = candidate.advertisement;
            if(!ads.isInterstitialSupported){
                root.setAttribute('data-mindustry-playgama-ads', 'unsupported');
                return false;
            }
            root.setAttribute('data-mindustry-playgama-ads', 'requested');
            if(globalThis.__mindustryAudioApi) globalThis.__mindustryAudioApi.platformPause(true);
            await Promise.resolve(ads.showInterstitial(placement));
            root.setAttribute('data-mindustry-playgama-ads', 'completed');
            return true;
        }catch(error){
            root.setAttribute('data-mindustry-playgama-ads', 'failed');
            console.warn('Playgama interstitial unavailable:', error);
            return false;
        }finally{
            if(globalThis.__mindustryAudioApi) globalThis.__mindustryAudioApi.platformPause(false);
            busy = false;
        }
    }
    globalThis.__mindustryPlaygamaAds = {request, active};
})();

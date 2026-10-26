(() => {
    'use strict';
    // Loaded by both builds. The Playgama SDK is supplied by the Playgama
    // packaging entry point; this adapter never loads ads in Yandex Games.
    const root = document.documentElement;
    const minGapMs = 90000;
    const initialDelayMs = 30000;
    const startedAt = performance.now();
    let lastRequestAt = -Infinity;
    let busy = false;
    let ready = null;

    function sdk(){
        if(globalThis.__mindustryYandex && globalThis.__mindustryYandex.available) return null;
        const candidate = globalThis.bridge;
        return candidate && candidate.advertisement ? candidate : null;
    }

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
        const candidate = sdk();
        if(!candidate) return false;
        const now = performance.now();
        if(busy || now - startedAt < initialDelayMs || now - lastRequestAt < minGapMs) return false;
        busy = true;
        lastRequestAt = now;
        root.setAttribute('data-mindustry-playgama-placement', placement);
        try{
            await initialize(candidate);
            const ads = candidate.advertisement;
            if(!ads.isInterstitialSupported){
                root.setAttribute('data-mindustry-playgama-ads', 'unsupported');
                return false;
            }
            root.setAttribute('data-mindustry-playgama-ads', 'requested');
            await Promise.resolve(ads.showInterstitial(placement));
            root.setAttribute('data-mindustry-playgama-ads', 'completed');
            return true;
        }catch(error){
            root.setAttribute('data-mindustry-playgama-ads', 'failed');
            console.warn('Playgama interstitial unavailable:', error);
            return false;
        }finally{
            busy = false;
        }
    }
    globalThis.__mindustryPlaygamaAds = {request};
})();

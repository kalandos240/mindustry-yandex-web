(() => {
    'use strict';
    // This adapter is enabled only in the Playgama release artifact. Yandex stays isolated.
    const root = document.documentElement;
    const active = Boolean(globalThis.__mindustryPlaygamaBuild)
        || new URLSearchParams(location.search).get('platform') === 'playgama'
        || /(^|\.)playgama\.com$/i.test(location.hostname);
    const initialDelayMs = 30000;
    const minGapMs = 90000;
    const startedAt = performance.now();
    let lastRequestAt = -Infinity;
    let loadingPromise = null;
    let sdkReady = null;
    let busy = false;
    let paused = false;
    let readySent = false;

    function changePause(value){
        if(paused === value) return;
        paused = value;
        const audio = globalThis.__mindustryAudioApi;
        if(audio && typeof audio.platformPause === 'function') audio.platformPause(value);
        // BrowserApplication owns this lifecycle; an ad must suspend actual frame updates.
        window.dispatchEvent(new Event(value ? 'mindustry:yandex-pause' : 'mindustry:yandex-resume'));
    }

    function loadSdk(){
        if(globalThis.bridge) return Promise.resolve(globalThis.bridge);
        if(!loadingPromise){
            loadingPromise = new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = 'https://bridge.playgama.com/v2/stable/playgama-bridge.js';
                script.onload = () => globalThis.bridge
                    ? resolve(globalThis.bridge)
                    : reject(new Error('Playgama Bridge global missing'));
                script.onerror = () => reject(new Error('Playgama Bridge SDK load failed'));
                document.head.appendChild(script);
            });
        }
        return loadingPromise;
    }

    async function initialize(){
        if(!active) return null;
        if(!sdkReady){
            sdkReady = loadSdk().then(async bridge => {
                await bridge.initialize();
                root.setAttribute('data-mindustry-playgama-sdk', 'ready');
                startReadyWatcher(bridge);
                return bridge;
            }).catch(error => {
                root.setAttribute('data-mindustry-playgama-sdk', 'unavailable');
                console.warn('Playgama SDK unavailable:', error);
                sdkReady = null;
                loadingPromise = null;
                return null;
            });
        }
        return sdkReady;
    }

    function startReadyWatcher(bridge){
        function sendReady(){
            if(readySent) return true;
            if(root.getAttribute('data-mindustry-web') !== 'ready') return false;
            readySent = true;
            if(bridge.platform && typeof bridge.platform.sendMessage === 'function'){
                bridge.platform.sendMessage('game_ready');
            }
            root.setAttribute('data-mindustry-playgama-game-ready', 'sent');
            return true;
        }
        if(sendReady()) return;
        const watcher = new MutationObserver(() => {
            if(sendReady()) watcher.disconnect();
        });
        watcher.observe(root, {attributes: true, attributeFilter: ['data-mindustry-web']});
    }

    async function request(placement){
        if(!active || busy) return false;
        const now = performance.now();
        if(now - startedAt < initialDelayMs || now - lastRequestAt < minGapMs) return false;
        busy = true;
        try{
            const bridge = await initialize();
            if(!bridge || !bridge.advertisement || !bridge.advertisement.isInterstitialSupported){
                root.setAttribute('data-mindustry-playgama-ads', 'unavailable');
                return false;
            }
            lastRequestAt = performance.now();
            root.setAttribute('data-mindustry-playgama-placement', placement);
            const ads = bridge.advertisement;
            // Bridge v2 showInterstitial returns void. Wait for its CLOSED or FAILED event.
            const outcome = await new Promise(resolve => {
                let settled = false;
                let timeout;
                const finish = result => {
                    if(settled) return;
                    settled = true;
                    clearTimeout(timeout);
                    if(typeof ads.off === 'function') ads.off('interstitial_state_changed', onState);
                    changePause(false);
                    resolve(result);
                };
                const onState = state => {
                    root.setAttribute('data-mindustry-playgama-ads', String(state));
                    if(state === 'closed' || state === 'failed') finish(state === 'closed');
                };
                if(typeof ads.on === 'function') ads.on('interstitial_state_changed', onState);
                changePause(true);
                timeout = setTimeout(() => finish(false), 90000);
                try{
                    ads.showInterstitial(placement);
                    onState(ads.interstitialState);
                }catch(error){
                    console.warn('Playgama interstitial failed:', error);
                    finish(false);
                }
            });
            return outcome;
        }catch(error){
            root.setAttribute('data-mindustry-playgama-ads', 'failed');
            console.warn('Playgama ad request failed:', error);
            return false;
        }finally{
            changePause(false);
            busy = false;
        }
    }

    globalThis.__mindustryPlaygamaAds = {
        active,
        request,
        initialize,
        get paused(){ return paused; }
    };
})();

(() => {
    'use strict';

    const root = document.documentElement;
    const state = {
        ysdk: null,
        initialized: false,
        available: false,
        locale: '',
        deviceType: '',
        deviceSource: '',
        paused: false,
        loadingReadySent: false,
        gameplayActive: false,
        adResumeGameplay: false,
        adWaitingForResume: false,
        initPromise: null,
        playerPromise: null
    };

    function mark(name, value){
        root.setAttribute(name, value);
    }

    function dispatch(name){
        window.dispatchEvent(new CustomEvent(name));
    }

    function installSdkScript(){
        if(globalThis.YaGames) return Promise.resolve();

        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = '/sdk.js';
            script.async = true;
            script.onload = () => resolve();
            script.onerror = () => reject(new Error('Yandex Games SDK loader is unavailable'));
            document.head.appendChild(script);
        });
    }

    function normalizeLocale(value){
        const lang = String(value || '').toLowerCase();
        return lang.startsWith('ru') ? 'ru' : 'en';
    }

    function normalizeDeviceType(info){
        const type = String(info && info.type || '').toLowerCase();
        if(type === 'desktop' || type === 'mobile' || type === 'tablet' || type === 'tv') return type;
        try{
            if(info && typeof info.isMobile === 'function' && info.isMobile()) return 'mobile';
            if(info && typeof info.isTablet === 'function' && info.isTablet()) return 'tablet';
            if(info && typeof info.isDesktop === 'function' && info.isDesktop()) return 'desktop';
            if(info && typeof info.isTV === 'function' && info.isTV()) return 'tv';
        }catch(_ignored){}
        return '';
    }

    function onPlatformPause(){
        state.paused = true;
        mark('data-yandex-game-state', 'paused');
        dispatch('mindustry:yandex-pause');
    }

    function onPlatformResume(){
        state.paused = false;

        // showFullscreenAdv() may close while game_api_pause is still active. Because
        // this wrapper explicitly stopped GameplayAPI before opening the ad, Yandex
        // will not implicitly restart that manually-stopped gameplay on resume.
        if(state.adResumeGameplay && state.adWaitingForResume){
            state.adResumeGameplay = false;
            state.adWaitingForResume = false;
            gameplayStart();
            mark('data-yandex-ad-resume', 'restarted-after-platform-resume');
        }

        mark('data-yandex-game-state', state.gameplayActive ? 'playing' : 'ready');
        dispatch('mindustry:yandex-resume');
    }

    async function init(){
        if(state.initPromise) return state.initPromise;

        state.initPromise = (async () => {
            mark('data-yandex-sdk', 'loading');
            try{
                await installSdkScript();
                if(!globalThis.YaGames || typeof globalThis.YaGames.init !== 'function'){
                    throw new Error('Yandex Games SDK did not expose YaGames.init');
                }

                const ysdk = await globalThis.YaGames.init();
                state.ysdk = ysdk;
                state.available = true;
                state.initialized = true;
                state.locale = normalizeLocale(ysdk && ysdk.environment && ysdk.environment.i18n && ysdk.environment.i18n.lang);

                let deviceInfo = null;
                if(ysdk && typeof ysdk.deviceInfo === 'function'){
                    try{
                        deviceInfo = await Promise.resolve(ysdk.deviceInfo());
                    }catch(error){
                        console.info('Yandex deviceInfo unavailable:', error && error.message ? error.message : error);
                    }
                }
                state.deviceType = normalizeDeviceType(deviceInfo);
                state.deviceSource = state.deviceType ? 'yandex-sdk' : 'browser-fallback';

                if(ysdk && typeof ysdk.on === 'function'){
                    ysdk.on('game_api_pause', onPlatformPause);
                    ysdk.on('game_api_resume', onPlatformResume);
                }

                mark('data-yandex-sdk', 'ready');
                mark('data-yandex-locale', state.locale);
                mark('data-yandex-device-type', state.deviceType || 'unknown');
                mark('data-yandex-device-source', state.deviceSource);
                mark('data-yandex-game-state', 'ready');
                return state;
            }catch(error){
                // Local development/CI may run outside Yandex where /sdk.js does not exist.
                // The release archive still contains the mandatory relative loader path and
                // the same code is exercised with a CI-provided SDK stub.
                state.initialized = true;
                state.available = false;
                state.locale = normalizeLocale(navigator.language || navigator.userLanguage || 'en');
                state.deviceType = '';
                state.deviceSource = 'browser-fallback';
                mark('data-yandex-sdk', 'unavailable');
                mark('data-yandex-locale', state.locale);
                mark('data-yandex-device-type', 'unknown');
                mark('data-yandex-device-source', state.deviceSource);
                console.info('Yandex SDK unavailable in this environment:', error && error.message ? error.message : error);
                return state;
            }
        })();

        return state.initPromise;
    }

    function loadingReady(){
        if(!state.available || !state.ysdk || state.loadingReadySent) return false;
        const api = state.ysdk.features && state.ysdk.features.LoadingAPI;
        if(!api || typeof api.ready !== 'function') return false;
        api.ready();
        state.loadingReadySent = true;
        mark('data-yandex-loading-ready', 'sent');
        return true;
    }

    function gameplayStart(){
        if(state.gameplayActive) return false;
        state.gameplayActive = true;
        if(!state.paused) mark('data-yandex-game-state', 'playing');
        const api = state.ysdk && state.ysdk.features && state.ysdk.features.GameplayAPI;
        if(api && typeof api.start === 'function') api.start();
        return true;
    }

    function gameplayStop(){
        if(!state.gameplayActive) return false;
        state.gameplayActive = false;
        if(!state.paused) mark('data-yandex-game-state', 'ready');
        const api = state.ysdk && state.ysdk.features && state.ysdk.features.GameplayAPI;
        if(api && typeof api.stop === 'function') api.stop();
        return true;
    }

    function finishFullscreenAdv(){
        if(!state.adResumeGameplay) return;

        if(state.paused){
            state.adWaitingForResume = true;
            mark('data-yandex-ad-resume', 'waiting-platform-resume');
            return;
        }

        state.adResumeGameplay = false;
        state.adWaitingForResume = false;
        gameplayStart();
        mark('data-yandex-ad-resume', 'restarted-after-callback');
    }

    function showFullscreenAdv(callbacks = {}){
        if(!state.ysdk || !state.ysdk.adv || typeof state.ysdk.adv.showFullscreenAdv !== 'function'){
            if(typeof callbacks.onError === 'function') callbacks.onError(new Error('Yandex fullscreen ads unavailable'));
            return false;
        }

        // Only restore gameplay if it was actually active before the ad. Opening an
        // ad from a menu must not fabricate a playing GameplayAPI state afterwards.
        state.adResumeGameplay = state.gameplayActive;
        state.adWaitingForResume = false;
        if(state.adResumeGameplay) gameplayStop();

        state.ysdk.adv.showFullscreenAdv({
            callbacks: {
                onOpen: () => {
                    mark('data-yandex-ad-state', 'open');
                    if(typeof callbacks.onOpen === 'function') callbacks.onOpen();
                },
                onClose: (wasShown) => {
                    mark('data-yandex-ad-state', 'closed');
                    if(typeof callbacks.onClose === 'function') callbacks.onClose(Boolean(wasShown));
                    finishFullscreenAdv();
                },
                onError: (error) => {
                    mark('data-yandex-ad-state', 'error');
                    if(typeof callbacks.onError === 'function') callbacks.onError(error);
                    finishFullscreenAdv();
                }
            }
        });
        return true;
    }

    async function getPlayer(){
        if(!state.ysdk || typeof state.ysdk.getPlayer !== 'function') return null;
        if(!state.playerPromise) state.playerPromise = state.ysdk.getPlayer();
        return state.playerPromise;
    }

    globalThis.__mindustryYandex = Object.assign(state, {
        init,
        loadingReady,
        gameplayStart,
        gameplayStop,
        showFullscreenAdv,
        getPlayer
    });
})();

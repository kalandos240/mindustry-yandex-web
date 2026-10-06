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
        adInFlight: false,
        initPromise: null,
        playerPromise: null,
        fullscreenButton: null
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

        // Start the IndexedDB durability barrier before Arc freezes. This is critical
        // on mobile where game_api_pause may be followed by tab/app suspension.
        const storage = globalThis.__mindustryStorage;
        if(storage && typeof storage.lifecycleFlush === 'function'){
            storage.lifecycleFlush('yandex-pause');
        }

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
            state.adInFlight = false;
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
                installFullscreenControl();
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

    function fullscreenApi(){
        return state.ysdk && state.ysdk.screen && state.ysdk.screen.fullscreen
            ? state.ysdk.screen.fullscreen : null;
    }

    function fullscreenStatus(){
        const api = fullscreenApi();
        if(api && typeof api.status === 'string'){
            return api.status === (api.STATUS_ON || 'on') || api.status === 'on' ? 'on' : 'off';
        }
        return document.fullscreenElement ? 'on' : 'off';
    }

    function syncFullscreenState(){
        const status = fullscreenStatus();
        mark('data-yandex-fullscreen-state', status);
        if(state.fullscreenButton){
            const ru = state.locale === 'ru';
            const label = status === 'on'
                ? (ru ? 'Выйти из полноэкранного режима' : 'Exit fullscreen')
                : (ru ? 'Полный экран' : 'Fullscreen');
            state.fullscreenButton.setAttribute('aria-pressed', status === 'on' ? 'true' : 'false');
            state.fullscreenButton.setAttribute('aria-label', label);
            state.fullscreenButton.title = label;
        }
        return status;
    }

    async function toggleFullscreen(){
        const api = fullscreenApi();
        const active = fullscreenStatus() === 'on';
        const action = active ? 'exit' : 'request';
        mark('data-yandex-fullscreen-action', action + '-pending');
        try{
            if(api && typeof api[action] === 'function'){
                await api[action]();
            }else if(active && typeof document.exitFullscreen === 'function'){
                await document.exitFullscreen();
            }else if(!active && document.documentElement && typeof document.documentElement.requestFullscreen === 'function'){
                await document.documentElement.requestFullscreen();
            }else{
                mark('data-yandex-fullscreen-action', 'unsupported');
                return false;
            }
            syncFullscreenState();
            mark('data-yandex-fullscreen-action', action + '-ready');
            return true;
        }catch(error){
            mark('data-yandex-fullscreen-action', action + '-error');
            console.info('Fullscreen request failed:', error && error.message ? error.message : error);
            return false;
        }
    }

    function installFullscreenControl(){
        if(state.fullscreenButton) return;
        const api = fullscreenApi();
        const browserApi = document.documentElement && typeof document.documentElement.requestFullscreen === 'function';
        if(!api && !browserApi){
            mark('data-yandex-fullscreen-control', 'unsupported');
            return;
        }

        const button = document.createElement('button');
        button.id = 'mindustry-fullscreen-toggle';
        button.type = 'button';
        button.textContent = '⛶';
        button.setAttribute('aria-label', 'Fullscreen');
        button.style.cssText = 'position:fixed;right:148px;top:12px;z-index:49;width:48px;height:44px;font:24px sans-serif;';
        button.onclick = () => { void toggleFullscreen(); };
        document.body.appendChild(button);
        state.fullscreenButton = button;
        mark('data-yandex-fullscreen-control', 'ready');
        syncFullscreenState();
        document.addEventListener('fullscreenchange', syncFullscreenState, {passive:true});
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
        if(!state.adResumeGameplay){
            state.adInFlight = false;
            return;
        }

        if(state.paused){
            state.adWaitingForResume = true;
            mark('data-yandex-ad-resume', 'waiting-platform-resume');
            return;
        }

        state.adResumeGameplay = false;
        state.adWaitingForResume = false;
        state.adInFlight = false;
        gameplayStart();
        mark('data-yandex-ad-resume', 'restarted-after-callback');
    }

    function showFullscreenAdv(callbacks = {}){
        if(state.adInFlight){
            const error = new Error('Yandex fullscreen ad is already in progress');
            mark('data-yandex-ad-state', 'busy');
            if(typeof callbacks.onError === 'function') callbacks.onError(error);
            return false;
        }
        if(!state.ysdk || !state.ysdk.adv || typeof state.ysdk.adv.showFullscreenAdv !== 'function'){
            if(typeof callbacks.onError === 'function') callbacks.onError(new Error('Yandex fullscreen ads unavailable'));
            return false;
        }

        // Only restore gameplay if it was actually active before the ad. Opening an
        // ad from a menu must not fabricate a playing GameplayAPI state afterwards.
        state.adResumeGameplay = state.gameplayActive;
        state.adWaitingForResume = false;
        state.adInFlight = true;
        if(state.adResumeGameplay) gameplayStop();

        let finalized = false;
        const finalize = (kind, payload) => {
            if(finalized) return;
            finalized = true;
            mark('data-yandex-ad-state', kind);
            // Restore lifecycle state even if a caller-provided callback throws.
            try{
                if(kind === 'closed' && typeof callbacks.onClose === 'function'){
                    callbacks.onClose(Boolean(payload));
                }else if(kind === 'error' && typeof callbacks.onError === 'function'){
                    callbacks.onError(payload);
                }
            }finally{
                finishFullscreenAdv();
            }
        };

        try{
            const result = state.ysdk.adv.showFullscreenAdv({
                callbacks: {
                    onOpen: () => {
                        mark('data-yandex-ad-state', 'open');
                        if(typeof callbacks.onOpen === 'function') callbacks.onOpen();
                    },
                    onClose: (wasShown) => finalize('closed', wasShown),
                    onError: (error) => finalize('error', error)
                }
            });
            // The current SDK reports completion through callbacks, but accepting a
            // thenable here also prevents a future/replaced SDK from leaving gameplay
            // stopped on an uncaught asynchronous rejection.
            if(result && typeof result.then === 'function'){
                result.catch(error => finalize('error', error));
            }
        }catch(error){
            finalize('error', error);
            return false;
        }
        return true;
    }

    async function showMenuFullscreenAdv(){
        if(!state.ysdk || !state.ysdk.adv || typeof state.ysdk.adv.showFullscreenAdv !== 'function'){
            mark('data-yandex-menu-ad-state', 'unavailable');
            return false;
        }

        // This path is called only after the user's explicit Back action has already
        // saved/reset the Mindustry world. Keep GameplayAPI stopped for the menu instead
        // of restoring the pre-ad gameplay state when game_api_resume arrives.
        if(state.gameplayActive) gameplayStop();

        const storage = globalThis.__mindustryStorage;
        if(storage && typeof storage.lifecycleFlush === 'function'){
            mark('data-yandex-menu-ad-storage', 'pending');
            const durable = await storage.lifecycleFlush('before-menu-ad');
            mark('data-yandex-menu-ad-storage', durable ? 'ready' : 'error');
            if(!durable) return false;
        }

        // The durability barrier is asynchronous. If the player already started another
        // sector/map while it was completing, this is no longer a menu transition and
        // the interstitial must not interrupt the newly resumed gameplay.
        if(state.gameplayActive){
            mark('data-yandex-menu-ad-state', 'cancelled-gameplay-resumed');
            return false;
        }

        mark('data-yandex-menu-ad-state', 'requested');
        return showFullscreenAdv({
            onOpen: () => mark('data-yandex-menu-ad-state', 'open'),
            onClose: wasShown => mark('data-yandex-menu-ad-state', wasShown ? 'closed-shown' : 'closed-not-shown'),
            onError: () => mark('data-yandex-menu-ad-state', 'error')
        });
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
        toggleFullscreen,
        showFullscreenAdv,
        showMenuFullscreenAdv,
        getPlayer
    });
})();

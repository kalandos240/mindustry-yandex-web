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
        cloudSyncPromise: null,
        cloudSyncQueued: null,
        cloudLastPayload: '',
        cloudLastFlushedPayload: '',
        cloudRestoreReady: false,
        cloudResetAt: '',
        cloudResetDurableAt: '',
        cloudResetGeneration: 0,
        cloudResetCommit: null,
        fullscreenButton: null,
        bannerWanted: false,
        bannerShowing: false,
        bannerRequest: 0,
        bannerReason: '',
        bannerSyncPromise: Promise.resolve(true),
        menuAdRequestedAt: 0
    };

    const cloudKey = 'mindustryWebCheckpointV1';
    const settingsKey = 'mindustry.web.settings.v1';
    const settingsPath = 'settings/' + settingsKey + '.mws1';
    const resetKey = 'web-campaign-reset-at';
    const resetIntentKey = 'mindustry.web.reset.intent.v1';
    const cloudBudgetBytes = 190 * 1024;
    const cloudFilePattern = /^saves\/(?:web-local-survival|sector-(?:serpulo|erekir)-\d+)\.msav$/;

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
            script.src = 'sdk.js';
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
            storage.lifecycleFlush('yandex-pause').then(durable => {
                if(durable) syncCloudCheckpoint('yandex-pause', true);
            });
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
                await withTimeout(installSdkScript(), 5000, 'Yandex SDK loader');
                if(!globalThis.YaGames || typeof globalThis.YaGames.init !== 'function'){
                    throw new Error('Yandex Games SDK did not expose YaGames.init');
                }

                const ysdk = await withTimeout(globalThis.YaGames.init(), 5000, 'Yandex SDK init');
                state.ysdk = ysdk;
                state.available = true;
                state.initialized = true;
                state.locale = normalizeLocale(ysdk && ysdk.environment && ysdk.environment.i18n && ysdk.environment.i18n.lang);

                // Preserve lifecycle signals even while optional device info waits.
                if(ysdk && typeof ysdk.on === 'function'){
                    ysdk.on('game_api_pause', onPlatformPause);
                    ysdk.on('game_api_resume', onPlatformResume);
                }

                let deviceType = '';
                try{
                    // SDK v2 exposes an info object; keep compatibility with callable
                    // adapters too. Optional device lookup failure must not disable
                    // an otherwise initialized SDK (cloud saves and lifecycle events).
                    const device = ysdk && ysdk.deviceInfo;
                    const deviceInfo = await withTimeout(typeof device === 'function' ? device.call(ysdk) : device,
                        1000, 'Yandex device info');
                    deviceType = normalizeDeviceType(deviceInfo);
                }catch(error){
                    console.info('Yandex deviceInfo unavailable:', error && error.message ? error.message : error);
                }
                state.deviceType = deviceType;
                state.deviceSource = state.deviceType ? 'yandex-sdk' : 'browser-fallback';

                mark('data-yandex-sdk', 'ready');
                mark('data-yandex-locale', state.locale);
                mark('data-yandex-device-type', state.deviceType || 'unknown');
                mark('data-yandex-device-source', state.deviceSource);
                mark('data-yandex-game-state', state.paused ? 'paused' : 'ready');
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
        button.style.cssText = 'position:absolute;right:148px;top:12px;z-index:49;width:48px;height:44px;font:24px sans-serif;';
        button.onclick = () => { void toggleFullscreen(); };
        const surface = document.getElementById('mindustry-game-surface');
        if(!surface) throw new Error('Missing portal game surface for fullscreen control');
        surface.appendChild(button);
        state.fullscreenButton = button;
        // Fullscreen is a platform option, not a gameplay HUD button. Keep
        // this portal-specific control in the menu instead of covering the
        // original Mindustry canvas while the player builds/fights.
        const syncGameplayVisibility = () => {
            const root = document.documentElement;
            const campaign = root.getAttribute('data-mindustry-campaign-state');
            const local = root.getAttribute('data-mindustry-local-map-state');
            const active = ['playing','paused'].includes(campaign) ||
                ['playing','paused'].includes(local);
            button.style.display = active ? 'none' : '';
        };
        new MutationObserver(syncGameplayVisibility).observe(document.documentElement, {
            attributes:true,
            attributeFilter:['data-mindustry-campaign-state', 'data-mindustry-local-map-state']
        });
        syncGameplayVisibility();
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
        void syncStickyBanner(true, state.gameplayActive ? 'game-ready-playing' : 'game-ready');
        return true;
    }

    function syncStickyBanner(wanted, reason){
        state.bannerWanted = !!wanted;
        state.bannerReason = reason || '';
        const request = ++state.bannerRequest;

        // Serialize banner side effects. A previous implementation tried to repair stale
        // async results recursively; rapid show -> hide transitions could make two
        // resolved promises continuously invalidate each other and starve the browser
        // microtask queue. Each queued operation now waits for the prior SDK call and
        // applies the latest desired state at execution time.
        state.bannerSyncPromise = state.bannerSyncPromise.catch(() => false).then(async () => {
            const target = state.bannerWanted;
            const adv = state.ysdk && state.ysdk.adv;
            const method = target ? 'showBannerAdv' : 'hideBannerAdv';

            if(!adv || typeof adv[method] !== 'function'){
                mark('data-yandex-banner-state', 'unavailable');
                mark('data-yandex-banner-reason', state.bannerReason || 'unsupported');
                return false;
            }

            mark('data-yandex-banner-state', target ? 'show-pending' : 'hide-pending');
            try{
                const result = await Promise.resolve(adv[method]());
                state.bannerShowing = !!(result && result.stickyAdvIsShowing);

                if(target){
                    mark('data-yandex-banner-state', state.bannerShowing ? 'shown' : 'not-shown');
                }else{
                    mark('data-yandex-banner-state', state.bannerShowing ? 'still-shown' : 'hidden');
                }

                const detail = result && result.reason ? String(result.reason) : state.bannerReason;
                if(detail) mark('data-yandex-banner-reason', detail);
                mark('data-yandex-banner-request', String(request));
                return state.bannerShowing === target;
            }catch(error){
                mark('data-yandex-banner-state', 'error');
                mark('data-yandex-banner-reason', String(error && error.message ? error.message : error));
                return false;
            }
        });
        return state.bannerSyncPromise;
    }

    function gameplayStart(){
        if(state.gameplayActive){
            void syncStickyBanner(true, 'gameplay-start');
            return false;
        }
        state.gameplayActive = true;
        void syncStickyBanner(true, 'gameplay-start');
        if(!state.paused) mark('data-yandex-game-state', 'playing');
        const api = state.ysdk && state.ysdk.features && state.ysdk.features.GameplayAPI;
        if(api && typeof api.start === 'function') api.start();
        return true;
    }

    function gameplayStop(showBanner = true){
        const changed = state.gameplayActive;
        if(changed){
            state.gameplayActive = false;
            if(!state.paused) mark('data-yandex-game-state', 'ready');
            const api = state.ysdk && state.ysdk.features && state.ysdk.features.GameplayAPI;
            if(api && typeof api.stop === 'function') api.stop();
        }
        void syncStickyBanner(showBanner, showBanner ? 'gameplay-stop' : 'gameplay-stop-ad');
        return changed;
    }

    function finishFullscreenAdv(){
        if(!state.adResumeGameplay){
            state.adInFlight = false;
            void syncStickyBanner(true, 'fullscreen-ad-finished-menu');
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
        // Sticky banners and fullscreen ads must never overlap. If this ad interrupts
        // gameplay, stop GameplayAPI without exposing the menu banner in between.
        if(state.adResumeGameplay) gameplayStop(false);
        else void syncStickyBanner(false, 'fullscreen-ad-menu');

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

        // A sticky banner is now allowed to stay visible beside the game.
        // Wait for its SDK hide call to complete BEFORE opening a fullscreen ad;
        // otherwise the two placements can overlap when gameplay is interrupted.
        void state.bannerSyncPromise.then(() => {
            if(state.bannerShowing){
                throw new Error('Fullscreen ad blocked: sticky banner could not be hidden');
            }
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
            if(result && typeof result.then === 'function'){
                result.catch(error => finalize('error', error));
            }
        }).catch(error => finalize('error', error));
        return true;
    }

    function beginMenuFullscreenAdv(){
        state.menuAdRequestedAt = performance.now();
        mark('data-yandex-menu-ad-intent', 'ready');
    }

    async function showMenuFullscreenAdv(){
        // beginMenuFullscreenAdv() is called at the start of the user's Back action,
        // before Mindustry performs its local save/reset. Measure the full click-to-ad
        // path that Yandex moderation observes.
        const requestedAt = state.menuAdRequestedAt || performance.now();
        state.menuAdRequestedAt = 0;
        if(state.gameplayActive) gameplayStop();

        const storage = globalThis.__mindustryStorage;
        if(storage && typeof storage.lifecycleFlush === 'function'){
            mark('data-yandex-menu-ad-storage', 'pending');
            let durable = false;
            try{
                durable = await withTimeout(
                    storage.lifecycleFlush('before-menu-ad'),
                    1200,
                    'Pre-ad storage flush'
                );
            }catch(error){
                mark('data-yandex-menu-ad-storage', 'timeout');
                mark('data-yandex-menu-ad-state', 'skipped-storage-timeout');
                return false;
            }
            mark('data-yandex-menu-ad-storage', durable ? 'ready' : 'error');
            if(!durable) return false;
        }

        if(!state.ysdk || !state.ysdk.adv || typeof state.ysdk.adv.showFullscreenAdv !== 'function'){
            mark('data-yandex-menu-ad-state', 'unavailable');
            void syncCloudCheckpoint('menu-transition', true);
            return false;
        }

        // The durability barrier is asynchronous. If the player already started another
        // sector/map while it was completing, this is no longer a menu transition and
        // the interstitial must not interrupt the newly resumed gameplay.
        if(state.gameplayActive){
            mark('data-yandex-menu-ad-state', 'cancelled-gameplay-resumed');
            return false;
        }

        mark('data-yandex-menu-ad-delay-ms', String(Math.round(performance.now() - requestedAt)));
        mark('data-yandex-menu-ad-state', 'requested');
        const requested = showFullscreenAdv({
            onOpen: () => mark('data-yandex-menu-ad-state', 'open'),
            onClose: wasShown => mark('data-yandex-menu-ad-state', wasShown ? 'closed-shown' : 'closed-not-shown'),
            onError: () => mark('data-yandex-menu-ad-state', 'error')
        });

        // Cloud persistence still runs for this transition, but it must not delay the ad.
        // game_api_pause will coalesce with this call through cloudSyncPromise.
        void syncCloudCheckpoint('menu-transition', true);
        return requested;
    }

    async function getPlayer(){
        if(!state.ysdk || typeof state.ysdk.getPlayer !== 'function') return null;
        if(!state.playerPromise){
            const lookup = withTimeout(Promise.resolve().then(() => state.ysdk.getPlayer()), 2500, 'Yandex player').catch(error => {
                // A temporary player-service error must not poison every later save.
                if(state.playerPromise === lookup) state.playerPromise = null;
                throw error;
            });
            state.playerPromise = lookup;
        }
        return state.playerPromise;
    }

    function withTimeout(promise, milliseconds, label){
        let timer = 0;
        return Promise.race([
            Promise.resolve(promise),
            new Promise((_, reject) => {
                timer = setTimeout(() => reject(new Error(label + ' timed out')), milliseconds);
            })
        ]).finally(() => clearTimeout(timer));
    }

    function settingsEntries(payload){
        const entries = new Map();
        if(typeof payload !== 'string' || !payload.startsWith('MWS1|')) return entries;
        let cursor = 5;
        const part = () => {
            const colon = payload.indexOf(':', cursor);
            if(colon < 0) throw new Error('Malformed browser settings length');
            const digits = payload.slice(cursor, colon);
            if(!/^\d+$/.test(digits)) throw new Error('Malformed browser settings length');
            const length = Number(digits);
            const start = colon + 1;
            const end = start + length;
            if(!Number.isInteger(length) || length < 0 || end > payload.length){
                throw new Error('Malformed browser settings field');
            }
            cursor = end;
            return payload.slice(start, end);
        };
        while(cursor < payload.length){
            const type = payload[cursor++];
            if(!'bilfsx'.includes(type)) throw new Error('Unknown browser settings type');
            const key = part();
            const value = part();
            validateSettingValue(type, value);
            entries.set(key, {type, value});
        }
        return entries;
    }

    function settingsValue(payload, wanted){
        const entry = settingsEntries(payload).get(wanted);
        return entry ? entry.value : '';
    }

    function validateSettingValue(type, value){
        if(type === 'b' && value !== '0' && value !== '1') throw new Error('Invalid boolean setting');
        if(type === 'i' || type === 'l'){
            if(!/^[+-]?\d+$/.test(value)) throw new Error('Invalid integer setting');
            const negative = value[0] === '-';
            const digits = value.replace(/^[+-]/, '').replace(/^0+/, '') || '0';
            const limit = type === 'i' ? (negative ? '2147483648' : '2147483647') :
                (negative ? '9223372036854775808' : '9223372036854775807');
            // Compare decimal strings so a Java long never loses precision in JS.
            if(digits.length > limit.length || digits.length === limit.length && digits > limit){
                throw new Error('Integer setting out of range');
            }
        }
        if(type === 'f' && !/^[+-]?(?:NaN|Infinity|(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)$/.test(value)){
            throw new Error('Invalid float setting');
        }
        if(type === 'x' && (value.length % 2 || /[^0-9a-fA-F]/.test(value))){
            throw new Error('Invalid binary setting');
        }
    }

    function bytesToBase64(bytes){
        // TeaVM exposes Java byte[] as Int8Array, so values >= 0x80 arrive as
        // negative numbers. String.fromCharCode(-1) becomes U+FFFF, which btoa()
        // rejects because it only accepts Latin-1 code units. Reinterpret the exact
        // same backing bytes as unsigned before constructing the binary string.
        const unsigned = bytes instanceof Uint8Array
            ? bytes
            : new Uint8Array(bytes.buffer, bytes.byteOffset || 0, bytes.byteLength);
        let binary = '';
        const chunk = 0x4000;
        for(let offset = 0; offset < unsigned.length; offset += chunk){
            binary += String.fromCharCode(...unsigned.subarray(offset, Math.min(unsigned.length, offset + chunk)));
        }
        return btoa(binary);
    }

    function base64ToBytes(value){
        const binary = atob(String(value || ''));
        const out = new Uint8Array(binary.length);
        for(let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i) & 0xff;
        return out;
    }

    function encodedBytes(value){
        return new TextEncoder().encode(JSON.stringify(value)).byteLength;
    }

    function localSettings(storage){
        const bytes = storage && storage.get(settingsPath);
        if(bytes) return new TextDecoder().decode(bytes);
        try{ return localStorage.getItem(settingsKey) || ''; }catch(_ignored){ return ''; }
    }

    function resetMarker(payload){
        const entry = settingsEntries(payload).get(resetKey);
        return entry && entry.type === 'l' && /^[1-9]\d*$/.test(entry.value) ? entry.value : '';
    }

    function campaignSetting(key){
        return key === resetKey || ['last-sector-save','unlocks','turn','utimei','launch-resources-seq','localgamemode'].includes(key)
            || key.startsWith('req-') || key.endsWith('-unlocked') || key.startsWith('lastloadout-') || key.startsWith('save-')
            || /^(?:serpulo|erekir)-(?:s-\d+-info|campaign-rules|campaign-stats|last-sector)$/.test(key);
    }

    function mergedCloudSettings(remote, local){
        const entries = settingsEntries(remote);
        try{
            for(const [key, entry] of settingsEntries(local)){
                if(!campaignSetting(key)) entries.set(key, entry);
            }
        }catch(error){
            console.info('Corrupt local preferences were not merged into cloud recovery:', error.message);
        }
        let payload = 'MWS1|';
        for(const [key, entry] of entries){
            payload += entry.type + key.length + ':' + key + entry.value.length + ':' + entry.value;
        }
        return payload;
    }

    function resetIntent(){
        let raw;
        try{ raw = localStorage.getItem(resetIntentKey); }catch(_ignored){ return null; }
        if(!raw) return null;
        const intent = JSON.parse(raw);
        if(intent.schema !== 1 || typeof intent.settings !== 'string' || !intent.settings.startsWith('MWS1|')
            || !Array.isArray(intent.retain) || intent.retain.length > 10000
            || intent.retain.some(path => typeof path !== 'string' || path.startsWith('/') || path.split('/').includes('..'))
            || !resetMarker(intent.settings)) throw new Error('Invalid durable reset intent');
        return intent;
    }

    function onSettingsStored(payload, force = false){
        try{
            const marker = resetMarker(payload);
            if(!marker) return null;
            const storage = globalThis.__mindustryStorage;
            if(!storage) return null;
            let previous;
            try{ previous = resetIntent(); }catch(_ignored){ previous = null; }
            if(!force && marker === state.cloudResetAt && !previous) return state.cloudResetCommit;
            state.cloudResetAt = marker;
            state.cloudRestoreReady = true;
            const generation = ++state.cloudResetGeneration;
            const intent = {schema:1, settings:payload, retain:storage.paths()};
            // Write ahead synchronously: a reload before IndexedDB commits must not
            // revive deleted files from the old durable tree.
            try{ localStorage.setItem(resetIntentKey, JSON.stringify(intent)); }
            catch(error){ mark('data-yandex-reset-intent', 'unavailable'); }
            mark('data-yandex-reset-state', 'pending');
            const pending = Promise.resolve().then(() => storage.flush()).then(async () => {
                if(generation !== state.cloudResetGeneration) return false;
                state.cloudResetDurableAt = marker;
                try{ localStorage.removeItem(resetIntentKey); }catch(_ignored){}
                mark('data-yandex-reset-state', 'durable');
                return syncCloudCheckpoint('confirmed-data-reset', true);
            }).catch(error => {
                if(generation === state.cloudResetGeneration){
                    mark('data-yandex-reset-state', 'error');
                    mark('data-yandex-reset-error', String(error && error.message || error));
                    console.error('Confirmed game-data reset is not durable:', error);
                }
                return false;
            }).finally(() => { if(state.cloudResetCommit === pending) state.cloudResetCommit = null; });
            state.cloudResetCommit = pending;
            return pending;
        }catch(error){
            mark('data-yandex-reset-state', 'error');
            mark('data-yandex-reset-error', String(error && error.message || error));
            return Promise.resolve(false);
        }
    }

    function buildCloudCheckpoint(){
        const storage = globalThis.__mindustryStorage;
        if(!storage) return null;

        let settings = '';
        const durableSettings = storage.get(settingsPath);
        if(durableSettings) settings = new TextDecoder().decode(durableSettings);
        if(!settings) try{ settings = localStorage.getItem(settingsKey) || ''; }catch(_ignored){}
        if(settings && !settings.startsWith('MWS1|')) settings = '';

        let lastSector = '';
        try{
            settingsValue(settings, null);
            lastSector = settingsValue(settings, 'last-sector-save');
        }catch(error){
            mark('data-yandex-cloud-state', 'invalid-settings');
            return null;
        }
        const campaignPath = /^sector-(?:serpulo|erekir)-\d+$/.test(lastSector)
            ? 'saves/' + lastSector + '.msav' : '';
        const localPath = 'saves/web-local-survival.msav';
        const files = Object.create(null);

        const add = path => {
            if(!path || !storage.exists(path)) return;
            const bytes = storage.get(path);
            if(bytes && bytes.byteLength >= 128) files[path] = bytesToBase64(bytes);
        };
        add(campaignPath);
        add(localPath);

        const compact = () => ({schema: 1, settings, files});
        let stable = compact();
        if(encodedBytes(stable) > cloudBudgetBytes && files[localPath]){
            delete files[localPath];
            stable = compact();
        }
        if(encodedBytes(stable) > cloudBudgetBytes && campaignPath && files[campaignPath]){
            delete files[campaignPath];
            stable = compact();
        }
        const bytes = encodedBytes(stable);
        if(bytes > cloudBudgetBytes){
            mark('data-yandex-cloud-state', 'too-large');
            mark('data-yandex-cloud-bytes', String(bytes));
            return null;
        }

        const canonical = JSON.stringify(stable);
        return {
            canonical,
            bytes,
            snapshot: {
                schema: 1,
                savedAt: Date.now(),
                settings,
                files
            }
        };
    }

    async function syncCloudCheckpoint(reason, flush = true){
        if(!state.available){
            mark('data-yandex-cloud-state', 'unavailable');
            return false;
        }
        const storage = globalThis.__mindustryStorage;
        if(!storage || root.getAttribute('data-mindustry-storage') !== 'ready' ||
            !state.cloudRestoreReady && !storage.paths().some(path => cloudFilePattern.test(path))){
            // SDK pause may arrive before IndexedDB hydration or cloud restore.
            // Never replace remote progress with an incomplete startup snapshot.
            mark('data-yandex-cloud-state', 'waiting-storage');
            return false;
        }
        if(state.cloudSyncPromise){
            // A checkpoint written during an upload must get its own latest
            // snapshot afterwards, rather than inherit the older upload's result.
            state.cloudSyncQueued = {
                reason,
                flush: Boolean(flush || state.cloudSyncQueued && state.cloudSyncQueued.flush)
            };
            return state.cloudSyncPromise;
        }

        const save = async (reason, flush) => {
            // Every upload after an explicit reset requires its local deletion and
            // settings marker to pass the durability barrier first.
            if(resetMarker(localSettings(storage))) await storage.flush();
            const built = buildCloudCheckpoint();
            if(!built) return false;
            if(built.canonical === state.cloudLastPayload &&
                (!flush || built.canonical === state.cloudLastFlushedPayload)){
                mark('data-yandex-cloud-state', 'unchanged');
                return true;
            }

            const player = await withTimeout(getPlayer(), 2500, 'Yandex player');
            if(!player || typeof player.setData !== 'function'){
                mark('data-yandex-cloud-state', 'player-unavailable');
                return false;
            }

            mark('data-yandex-cloud-state', 'saving');
            const resetAtUpload = resetMarker(built.snapshot.settings);
            const upload = Promise.resolve(player.setData({[cloudKey]: built.snapshot}, Boolean(flush)));
            upload.then(() => {
                const currentReset = resetMarker(localSettings(storage));
                if(currentReset && currentReset !== resetAtUpload){
                    // A timed-out older SDK write can acknowledge after the reset
                    // upload. Reassert the latest durable state even if its normal
                    // canonical cache would otherwise classify it as unchanged.
                    state.cloudLastPayload = '';
                    state.cloudLastFlushedPayload = '';
                    void syncCloudCheckpoint('late-before-reset-upload', true);
                }
            }, () => {}).catch(error => console.info('Yandex late cloud acknowledgement ignored:', error));
            await withTimeout(upload, 3500, 'Yandex cloud save');
            state.cloudLastPayload = built.canonical;
            state.cloudLastFlushedPayload = flush ? built.canonical : '';
            mark('data-yandex-cloud-state', 'saved');
            mark('data-yandex-cloud-reason', String(reason || 'unknown'));
            mark('data-yandex-cloud-bytes', String(built.bytes));
            mark('data-yandex-cloud-files', String(Object.keys(built.snapshot.files).length));
            return true;
        };
        const failed = error => {
            mark('data-yandex-cloud-state', 'error');
            mark('data-yandex-cloud-error', String(error && error.message ? error.message : error));
            console.info('Yandex cloud checkpoint save unavailable:', error && error.message ? error.message : error);
            return false;
        };
        state.cloudSyncPromise = (async () => {
            try{
                let next = {reason, flush};
                let result = false;
                while(next){
                    state.cloudSyncQueued = null;
                    result = await save(next.reason, next.flush).catch(failed);
                    next = state.cloudSyncQueued;
                }
                return result;
            }finally{
                // Clear synchronously with the last queue check. An outer
                // Promise.finally leaves a microtask gap that can lose a caller.
                state.cloudSyncQueued = null;
                state.cloudSyncPromise = null;
            }
        })();

        return state.cloudSyncPromise;
    }

    async function restoreCloudCheckpoint(){
        const storage = globalThis.__mindustryStorage;
        if(!storage){
            mark('data-yandex-cloud-restore', 'storage-unavailable');
            return false;
        }
        if(root.getAttribute('data-mindustry-storage') !== 'ready'){
            mark('data-yandex-cloud-restore', 'waiting-storage');
            return false;
        }
        try{
            const intent = resetIntent();
            if(intent){
                const retain = new Set(intent.retain);
                for(const path of storage.paths()) if(!retain.has(path)) storage.remove(path);
                const bytes = new TextEncoder().encode(intent.settings);
                storage.put(settingsPath, bytes, bytes.byteLength);
                await onSettingsStored(intent.settings, true);
                if(state.cloudResetDurableAt !== resetMarker(intent.settings)) throw new Error('Reset replay did not become durable');
                try{ localStorage.setItem(settingsKey, intent.settings); }catch(_ignored){}
                mark('data-yandex-cloud-restore', 'skipped-local-reset');
                return false;
            }
            const payload = localSettings(storage);
            if(resetMarker(payload)){
                state.cloudRestoreReady = true;
                void onSettingsStored(payload, true);
                mark('data-yandex-cloud-restore', 'skipped-local-reset');
                return false;
            }
        }catch(error){
            mark('data-yandex-cloud-restore', 'reset-error');
            mark('data-yandex-reset-error', String(error && error.message || error));
            return false;
        }
        if(!state.available){
            mark('data-yandex-cloud-restore', 'unavailable');
            return false;
        }
        const resetGeneration = state.cloudResetGeneration;

        if(storage.paths().some(path => cloudFilePattern.test(path))){
            state.cloudRestoreReady = true;
            mark('data-yandex-cloud-restore', 'skipped-local-present');
            return false;
        }

        try{
            const player = await withTimeout(getPlayer(), 2500, 'Yandex player');
            if(!player || typeof player.getData !== 'function'){
                mark('data-yandex-cloud-restore', 'player-unavailable');
                return false;
            }

            mark('data-yandex-cloud-restore', 'loading');
            const data = await withTimeout(player.getData([cloudKey]), 3500, 'Yandex cloud load');
            const snapshot = data && data[cloudKey];
            if(state.cloudResetGeneration !== resetGeneration || resetMarker(localSettings(storage))){
                mark('data-yandex-cloud-restore', 'skipped-local-reset');
                return false;
            }
            if(storage.paths().some(path => cloudFilePattern.test(path))){
                state.cloudRestoreReady = true;
                mark('data-yandex-cloud-restore', 'skipped-local-present');
                return false;
            }
            if(!snapshot || snapshot.schema !== 1){
                mark('data-yandex-cloud-restore', 'empty');
                if(!snapshot) state.cloudRestoreReady = true;
                return false;
            }

            // Validate and decode the entire snapshot before changing local files.
            if(encodedBytes({schema:1, settings:snapshot.settings || '', files:snapshot.files || {}}) > cloudBudgetBytes){
                throw new Error('Cloud checkpoint exceeds restore budget');
            }
            let settingsBytes = null;
            let remoteSettings = '';
            if(snapshot.settings){
                if(typeof snapshot.settings !== 'string' || !snapshot.settings.startsWith('MWS1|')){
                    throw new Error('Invalid cloud settings payload');
                }
                // A null lookup scans every field instead of stopping at a key.
                settingsValue(snapshot.settings, null);
                remoteSettings = snapshot.settings;
            }
            const localPayload = localSettings(storage);
            if(remoteSettings || localPayload) settingsBytes = new TextEncoder().encode(mergedCloudSettings(remoteSettings, localPayload));

            const decodedFiles = [];
            if(snapshot.files != null && (typeof snapshot.files !== 'object' || Array.isArray(snapshot.files))){
                throw new Error('Invalid cloud save file list');
            }
            const files = snapshot.files && typeof snapshot.files === 'object' ? snapshot.files : {};
            for(const [path, encoded] of Object.entries(files)){
                if(!cloudFilePattern.test(path)) continue;
                if(typeof encoded !== 'string') throw new Error('Invalid cloud save encoding: ' + path);
                const bytes = base64ToBytes(encoded);
                if(bytes.byteLength < 128) throw new Error('Incomplete cloud save: ' + path);
                decodedFiles.push([path, bytes]);
            }
            if(settingsBytes) storage.put(settingsPath, settingsBytes, settingsBytes.byteLength);
            for(const [path, bytes] of decodedFiles){
                storage.put(path, bytes, bytes.byteLength);
            }
            await storage.flush();
            if(state.cloudResetGeneration !== resetGeneration){
                mark('data-yandex-cloud-restore', 'skipped-local-reset');
                return false;
            }
            if(settingsBytes){
                const payload = new TextDecoder().decode(settingsBytes);
                state.cloudResetAt = resetMarker(payload);
                try{ localStorage.setItem(settingsKey, payload); }catch(_ignored){}
            }
            state.cloudRestoreReady = true;
            mark('data-yandex-cloud-restore', 'ready');
            mark('data-yandex-cloud-restored-files', String(decodedFiles.length));
            mark('data-yandex-cloud-restored-settings', snapshot.settings ? 'yes' : 'no');
            return true;
        }catch(error){
            mark('data-yandex-cloud-restore', 'error');
            mark('data-yandex-cloud-restore-error', String(error && error.message ? error.message : error));
            console.info('Yandex cloud checkpoint restore unavailable:', error && error.message ? error.message : error);
            return false;
        }
    }

    globalThis.__mindustryYandex = Object.assign(state, {
        init,
        loadingReady,
        gameplayStart,
        gameplayStop,
        syncStickyBanner,
        toggleFullscreen,
        showFullscreenAdv,
        beginMenuFullscreenAdv,
        showMenuFullscreenAdv,
        getPlayer,
        syncCloudCheckpoint,
        restoreCloudCheckpoint,
        onSettingsStored
    });
})();

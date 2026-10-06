package mindustry.web;

import arc.*;
import arc.backend.web.*;
import mindustry.*;
import org.teavm.jso.*;

/** Concrete TeaVM browser scheduler for the current Arc Application API. */
public final class BrowserApplication extends WebApplicationBase{
    @JSFunctor
    private interface FrameCallback extends JSObject{
        void run(double timestamp);
    }

    @JSFunctor
    private interface LifecycleCallback extends JSObject{
        void run();
    }

    private boolean resizePending = true;
    private final FrameCallback frameCallback = this::onAnimationFrame;
    private final BrowserCanvas.ResizeCallback resizeCallback = () -> resizePending = true;
    private final LifecycleCallback platformPauseCallback = () -> setPlatformPaused(true);
    private final LifecycleCallback platformResumeCallback = () -> setPlatformPaused(false);
    private final WebGraphics graphics;
    private final WebInput input;
    private final BrowserGL20 gl20;
    private final boolean mobileBrowser;
    private final float pixelRatioCap;
    private String clipboard = "";
    private boolean platformPaused;
    private boolean lastPlatformPaused;
    private boolean lastGameplayActive;
    private boolean awaitingPlatformResumeFrame;
    private int browserFrameCallbacks;

    public BrowserApplication(ApplicationListener listener, WebConfig config){
        super(listener, config);

        mobileBrowser = detectMobileBrowser();
        pixelRatioCap = mobileBrowser ? Math.min(config.maxPixelRatio, 1.5f) : config.maxPixelRatio;
        markBrowserInputMode(mobileBrowser ? "mobile" : "desktop");
        markPixelRatioPolicy(pixelRatioCap, mobileBrowser ? "mobile" : "desktop");

        if(!BrowserCanvas.initialize(config.canvasId, config.alpha, config.stencil, config.antialiasing,
        config.premultipliedAlpha, config.preserveDrawingBuffer)){
            throw new IllegalStateException("WebGL is not available in this browser.");
        }

        graphics = new WebGraphics(config);
        graphics.setWebGLVersion(BrowserCanvas.getWebGLMajor(config.canvasId));
        gl20 = new BrowserGL20(BrowserCanvas.getContext(config.canvasId));
        graphics.setGL20(gl20);
        BrowserCanvas.resizeToDisplay(config.canvasId, pixelRatioCap);
        updateGraphicsMetrics();
        BrowserCanvas.installResizeSignal(config.canvasId, resizeCallback);
        Core.graphics = graphics;

        input = new WebInput();
        Core.input = input;
        BrowserInputBridge.install(config.canvasId, input);

        platformPaused = BrowserYandex.paused();
        lastPlatformPaused = platformPaused;
        if(platformPaused) BrowserYandex.markPauseState("paused");
        installPlatformLifecycle(platformPauseCallback, platformResumeCallback);
        markFramePolicies();

        initialize();
        requestAnimationFrame(frameCallback);
    }

    @Override
    public boolean isMobile(){
        return mobileBrowser;
    }

    private void onAnimationFrame(double timestamp){
        if(!isRunning()) return;

        int callbackIndex = ++browserFrameCallbacks;
        boolean traceStartup = callbackIndex <= 3;
        String phase = "entry";
        try{
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "resize";
            // DOM resize events signal the next frame immediately. A low-frequency fallback
            // catches rare DPR changes that do not dispatch resize/orientation events.
            boolean resizeFallback = (callbackIndex & 63) == 0;
            if(resizePending || resizeFallback){
                resizePending = false;
                if(BrowserCanvas.resizeToDisplay(config.canvasId, pixelRatioCap)){
                    updateGraphicsMetrics();
                    resize(graphics.getWidth(), graphics.getHeight());
                }
            }
            graphics.updateFrame(timestamp);
            input.update();
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "pause-sample";
            // Yandex pause/resume events are primary. Sample the shared state only as a
            // self-healing fallback instead of crossing the JS boundary every frame.
            if((callbackIndex & 63) == 0){
                boolean sampledPause = BrowserYandex.paused();
                if(sampledPause != platformPaused) setPlatformPaused(sampledPause);
            }
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "application-frame";
            if(!platformPaused){
                // Keep the concrete TeaVM scheduler split into explicit named stages so
                // browser failures still report whether globals, listeners or posted tasks failed.
                phase = "frame-globals";
                if(Core.settings == null){
                    arc.util.Time.updateGlobal();
                }else{
                    Core.settings.autosave();
                    arc.util.Time.updateGlobal();
                }

                phase = "frame-listeners";
                listen(ApplicationListener::update);

                phase = "frame-post";
                runPostedTasks();

                phase = "gameplay-sync";
                syncGameplayMarker();

                if(awaitingPlatformResumeFrame){
                    phase = "resume-frame";
                    awaitingPlatformResumeFrame = false;
                    int resumedSector = Vars.state != null && Vars.state.rules != null && Vars.state.rules.sector != null
                        ? Vars.state.rules.sector.id : -1;
                    BrowserYandex.markResumeFrame(browserFrameCallbacks,
                        Vars.state != null && Vars.state.isPlaying(), resumedSector);
                }
            }
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "input-post-update";
            input.postUpdate();
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "reschedule";
            requestAnimationFrame(frameCallback);
            if(traceStartup) markFrameStage("scheduled", callbackIndex);
        }catch(Throwable error){
            BrowserCanvas.setStatus("error", "Mindustry Web frame loop failed at " + phase + " #" + callbackIndex + ": " + describe(error));
            throw error;
        }
    }

    private static String describe(Throwable error){
        // Basin is already localized to a world-processor LogicBlock. Keep only the
        // primary message plus the lean Logic and LogicBlock subphase breadcrumbs; the
        // temporary generic per-building name trace is intentionally gone from the
        // production hot path and TeaVM graph.
        String out = String.valueOf(error.getMessage())
            + " [lp=" + (Vars.logic == null ? -1 : Vars.logic.webPhase) + "]";
        if(Vars.logic != null) out += " [bp=" + Vars.logic.webBuildPhase + "]";
        return out;
    }

    private void setPlatformPaused(boolean paused){
        platformPaused = paused;
        if(paused == lastPlatformPaused) return;
        lastPlatformPaused = paused;
        BrowserYandex.markPauseState(paused ? "paused" : "running");

        // Yandex game_api_pause/resume is an application lifecycle boundary, not only a
        // scheduler gate. Propagate it through Arc so BrowserAudio and every other
        // ApplicationListener can suspend/resume their platform resources correctly.
        if(paused){
            // Ads/tab switches can take focus without matching DOM keyup/pointerup
            // events. Clear browser-held controls before freezing Arc's frame loop.
            BrowserInputBridge.releaseAll(config.canvasId, "platform-pause");
            pause();
        }else{
            resume();
            awaitingPlatformResumeFrame = true;
        }
    }

    private void syncGameplayMarker(){
        boolean gameplayActive = Vars.state != null && Vars.state.isPlaying();
        if(gameplayActive == lastGameplayActive) return;

        lastGameplayActive = gameplayActive;
        if(gameplayActive){
            BrowserYandex.gameplayStart();
        }else{
            BrowserYandex.gameplayStop();
        }
    }

    private void updateGraphicsMetrics(){
        graphics.updateSize(
            BrowserCanvas.getClientWidth(config.canvasId),
            BrowserCanvas.getClientHeight(config.canvasId),
            BrowserCanvas.getBackBufferWidth(config.canvasId),
            BrowserCanvas.getBackBufferHeight(config.canvasId),
            BrowserCanvas.getDensity(config.canvasId, pixelRatioCap)
        );
    }

    @Override
    public String getClipboardText(){
        return clipboard;
    }

    @Override
    public void setClipboardText(String text){
        clipboard = text == null ? "" : text;
        writeClipboard(clipboard);
    }

    @Override
    public boolean openURI(String uri){
        markExternalNavigationBlocked();
        return false;
    }

    @Override
    public void exit(){
        super.exit();
        BrowserYandex.gameplayStop();
        BrowserCanvas.setStatus("stopped", "Mindustry Web runtime stopped");
    }

    @JSBody(script = """
        const root = document.documentElement;
        const forced = new URLSearchParams(location.search).get('mindustryMobile');
        if(forced === '1' || forced === '0'){
            root.setAttribute('data-mindustry-device-source', 'query-override');
            return forced === '1';
        }

        const platform = globalThis.__mindustryYandex;
        const type = String(platform && platform.deviceType || '').toLowerCase();
        if(type){
            root.setAttribute('data-mindustry-device-source', 'yandex-sdk');
            if(type === 'mobile' || type === 'tablet') return true;
            if(type === 'desktop' || type === 'tv') return false;
        }

        const points = Number(navigator.maxTouchPoints || 0);
        const coarse = !!(globalThis.matchMedia && matchMedia('(pointer: coarse)').matches);
        const noHover = !!(globalThis.matchMedia && matchMedia('(hover: none)').matches);
        root.setAttribute('data-mindustry-device-source', 'browser-fallback');
        return points > 0 && (coarse || noHover);
        """)
    private static native boolean detectMobileBrowser();

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-input-mode', mode);")
    private static native void markBrowserInputMode(String mode);

    @JSBody(params = {"cap", "mode"}, script = "document.documentElement.setAttribute('data-mindustry-pixel-ratio-cap', String(cap)); document.documentElement.setAttribute('data-mindustry-pixel-ratio-policy', mode === 'mobile' ? 'mobile-1.5x' : 'desktop-config');")
    private static native void markPixelRatioPolicy(float cap, String mode);

    @JSBody(params = {"callback"}, script = "window.requestAnimationFrame(callback);")
    private static native void requestAnimationFrame(FrameCallback callback);

    @JSBody(params = {"phase", "index"}, script = "document.documentElement.setAttribute('data-mindustry-frame-stage', phase); document.documentElement.setAttribute('data-mindustry-frame-callbacks', String(index));")
    private static native void markFrameStage(String phase, int index);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-pause-policy','event-driven-64-frame-fallback'); document.documentElement.setAttribute('data-mindustry-frame-resize-policy','event-driven-64-frame-fallback');")
    private static native void markFramePolicies();

    @JSBody(params = {"pauseCallback", "resumeCallback"}, script = """
        window.addEventListener('mindustry:yandex-pause', pauseCallback);
        window.addEventListener('mindustry:yandex-resume', resumeCallback);
        """)
    private static native void installPlatformLifecycle(LifecycleCallback pauseCallback, LifecycleCallback resumeCallback);

    @JSBody(params = {"text"}, script = """
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).catch(() => {});
        }
        """)
    private static native void writeClipboard(String text);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-navigation', 'blocked');")
    private static native void markExternalNavigationBlocked();
}

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

    private final FrameCallback frameCallback = this::onAnimationFrame;
    private final LifecycleCallback platformPauseCallback = () -> setPlatformPaused(true);
    private final LifecycleCallback platformResumeCallback = () -> setPlatformPaused(false);
    private final WebGraphics graphics;
    private final WebInput input;
    private final BrowserGL20 gl20;
    private String clipboard = "";
    private boolean platformPaused;
    private boolean lastPlatformPaused;
    private boolean lastGameplayActive;
    private int browserFrameCallbacks;

    public BrowserApplication(ApplicationListener listener, WebConfig config){
        super(listener, config);

        if(!BrowserCanvas.initialize(config.canvasId, config.alpha, config.stencil, config.antialiasing,
        config.premultipliedAlpha, config.preserveDrawingBuffer)){
            throw new IllegalStateException("WebGL is not available in this browser.");
        }

        graphics = new WebGraphics(config);
        graphics.setWebGLVersion(BrowserCanvas.getWebGLMajor(config.canvasId));
        gl20 = new BrowserGL20(BrowserCanvas.getContext(config.canvasId));
        graphics.setGL20(gl20);
        BrowserCanvas.resizeToDisplay(config.canvasId, config.maxPixelRatio);
        updateGraphicsMetrics();
        Core.graphics = graphics;

        input = new WebInput();
        Core.input = input;
        BrowserInputBridge.install(config.canvasId, input);

        // Portal pause/resume events are external to requestAnimationFrame. Observe them
        // directly so a resume can be accepted even while a browser throttles animation
        // frames for an ad, background tab or other platform interruption. A sampled
        // fallback remains in onAnimationFrame in case an event was emitted before this
        // application object was installed.
        platformPaused = BrowserYandex.paused();
        lastPlatformPaused = platformPaused;
        if(platformPaused) BrowserYandex.markPauseState("paused");
        installPlatformLifecycle(platformPauseCallback, platformResumeCallback);

        initialize();
        requestAnimationFrame(frameCallback);
    }

    private void onAnimationFrame(double timestamp){
        if(!isRunning()) return;

        int callbackIndex = ++browserFrameCallbacks;
        // Frame-stage DOM attributes were added as startup diagnostics. Updating two
        // attributes six times at 60 FPS caused hundreds of DOM mutations per second.
        // Keep the same diagnostics for the first three startup frames only; errors are
        // still reported through BrowserCanvas.setStatus below.
        boolean traceStartup = callbackIndex <= 3;
        String phase = "entry";
        try{
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "resize";
            if(BrowserCanvas.resizeToDisplay(config.canvasId, config.maxPixelRatio)){
                updateGraphicsMetrics();
                resize(graphics.getWidth(), graphics.getHeight());
            }
            graphics.updateFrame(timestamp);
            input.update();
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "pause-sample";
            boolean sampledPause = BrowserYandex.paused();
            if(sampledPause != platformPaused) setPlatformPaused(sampledPause);
            if(traceStartup) markFrameStage(phase, callbackIndex);

            // game_api_pause is sent for ads, tab/background changes and other portal
            // interruptions. Keep the scheduler alive, but do not advance Mindustry
            // simulation/render callbacks while the platform is paused.
            phase = "application-frame";
            if(!platformPaused){
                frame();
                syncGameplayMarker();
            }
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "input-post-update";
            input.postUpdate();
            if(traceStartup) markFrameStage(phase, callbackIndex);

            phase = "reschedule";
            requestAnimationFrame(frameCallback);
            if(traceStartup) markFrameStage("scheduled", callbackIndex);
        }catch(Throwable error){
            BrowserCanvas.setStatus("error", "Mindustry Web frame loop failed at " + phase + " #" + callbackIndex + ": "
                + error.getClass().getName() + ": " + String.valueOf(error.getMessage()));
            throw error;
        }
    }

    private void setPlatformPaused(boolean paused){
        platformPaused = paused;
        if(paused == lastPlatformPaused) return;
        lastPlatformPaused = paused;
        BrowserYandex.markPauseState(paused ? "paused" : "running");
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
            BrowserCanvas.getDensity(config.canvasId, config.maxPixelRatio)
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
        // Yandex Games must not expose navigation to the upstream project's website,
        // GitHub, Discord, stores or any other external resource. Keep this blocked at
        // the platform boundary so future upstream UI additions cannot re-enable it.
        markExternalNavigationBlocked();
        return false;
    }

    @Override
    public void exit(){
        super.exit();
        BrowserYandex.gameplayStop();
        BrowserCanvas.setStatus("stopped", "Mindustry Web runtime stopped");
    }

    @JSBody(params = {"callback"}, script = "window.requestAnimationFrame(callback);")
    private static native void requestAnimationFrame(FrameCallback callback);

    @JSBody(params = {"phase", "index"}, script = "document.documentElement.setAttribute('data-mindustry-frame-stage', phase); document.documentElement.setAttribute('data-mindustry-frame-callbacks', String(index));")
    private static native void markFrameStage(String phase, int index);

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

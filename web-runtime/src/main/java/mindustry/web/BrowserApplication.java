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
    private final boolean mobileBrowser;
    private String clipboard = "";
    private boolean platformPaused;
    private boolean lastPlatformPaused;
    private boolean lastGameplayActive;
    private int browserFrameCallbacks;

    public BrowserApplication(ApplicationListener listener, WebConfig config){
        super(listener, config);

        mobileBrowser = detectMobileBrowser();
        markBrowserInputMode(mobileBrowser ? "mobile" : "desktop");

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

        platformPaused = BrowserYandex.paused();
        lastPlatformPaused = platformPaused;
        if(platformPaused) BrowserYandex.markPauseState("paused");
        installPlatformLifecycle(platformPauseCallback, platformResumeCallback);

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
            BrowserCanvas.setStatus("error", "Mindustry Web frame loop failed at " + phase + " #" + callbackIndex + ": " + describe(error));
            throw error;
        }
    }

    private static String describe(Throwable error){
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while(current != null && depth++ < 4){
            if(out.length() > 0) out.append(" <- ");
            out.append(current.getClass().getName()).append(": ").append(String.valueOf(current.getMessage()));
            StackTraceElement[] stack = current.getStackTrace();
            int frames = Math.min(stack == null ? 0 : stack.length, 10);
            for(int i = 0; i < frames; i++){
                out.append(" @ ").append(stack[i].getClassName()).append('.').append(stack[i].getMethodName())
                    .append(':').append(stack[i].getLineNumber());
            }
            current = current.getCause();
        }
        return out.toString();
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
        const forced = new URLSearchParams(location.search).get('mindustryMobile');
        if(forced === '1') return true;
        if(forced === '0') return false;
        const points = Number(navigator.maxTouchPoints || 0);
        const coarse = !!(globalThis.matchMedia && matchMedia('(pointer: coarse)').matches);
        const noHover = !!(globalThis.matchMedia && matchMedia('(hover: none)').matches);
        return points > 0 && (coarse || noHover);
        """)
    private static native boolean detectMobileBrowser();

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-input-mode', mode);")
    private static native void markBrowserInputMode(String mode);

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

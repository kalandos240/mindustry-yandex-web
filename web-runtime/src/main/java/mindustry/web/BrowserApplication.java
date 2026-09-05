package mindustry.web;

import arc.*;
import arc.backend.web.*;
import org.teavm.jso.*;

/** Concrete TeaVM browser scheduler for the current Arc Application API. */
public final class BrowserApplication extends WebApplicationBase{
    @JSFunctor
    private interface FrameCallback extends JSObject{
        void run(double timestamp);
    }

    private final FrameCallback frameCallback = this::onAnimationFrame;
    private final WebGraphics graphics;
    private final WebInput input;
    private final BrowserGL20 gl20;
    private String clipboard = "";

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
        updateGraphicsMetrics();
        Core.graphics = graphics;

        input = new WebInput();
        Core.input = input;
        BrowserInputBridge.install(config.canvasId, input);

        initialize();
        requestAnimationFrame(frameCallback);
    }

    private void onAnimationFrame(double timestamp){
        if(!isRunning()) return;

        BrowserCanvas.resizeToDisplay(config.canvasId);
        updateGraphicsMetrics();
        graphics.updateFrame(timestamp);
        input.update();
        frame();
        input.postUpdate();
        requestAnimationFrame(frameCallback);
    }

    private void updateGraphicsMetrics(){
        graphics.updateSize(
            BrowserCanvas.getClientWidth(config.canvasId),
            BrowserCanvas.getClientHeight(config.canvasId),
            BrowserCanvas.getBackBufferWidth(config.canvasId),
            BrowserCanvas.getBackBufferHeight(config.canvasId),
            BrowserCanvas.getDensity()
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
        return false;
    }

    @Override
    public void exit(){
        super.exit();
        BrowserCanvas.setStatus("stopped", "Mindustry Web runtime stopped");
    }

    @JSBody(params = {"callback"}, script = "window.requestAnimationFrame(callback);")
    private static native void requestAnimationFrame(FrameCallback callback);

    @JSBody(params = {"text"}, script = """
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).catch(() => {});
        }
        """)
    private static native void writeClipboard(String text);
}

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
    private String clipboard = "";

    public BrowserApplication(ApplicationListener listener, WebConfig config){
        super(listener, config);

        if(!BrowserCanvas.initialize(config.canvasId, config.alpha, config.stencil, config.antialiasing,
        config.premultipliedAlpha, config.preserveDrawingBuffer)){
            throw new IllegalStateException("WebGL is not available in this browser.");
        }

        initialize();
        requestAnimationFrame(frameCallback);
    }

    private void onAnimationFrame(double timestamp){
        if(!isRunning()) return;

        BrowserCanvas.resizeToDisplay(config.canvasId);
        frame();
        requestAnimationFrame(frameCallback);
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
        return uri != null && !uri.isEmpty() && openWindow(uri);
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

    @JSBody(params = {"uri"}, script = """
        try {
            const opened = window.open(uri, '_blank', 'noopener,noreferrer');
            return opened !== null;
        } catch (e) {
            return false;
        }
        """)
    private static native boolean openWindow(String uri);
}

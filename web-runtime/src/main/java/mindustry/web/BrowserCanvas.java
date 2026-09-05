package mindustry.web;

import org.teavm.jso.*;
import org.teavm.jso.webgl.*;

/** Minimal DOM/WebGL bridge used while Arc's GL20 adapter is being migrated. */
public final class BrowserCanvas{
    private BrowserCanvas(){}

    @JSBody(params = {"canvasId", "alpha", "stencil", "antialias", "premultipliedAlpha", "preserveDrawingBuffer"}, script = """
        const canvas = document.getElementById(canvasId);
        if (!canvas) throw new Error('Canvas #' + canvasId + ' not found');
        const options = {
            alpha: alpha,
            depth: true,
            stencil: stencil,
            antialias: antialias,
            premultipliedAlpha: premultipliedAlpha,
            preserveDrawingBuffer: preserveDrawingBuffer,
            powerPreference: 'high-performance'
        };
        const gl = canvas.getContext('webgl2', options) || canvas.getContext('webgl', options);
        if (!gl) return false;
        canvas.__mindustryGL = gl;
        canvas.__mindustryGLMajor = (typeof WebGL2RenderingContext !== 'undefined' && gl instanceof WebGL2RenderingContext) ? 2 : 1;
        canvas.__mindustryResizeDirty = true;
        canvas.__mindustryLastDpr = 0;
        const markResizeDirty = () => { canvas.__mindustryResizeDirty = true; };
        if (typeof ResizeObserver !== 'undefined') {
            canvas.__mindustryResizeObserver = new ResizeObserver(markResizeDirty);
            canvas.__mindustryResizeObserver.observe(canvas);
        }
        window.addEventListener('resize', markResizeDirty, {passive: true});
        window.addEventListener('orientationchange', markResizeDirty, {passive: true});
        document.documentElement.dataset.mindustryGl = canvas.__mindustryGLMajor === 2 ? 'webgl2' : 'webgl1';
        return true;
        """)
    public static native boolean initialize(String canvasId, boolean alpha, boolean stencil, boolean antialias,
                                             boolean premultipliedAlpha, boolean preserveDrawingBuffer);

    @JSBody(params = {"canvasId"}, script = "return document.getElementById(canvasId).__mindustryGL;")
    public static native WebGLRenderingContext getContext(String canvasId);

    /**
     * Synchronizes the drawing buffer with CSS size only when ResizeObserver/window
     * resize or devicePixelRatio says metrics changed. This avoids layout reads and
     * redundant gl.viewport calls on every requestAnimationFrame.
     */
    @JSBody(params = {"canvasId"}, script = """
        const canvas = document.getElementById(canvasId);
        const ratio = Math.max(1, window.devicePixelRatio || 1);
        if (!canvas.__mindustryResizeDirty && canvas.__mindustryLastDpr === ratio) return false;

        const clientWidth = Math.max(1, canvas.clientWidth | 0);
        const clientHeight = Math.max(1, canvas.clientHeight | 0);
        const width = Math.max(1, Math.floor(clientWidth * ratio));
        const height = Math.max(1, Math.floor(clientHeight * ratio));
        const bufferChanged = canvas.width !== width || canvas.height !== height;
        const metricsChanged = bufferChanged
            || canvas.__mindustryClientWidth !== clientWidth
            || canvas.__mindustryClientHeight !== clientHeight
            || canvas.__mindustryLastDpr !== ratio;

        if (canvas.width !== width) canvas.width = width;
        if (canvas.height !== height) canvas.height = height;
        if (bufferChanged) {
            const gl = canvas.__mindustryGL;
            if (gl) gl.viewport(0, 0, width, height);
        }

        canvas.__mindustryClientWidth = clientWidth;
        canvas.__mindustryClientHeight = clientHeight;
        canvas.__mindustryBackBufferWidth = width;
        canvas.__mindustryBackBufferHeight = height;
        canvas.__mindustryLastDpr = ratio;
        canvas.__mindustryResizeDirty = false;
        return metricsChanged;
        """)
    public static native boolean resizeToDisplay(String canvasId);

    @JSBody(params = {"canvasId"}, script = "const c=document.getElementById(canvasId); return Math.max(1, (c.__mindustryClientWidth || c.clientWidth) | 0);")
    public static native int getClientWidth(String canvasId);

    @JSBody(params = {"canvasId"}, script = "const c=document.getElementById(canvasId); return Math.max(1, (c.__mindustryClientHeight || c.clientHeight) | 0);")
    public static native int getClientHeight(String canvasId);

    @JSBody(params = {"canvasId"}, script = "const c=document.getElementById(canvasId); return Math.max(1, (c.__mindustryBackBufferWidth || c.width) | 0);")
    public static native int getBackBufferWidth(String canvasId);

    @JSBody(params = {"canvasId"}, script = "const c=document.getElementById(canvasId); return Math.max(1, (c.__mindustryBackBufferHeight || c.height) | 0);")
    public static native int getBackBufferHeight(String canvasId);

    @JSBody(script = "return Math.max(1, window.devicePixelRatio || 1);")
    public static native float getDensity();

    @JSBody(params = {"canvasId"}, script = "return document.getElementById(canvasId).__mindustryGLMajor || 1;")
    public static native int getWebGLMajor(String canvasId);

    @JSBody(params = {"canvasId", "timeSeconds"}, script = """
        const canvas = document.getElementById(canvasId);
        const gl = canvas.__mindustryGL;
        if (!gl) return;
        const pulse = 0.08 + 0.03 * (Math.sin(timeSeconds) * 0.5 + 0.5);
        gl.clearColor(pulse, pulse, pulse + 0.02, 1.0);
        gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
        """)
    public static native void clearSmokeFrame(String canvasId, double timeSeconds);

    public static void setStatus(String state, String text){
        setStatusDom(state, text);
        if("ready".equals(state)){
            BrowserYandex.loadingReady();
        }
    }

    @JSBody(params = {"state", "text"}, script = """
        document.documentElement.dataset.mindustryWeb = state;
        if (state === 'error') {
            const compact = String(text == null ? '' : text).replace(/\s+/g, ' ').slice(0, 800);
            document.documentElement.setAttribute('data-mindustry-error', compact);
        } else {
            document.documentElement.removeAttribute('data-mindustry-error');
        }
        const status = document.getElementById('mindustry-web-status');
        if (status) status.textContent = text;
        """)
    private static native void setStatusDom(String state, String text);
}

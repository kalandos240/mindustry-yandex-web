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
        document.documentElement.dataset.mindustryGl = canvas.__mindustryGLMajor === 2 ? 'webgl2' : 'webgl1';
        return true;
        """)
    public static native boolean initialize(String canvasId, boolean alpha, boolean stencil, boolean antialias,
                                             boolean premultipliedAlpha, boolean preserveDrawingBuffer);

    @JSBody(params = {"canvasId"}, script = "return document.getElementById(canvasId).__mindustryGL;")
    public static native WebGLRenderingContext getContext(String canvasId);

    @JSBody(params = {"canvasId"}, script = """
        const canvas = document.getElementById(canvasId);
        const ratio = Math.max(1, window.devicePixelRatio || 1);
        const width = Math.max(1, Math.floor(canvas.clientWidth * ratio));
        const height = Math.max(1, Math.floor(canvas.clientHeight * ratio));
        if (canvas.width !== width) canvas.width = width;
        if (canvas.height !== height) canvas.height = height;
        const gl = canvas.__mindustryGL;
        if (gl) gl.viewport(0, 0, width, height);
        """)
    public static native void resizeToDisplay(String canvasId);

    @JSBody(params = {"canvasId"}, script = "return Math.max(1, document.getElementById(canvasId).clientWidth | 0);")
    public static native int getClientWidth(String canvasId);

    @JSBody(params = {"canvasId"}, script = "return Math.max(1, document.getElementById(canvasId).clientHeight | 0);")
    public static native int getClientHeight(String canvasId);

    @JSBody(params = {"canvasId"}, script = "return Math.max(1, document.getElementById(canvasId).width | 0);")
    public static native int getBackBufferWidth(String canvasId);

    @JSBody(params = {"canvasId"}, script = "return Math.max(1, document.getElementById(canvasId).height | 0);")
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

    @JSBody(params = {"state", "text"}, script = """
        document.documentElement.dataset.mindustryWeb = state;
        const status = document.getElementById('mindustry-web-status');
        if (status) status.textContent = text;
        """)
    public static native void setStatus(String state, String text);
}

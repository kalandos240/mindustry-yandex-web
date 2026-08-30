package arc.backend.web;

import arc.*;
import arc.Graphics.Cursor;
import arc.Graphics.Cursor.SystemCursor;
import arc.graphics.*;
import arc.graphics.gl.*;

/** Browser implementation of Arc's graphics metadata and frame timing. */
public class WebGraphics extends Graphics{
    private GL20 gl20;
    private GL30 gl30;
    private GLVersion glVersion;

    private int width = 1;
    private int height = 1;
    private int backBufferWidth = 1;
    private int backBufferHeight = 1;
    private float density = 1f;

    private long frameId = -1;
    private double lastTimestampMs = -1d;
    private float deltaTime = 1f / 60f;
    private int fps = 60;
    private int fpsFrames;
    private double fpsWindowStartMs = -1d;
    private boolean continuous = true;
    private boolean fullscreen;

    private final BufferFormat bufferFormat;

    public WebGraphics(WebConfig config){
        bufferFormat = new BufferFormat(8, 8, 8, config.alpha ? 8 : 0, 24, config.stencil ? 8 : 0,
        config.antialiasing ? 4 : 0, false);
        setWebGLVersion(2);
    }

    /** Updated by the browser backend after the canvas backing store has been resized. */
    public void updateSize(int width, int height, int backBufferWidth, int backBufferHeight, float density){
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.backBufferWidth = Math.max(1, backBufferWidth);
        this.backBufferHeight = Math.max(1, backBufferHeight);
        this.density = Math.max(1f, density);
    }

    /** Updated once for every requestAnimationFrame callback. */
    public void updateFrame(double timestampMs){
        frameId++;

        if(lastTimestampMs >= 0d){
            double elapsed = Math.max(0d, Math.min(250d, timestampMs - lastTimestampMs));
            deltaTime = (float)(elapsed / 1000d);
        }
        lastTimestampMs = timestampMs;

        if(fpsWindowStartMs < 0d) fpsWindowStartMs = timestampMs;
        fpsFrames++;
        double fpsElapsed = timestampMs - fpsWindowStartMs;
        if(fpsElapsed >= 1000d){
            fps = Math.max(1, (int)Math.round(fpsFrames * 1000d / fpsElapsed));
            fpsFrames = 0;
            fpsWindowStartMs = timestampMs;
        }
    }

    public void setWebGLVersion(int major){
        glVersion = new GLVersion(Application.ApplicationType.web,
        major >= 2 ? "WebGL 2.0" : "WebGL 1.0", "Browser", "WebGL");
    }

    @Override
    public boolean supportsInstancing(){
        return gl30 != null;
    }

    @Override
    public GL20 getGL20(){
        return gl20;
    }

    @Override
    public void setGL20(GL20 gl20){
        this.gl20 = gl20;
        Core.gl = Core.gl20 = gl20;
    }

    @Override
    public GL30 getGL30(){
        return gl30;
    }

    @Override
    public void setGL30(GL30 gl30){
        this.gl30 = gl30;
        Core.gl30 = gl30;
        if(gl30 != null) setGL20(gl30);
    }

    @Override
    public int getWidth(){
        return width;
    }

    @Override
    public int getHeight(){
        return height;
    }

    @Override
    public int getBackBufferWidth(){
        return backBufferWidth;
    }

    @Override
    public int getBackBufferHeight(){
        return backBufferHeight;
    }

    @Override
    public long getFrameId(){
        return frameId;
    }

    @Override
    public float getDeltaTime(){
        return deltaTime;
    }

    @Override
    public int getFramesPerSecond(){
        return fps;
    }

    @Override
    public GLVersion getGLVersion(){
        return glVersion;
    }

    @Override
    public float getPpiX(){
        return 96f * density;
    }

    @Override
    public float getPpiY(){
        return 96f * density;
    }

    @Override
    public float getPpcX(){
        return getPpiX() / 2.54f;
    }

    @Override
    public float getPpcY(){
        return getPpiY() / 2.54f;
    }

    @Override
    public float getDensity(){
        return density;
    }

    @Override
    public void setTitle(String title){
        // Applied by the TeaVM/DOM layer. Kept here to satisfy the platform abstraction.
    }

    @Override
    public void setVSync(boolean vsync){
        // Browser presentation is synchronized by requestAnimationFrame.
    }

    @Override
    public BufferFormat getBufferFormat(){
        return bufferFormat;
    }

    @Override
    public boolean supportsExtension(String extension){
        // The concrete WebGL bridge will provide extension discovery once GL20 is installed.
        return false;
    }

    @Override
    public boolean isContinuousRendering(){
        return continuous;
    }

    @Override
    public void setContinuousRendering(boolean continuous){
        this.continuous = continuous;
    }

    @Override
    public void requestRendering(){
        // requestAnimationFrame scheduling is owned by the browser Application implementation.
    }

    @Override
    public boolean isFullscreen(){
        return fullscreen;
    }

    public void setFullscreenState(boolean fullscreen){
        this.fullscreen = fullscreen;
    }

    @Override
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot){
        return null;
    }

    @Override
    protected void setCursor(Cursor cursor){
        // Implemented by the DOM cursor bridge in a later M1 step.
    }

    @Override
    protected void setSystemCursor(SystemCursor systemCursor){
        // Implemented by the DOM cursor bridge in a later M1 step.
    }
}

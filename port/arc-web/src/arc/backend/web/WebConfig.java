package arc.backend.web;

import arc.graphics.*;

/** Configuration shared by browser implementations of Arc. */
public class WebConfig{
    /** Logical canvas width used before the first browser resize event. */
    public int width = 1280;
    /** Logical canvas height used before the first browser resize event. */
    public int height = 720;

    /** DOM id of the canvas that owns the Arc rendering context. */
    public String canvasId = "mindustry-canvas";

    public boolean disableAudio = false;
    public boolean stencil = false;
    public boolean antialiasing = false;
    public boolean alpha = false;
    public boolean premultipliedAlpha = false;
    public boolean preserveDrawingBuffer = false;
    public boolean debugGl = false;

    /**
     * Maximum backing-buffer scale relative to CSS pixels. Modern phones commonly
     * report DPR 3-4, which turns a full-screen canvas into 9-16x as many pixels.
     * 2x remains visually sharp while putting a predictable ceiling on fill-rate,
     * framebuffer memory and post-processing cost for the Yandex mobile target.
     */
    public float maxPixelRatio = 2f;

    /** Pause game updates when the document becomes hidden. */
    public boolean pauseWhenHidden = true;

    public Color initialBackgroundColor = Color.black;
}

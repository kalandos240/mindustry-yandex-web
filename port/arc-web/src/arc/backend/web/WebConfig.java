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

    /** Pause game updates when the document becomes hidden. */
    public boolean pauseWhenHidden = true;

    public Color initialBackgroundColor = Color.black;
}

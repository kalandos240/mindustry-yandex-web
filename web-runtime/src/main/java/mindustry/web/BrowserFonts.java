package mindustry.web;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

/** Browser-safe Mindustry font bridge backed only by packaged BMFont/PNG assets. */
public final class BrowserFonts{
    private static final String defaultFnt = "webfonts/default.fnt";
    private static Font defaultFont;

    private BrowserFonts(){}

    public static Font loadDefault(){
        if(defaultFont != null) return defaultFont;

        Fi definition = Core.files.internal(defaultFnt);
        if(!definition.exists() || definition.length() < 512){
            throw new IllegalStateException("Packaged browser BMFont is missing or empty: " + defaultFnt);
        }

        Font font = new Font(definition, false);
        if(font.getData().getGlyph('M') == null || font.getData().getGlyph('\u0416') == null){
            font.dispose();
            throw new IllegalStateException("Browser BMFont is missing required Latin/Cyrillic glyphs");
        }
        if(font.getRegions().isEmpty()){
            font.dispose();
            throw new IllegalStateException("Browser BMFont did not load any texture pages");
        }
        for(TextureRegion region : font.getRegions()){
            if(region == null || region.texture == null || region.texture.getTextureObjectHandle() == 0){
                font.dispose();
                throw new IllegalStateException("Browser BMFont texture page did not upload to WebGL");
            }
        }

        font.getData().markupEnabled = true;
        Fonts.def = font;
        defaultFont = font;
        return font;
    }

    /** Proves the non-FreeType font path reaches the same real WebGL VBO SpriteBatch. */
    public static void loadAndVerifyRendering(){
        Font font = loadDefault();
        int width = Core.graphics.getWidth();
        int height = Core.graphics.getHeight();
        if(width <= 0 || height <= 0){
            throw new IllegalStateException("Invalid framebuffer while verifying browser font: " + width + "x" + height);
        }

        Draw.proj(0f, 0f, width, height);
        Draw.color();
        long before = SpriteBatch.totalDrawCalls;
        font.setColor(Color.white);
        font.draw("Mindustry Web / \u041c\u0438\u043d\u0434\u0430\u0441\u0442\u0440\u0438", 24f, Math.max(48f, height - 24f));
        Draw.flush();

        if(SpriteBatch.totalDrawCalls <= before){
            throw new IllegalStateException("Arc Font did not submit glyph vertices through SpriteBatch");
        }
        int error = Core.gl20.glGetError();
        if(error != GL20.GL_NO_ERROR){
            throw new IllegalStateException("Browser BMFont render failed with GL error 0x" + Integer.toHexString(error));
        }

        markReady();
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-font', 'ready');")
    private static native void markReady();
}

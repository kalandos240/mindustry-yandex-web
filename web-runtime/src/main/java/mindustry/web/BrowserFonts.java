package mindustry.web;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.gen.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

/** Browser-safe Mindustry font bridge backed only by packaged BMFont/PNG assets. */
public final class BrowserFonts{
    private static boolean loaded;

    private BrowserFonts(){}

    public static void loadAll(){
        if(loaded) return;

        Fonts.def = load("default");
        Fonts.outline = load("outline");
        Fonts.monospace = load("monospace");
        Fonts.icon = load("icon");
        Fonts.iconLarge = load("iconLarge");
        Fonts.tech = load("tech");
        Fonts.logic = load("logic");

        requireGlyph(Fonts.def, 'M', "default Latin");
        requireGlyph(Fonts.def, '\u0416', "default Cyrillic");
        requireGlyph(Fonts.outline, '\u0416', "outline Cyrillic");
        requireGlyph(Fonts.monospace, '\u0416', "monospace Cyrillic");
        requireGlyph(Fonts.tech, 'M', "tech Latin");
        requireGlyph(Fonts.logic, 'A', "logic ASCII");
        requireGlyph(Fonts.logic, '0', "logic digits");
        if(Iconc.all == null || Iconc.all.isEmpty()){
            throw new IllegalStateException("Mindustry generated icon character table is empty");
        }
        requireGlyph(Fonts.icon, Iconc.all.charAt(0), "icon");
        requireGlyph(Fonts.iconLarge, Iconc.all.charAt(0), "iconLarge");

        Fonts.def.getData().markupEnabled = true;
        Fonts.outline.getData().markupEnabled = true;
        Fonts.tech.getData().down *= 1.5f;
        loaded = true;
    }

    private static Font load(String name){
        String path = "webfonts/" + name + ".fnt";
        Fi definition = Core.files.internal(path);
        if(!definition.exists() || definition.length() < 128){
            throw new IllegalStateException("Packaged browser BMFont is missing or empty: " + path);
        }

        Font font = new Font(definition, false);
        if(font.getRegions().isEmpty()){
            font.dispose();
            throw new IllegalStateException("Browser BMFont did not load texture pages: " + name);
        }
        for(TextureRegion region : font.getRegions()){
            if(region == null || region.texture == null || region.texture.getTextureObjectHandle() == 0){
                font.dispose();
                throw new IllegalStateException("Browser BMFont texture page did not upload to WebGL: " + name);
            }
        }
        return font;
    }

    private static void requireGlyph(Font font, char glyph, String label){
        if(font.getData().getGlyph(glyph) == null){
            throw new IllegalStateException("Browser BMFont is missing required glyph: " + label + " / " + (int)glyph);
        }
    }

    /** Proves text and icon fonts reach the same real WebGL VBO SpriteBatch. */
    public static void loadAndVerifyRendering(){
        loadAll();
        int width = Core.graphics.getWidth();
        int height = Core.graphics.getHeight();
        if(width <= 0 || height <= 0){
            throw new IllegalStateException("Invalid framebuffer while verifying browser fonts: " + width + "x" + height);
        }

        Draw.proj(0f, 0f, width, height);
        Draw.color();
        long before = SpriteBatch.totalDrawCalls;
        Fonts.def.setColor(Color.white);
        Fonts.def.draw("Mindustry Web / \u041c\u0438\u043d\u0434\u0430\u0441\u0442\u0440\u0438", 24f, Math.max(64f, height - 24f));
        Fonts.icon.setColor(Color.white);
        Fonts.icon.draw(String.valueOf(Iconc.all.charAt(0)), 24f, Math.max(32f, height - 56f));
        Draw.flush();

        if(SpriteBatch.totalDrawCalls <= before){
            throw new IllegalStateException("Arc fonts did not submit glyph vertices through SpriteBatch");
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

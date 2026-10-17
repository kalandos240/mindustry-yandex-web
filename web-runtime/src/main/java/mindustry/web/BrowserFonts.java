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
        for(int i = 0; i < Iconc.all.length(); i++){
            char glyph = Iconc.all.charAt(i);
            requireGlyph(Fonts.def, glyph, "small UI icon");
            requireGlyph(Fonts.outline, glyph, "outline UI icon");
            requireGlyph(Fonts.icon, glyph, "icon");
            requireGlyph(Fonts.iconLarge, glyph, "iconLarge");
        }

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

    /** Verify every registered content emoji uses its original atlas pixels. */
    public static void verifyContentIconPages(){
        int count = 0;
        Font[] fonts = {Fonts.def, Fonts.outline};
        for(String line : Core.files.internal("icons/icons.properties").readString().split("\n")){
            if(line.trim().isEmpty()) continue;
            String[] pair = line.split("=");
            int code = Integer.parseInt(pair[0]);
            String[] names = pair[1].trim().split("\\|");
            if(Fonts.getUnicode(names[0]) != code) continue;
            TextureRegion original = Core.atlas.find(names[1]);
            for(Font font : fonts){
                Font.Glyph glyph = font.getData().getGlyph((char)code);
                if(glyph == null || glyph.page < 0 || glyph.page >= font.getRegions().size
                    || font.getRegions().get(glyph.page).texture != original.texture
                    || glyph.u != original.u || glyph.u2 != original.u2
                    || glyph.v != original.v2 || glyph.v2 != original.v){
                    throw new IllegalStateException("Inline content icon lost original atlas page: " + names[0]);
                }
            }
            count++;
        }
        if(count < 2 || Fonts.getUnicode("copper") == 0 || Fonts.getUnicode("lead") == 0){
            throw new IllegalStateException("Original inline resource icons are absent");
        }
        // Exercise caches created with one baked page growing to mixed text
        // and atlas glyphs; the native FontCache must submit both textures.
        Draw.flush();
        for(int i = 0; i < 8 && Core.gl20.glGetError() != GL20.GL_NO_ERROR; i++){}
        Draw.proj(0f, 0f, Core.graphics.getWidth(), Core.graphics.getHeight());
        Draw.color();
        String sample = "Cu " + Fonts.getUnicodeStr("copper") + " Pb " + Fonts.getUnicodeStr("lead");
        Fonts.def.setColor(Color.white);
        Fonts.outline.setColor(Color.white);
        Fonts.def.draw(sample, 24f, Core.graphics.getHeight() - 24f);
        Fonts.outline.draw(sample, 24f, Core.graphics.getHeight() - 52f);
        Draw.flush();
        int error = Core.gl20.glGetError();
        if(error != GL20.GL_NO_ERROR){
            throw new IllegalStateException("Inline content font/atlas render failed: " + error);
        }
        markContentIconsReady(count);
    }

    @JSBody(params={"count"},script="const r=document.documentElement;r.setAttribute('data-mindustry-content-icons','atlas-pages-verified');r.setAttribute('data-mindustry-content-icon-count',String(count));")
    private static native void markContentIconsReady(int count);

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

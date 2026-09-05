package mindustry.web;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.ui.*;

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

    public static void drawSmoke(float x, float y){
        Font font = loadDefault();
        font.setColor(Color.white);
        font.draw("Mindustry Web / \u041c\u0438\u043d\u0434\u0430\u0441\u0442\u0440\u0438", x, y);
    }
}

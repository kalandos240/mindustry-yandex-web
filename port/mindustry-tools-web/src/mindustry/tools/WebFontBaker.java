package mindustry.tools;

import arc.files.*;
import arc.freetype.*;
import arc.freetype.FreeTypeFontGenerator.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.Font.*;
import arc.graphics.g2d.PixmapPacker.*;
import arc.util.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

/**
 * Produces a browser-safe BMFont from Mindustry's vanilla WOFF using Arc FreeType
 * at build time. No FreeType/JNI code is needed by the TeaVM runtime.
 */
public final class WebFontBaker{
    private static final int pageSize = 2048;

    private WebFontBaker(){}

    public static void main(String[] args) throws Exception{
        Fi root = Fi.get("core/assets");
        Fi source = root.child("fonts/font.woff");
        Fi output = root.child("webfonts");
        output.deleteDirectory();
        output.mkdirs();

        String characters = collectCharacters(root.child("bundles"));
        Log.info("[WebFont] Baking @ unique characters from @", characters.length(), source);

        PixmapPacker packer = new PixmapPacker(pageSize, pageSize, 2, false, new SkylineStrategy());
        packer.setAllowMultiplePages(true);

        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = 18;
        parameter.incremental = false;
        parameter.characters = characters;
        parameter.shadowColor = Color.darkGray;
        parameter.shadowOffsetY = 2;
        parameter.packer = packer;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(source);
        FreeTypeFontData data;
        try{
            data = generator.generateData(parameter);
        }finally{
            generator.dispose();
        }

        if(packer.getPages().isEmpty()) throw new IllegalStateException("Web font baker produced no texture pages");

        for(int i = 0; i < packer.getPages().size; i++){
            Fi page = output.child("default-" + i + ".png");
            PixmapIO.writePng(page, packer.getPages().get(i).getPixmap());
            if(page.length() <= 0) throw new IllegalStateException("Empty Web font page: " + page);
        }

        writeBmFont(output.child("default.fnt"), data, characters, packer.getPages().size);
        output.child("characters.txt").writeString(characters, false, "UTF-8");

        Log.info("[WebFont] Wrote @ pages; fnt=@ bytes; chars=@", packer.getPages().size,
            output.child("default.fnt").length(), characters.length());
        packer.dispose();
    }

    private static String collectCharacters(Fi bundles) throws IOException{
        LinkedHashSet<Character> chars = new LinkedHashSet<>();
        add(chars, FreeTypeFontGenerator.DEFAULT_CHARS);
        chars.add('\0');

        // English + Russian are the first browser release targets. Reading values
        // through java.util.Properties resolves escaped Unicode code points first.
        String[] names = {"bundle.properties", "bundle_ru.properties"};
        for(String name : names){
            Fi file = bundles.child(name);
            if(!file.exists()) continue;
            Properties properties = new Properties();
            try(Reader reader = new InputStreamReader(file.read(), StandardCharsets.UTF_8)){
                properties.load(reader);
            }
            for(Object value : properties.values()) add(chars, String.valueOf(value));
        }

        // Keep the full Cyrillic block available for generated/player-facing UI text.
        for(char c = '\u0400'; c <= '\u052f'; c++) chars.add(c);

        StringBuilder result = new StringBuilder(chars.size());
        for(char c : chars){
            if(!Character.isSurrogate(c)) result.append(c);
        }
        return result.toString();
    }

    private static void add(Set<Character> out, String text){
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            if(!Character.isSurrogate(c)) out.add(c);
        }
    }

    private static void writeBmFont(Fi file, FreeTypeFontData data, String characters, int pages){
        StringBuilder out = new StringBuilder(256 * 1024);
        int base = Math.round(data.ascent + data.capHeight);
        int lineHeight = Math.max(1, Math.round(data.lineHeight));

        out.append("info face=\"Mindustry Web\" size=18 bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=1 padding=")
            .append((int)data.padTop).append(',').append((int)data.padRight).append(',')
            .append((int)data.padBottom).append(',').append((int)data.padLeft)
            .append(" spacing=0,0\n");
        out.append("common lineHeight=").append(lineHeight)
            .append(" base=").append(base)
            .append(" scaleW=").append(pageSize)
            .append(" scaleH=").append(pageSize)
            .append(" pages=").append(pages)
            .append(" packed=0\n");
        for(int i = 0; i < pages; i++){
            out.append("page id=").append(i).append(" file=\"default-").append(i).append(".png\"\n");
        }

        LinkedHashMap<Integer, Glyph> glyphs = new LinkedHashMap<>();
        Glyph missing = data.missingGlyph;
        if(missing != null) glyphs.put(0, missing);
        for(int i = 0; i < characters.length(); i++){
            char c = characters.charAt(i);
            Glyph glyph = data.getGlyph(c);
            if(glyph != null) glyphs.putIfAbsent((int)c, glyph);
        }

        out.append("chars count=").append(glyphs.size()).append('\n');
        for(Map.Entry<Integer, Glyph> entry : glyphs.entrySet()){
            int id = entry.getKey();
            Glyph g = entry.getValue();
            int bmYoffset = -g.yoffset - g.height;
            out.append("char id=").append(id)
                .append(" x=").append(g.srcX)
                .append(" y=").append(g.srcY)
                .append(" width=").append(g.width)
                .append(" height=").append(g.height)
                .append(" xoffset=").append(g.xoffset)
                .append(" yoffset=").append(bmYoffset)
                .append(" xadvance=").append(g.xadvance)
                .append(" page=").append(g.page)
                .append(" chnl=15\n");
        }
        out.append("kernings count=0\n");
        file.writeString(out.toString(), false, "UTF-8");
    }
}

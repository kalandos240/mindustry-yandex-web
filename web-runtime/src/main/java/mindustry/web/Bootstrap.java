package mindustry.web;

import arc.*;
import arc.backend.web.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.world.blocks.storage.*;

/** First executable bridge between current Mindustry/Arc bytecode and a browser frame loop. */
public final class Bootstrap{
    private static final String settingsKey = "mindustry.web.settings.v1";
    private static final String smokeBundle = "bundles/bundle.properties";
    private static final String serpuloData = "planets/serpulo.json";
    private static final String smokePng = "sprites/error.png";

    private Bootstrap(){}

    public static void main(String[] args){
        try{
            installAndVerifyFiles();
            installAndVerifySettings();
        }catch(Throwable error){
            BrowserCanvas.setStatus("error", "Mindustry Web bootstrap failed: " + describe(error));
            throw error;
        }

        WebConfig config = new WebConfig();

        new BrowserApplication(new ApplicationListener(){
            private final WebClientLauncher launcher = new WebClientLauncher();
            private int frames;

            @Override
            public void init(){
                // BrowserApplication installs Core.app/graphics/gl/input before listener init.
                // Prove the packaged binary asset can travel all the way through Arc's Texture
                // path and the Web GL20 adapter before the wider Mindustry client setup starts.
                try{
                    verifyTextureUpload();
                }catch(Throwable error){
                    BrowserCanvas.setStatus("error", "Mindustry Web texture upload failed: " + describe(error));
                    throw error;
                }

                // Execute the real browser-specific Mindustry startup.
                try{
                    launcher.setup();
                }catch(Throwable error){
                    BrowserCanvas.setStatus("error", "Mindustry Web startup failed: " + describe(error));
                    throw error;
                }

                // Load the exact generated vanilla atlas through Arc AssetManager and
                // TextureAtlasLoader. This exercises binary AATLS parsing, dependency
                // resolution, every atlas PNG page and the WebGL texture upload path.
                try{
                    AtlasSmoke.loadAndVerify();
                }catch(Throwable error){
                    BrowserCanvas.setStatus("error", "Mindustry Web vanilla atlas failed: " + describe(error));
                    throw error;
                }

                // Complete the client-side content lifecycle only after Core.atlas is the
                // real vanilla atlas. loadColors() proves packaged block metadata is readable;
                // load() resolves actual UI/full icons and every content-specific atlas region.
                try{
                    Vars.content.loadColors();
                    Vars.content.load();
                    verifyContentRegions();
                }catch(Throwable error){
                    BrowserCanvas.setStatus("error", "Mindustry Web content regions failed: " + describe(error));
                    throw error;
                }

                if(Vars.content == null
                || Vars.content.item("copper") == null
                || Vars.content.liquid("water") == null
                || Vars.content.statusEffect("burning") == null
                || Vars.content.unit("dagger") == null
                || Vars.content.block("copper-wall") == null
                || Vars.content.block("core-shard") == null
                || Vars.content.block("duo") == null){
                    throw new IllegalStateException("Mindustry browser gameplay registries failed runtime initialization");
                }

                // Registration alone is insufficient: Block.initBuilding must preserve
                // specialized Building factories on Web rather than silently falling back
                // to Building::create when reflection metadata is missing.
                Object coreBuild = Vars.content.block("core-shard").buildType.get();
                if(!(coreBuild instanceof CoreBlock.CoreBuild)){
                    throw new IllegalStateException("Mindustry browser block factory lost CoreBlock.CoreBuild specialization");
                }

                // ContentLoader.init() must complete the logical campaign/content phase,
                // including packaged Serpulo metadata, sector remapping and tech-tree binding.
                if(Planets.serpulo == null
                || Planets.erekir == null
                || Planets.serpulo.data == null
                || Planets.serpulo.data.attackSectors.length == 0
                || SectorPresets.groundZero == null
                || SectorPresets.groundZero.sector == null
                || SectorPresets.groundZero.sector.planet != Planets.serpulo
                || Planets.serpulo.techTree == null
                || Planets.erekir.techTree == null
                || TechTree.roots.size < 2){
                    throw new IllegalStateException("Mindustry browser content.init campaign/tech-tree state failed runtime initialization");
                }

                BrowserCanvas.setStatus("initialized", "Mindustry clientSetup initialized; vanilla content.init/load, campaign metadata, tech trees, specialized block factories and real content regions ready; binary-png@Core.files; texture-upload@WebGL; vanilla-atlas@WebGL; content-regions@atlas; settings@localStorage; waiting for animation frames...");
            }

            @Override
            public void update(){
                double seconds = System.currentTimeMillis() / 1000.0;
                float pulse = 0.08f + 0.03f * (float)(Math.sin(seconds) * 0.5 + 0.5);
                Core.graphics.clear(pulse, pulse, pulse + 0.02f, 1f);

                if(++frames == 3){
                    String glVersion = Core.gl20.glGetString(GL20.GL_VERSION);
                    BrowserCanvas.setStatus("ready", "Mindustry core " + Version.buildString() + " + vanilla content.init/load + Arc GL20 ready; campaign metadata/tech-tree/block-factory runtime verified; binary-png@Core.files; texture-upload@WebGL; vanilla-atlas@WebGL; content-regions@atlas; settings@localStorage: " + glVersion);
                }
            }
        }, config);
    }

    private static void installAndVerifyFiles(){
        BrowserFiles files = new BrowserFiles("assets");
        files.preloadText(smokeBundle);
        files.preloadText(serpuloData);
        files.preloadBinary(smokePng);
        Core.files = files;

        String bundle = Core.files.internal(smokeBundle).readString();
        String planet = Core.files.internal(serpuloData).readString();
        byte[] png = Core.files.internal(smokePng).readBytes();
        if(bundle.length() < 1000
        || !bundle.contains("credits = Credits")
        || !bundle.contains("gameover = Game Over")
        || Core.files.internal(smokeBundle).length() < 1000
        || !planet.contains("attackSectors")
        || !planet.contains("groundZero")
        || png.length < 8
        || (png[0] & 0xff) != 0x89
        || (png[1] & 0xff) != 0x50
        || (png[2] & 0xff) != 0x4e
        || (png[3] & 0xff) != 0x47
        || Core.files.internal(smokePng).length() != png.length){
            throw new IllegalStateException("Mindustry packaged text/binary asset failed Core.files/Fi round-trip");
        }

        // Prove the complete binary path in TeaVM/Chrome: async browser preload ->
        // byte[] -> BrowserFi -> Arc's pure-Java PNG reader -> direct-buffer Pixmap.
        Pixmap pixmap = new Pixmap(Core.files.internal(smokePng));
        try{
            if(pixmap.width <= 0 || pixmap.height <= 0 || pixmap.getPixels().capacity() != pixmap.width * pixmap.height * 4){
                throw new IllegalStateException("Mindustry packaged PNG failed Arc Pixmap decode");
            }
        }finally{
            pixmap.dispose();
        }
    }

    private static void verifyTextureUpload(){
        // Discard any context-creation noise so the error checked below belongs to
        // this upload path rather than browser/driver initialization.
        for(int i = 0; i < 8 && Core.gl20.glGetError() != GL20.GL_NO_ERROR; i++){
            // drain
        }

        Texture texture = new Texture(Core.files.internal(smokePng));
        try{
            if(texture.width <= 0 || texture.height <= 0 || texture.getTextureObjectHandle() == 0){
                throw new IllegalStateException("Arc Texture did not create a valid WebGL texture object");
            }

            int error = Core.gl20.glGetError();
            if(error != GL20.GL_NO_ERROR){
                throw new IllegalStateException("WebGL texture upload failed with GL error 0x" + Integer.toHexString(error));
            }
        }finally{
            texture.dispose();
        }
    }

    private static void verifyContentRegions(){
        requireAtlasRegion("dagger.fullIcon", Vars.content.unit("dagger").fullIcon);
        requireAtlasRegion("copper.uiIcon", Vars.content.item("copper").uiIcon);
        requireAtlasRegion("water.uiIcon", Vars.content.liquid("water").uiIcon);
        requireAtlasRegion("duo.uiIcon", Vars.content.block("duo").uiIcon);
        requireAtlasRegion("core-shard.uiIcon", Vars.content.block("core-shard").uiIcon);
    }

    private static void requireAtlasRegion(String name, TextureRegion region){
        if(region == null
        || !region.found()
        || region.texture == null
        || region.width <= 0
        || region.height <= 0
        || region.texture.getTextureObjectHandle() == 0){
            throw new IllegalStateException("Mindustry content region is not backed by a live vanilla atlas texture: " + name);
        }
    }

    private static void installAndVerifySettings(){
        BrowserSettings settings = new BrowserSettings(settingsKey);
        settings.load();
        settings.put("web.smoke.bool", true);
        settings.put("web.smoke.int", 42);
        settings.put("web.smoke.long", 9007199254740993L);
        settings.put("web.smoke.float", 1.25f);
        settings.put("web.smoke.string", "Mindustry:Web|settings");
        settings.put("web.smoke.binary", new byte[]{0, 1, 15, 16, 127, -1});
        settings.forceSave();

        BrowserSettings verify = new BrowserSettings(settingsKey);
        verify.load();
        if(!verify.getBool("web.smoke.bool", false)
        || verify.getInt("web.smoke.int", 0) != 42
        || verify.getLong("web.smoke.long", 0L) != 9007199254740993L
        || verify.getFloat("web.smoke.float", 0f) != 1.25f
        || !"Mindustry:Web|settings".equals(verify.getString("web.smoke.string", ""))
        || !binaryMatches(verify.getBytes("web.smoke.binary", new byte[0]))){
            throw new IllegalStateException("Browser settings localStorage round-trip failed");
        }

        Core.settings = settings;
    }

    private static String describe(Throwable error){
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while(current != null && depth++ < 6){
            if(out.length() > 0) out.append(" <- ");
            out.append(current.getClass().getName()).append(": ").append(String.valueOf(current.getMessage()));
            current = current.getCause();
        }
        return out.toString();
    }

    private static boolean binaryMatches(byte[] value){
        byte[] expected = {0, 1, 15, 16, 127, -1};
        if(value.length != expected.length) return false;
        for(int i = 0; i < expected.length; i++){
            if(value[i] != expected[i]) return false;
        }
        return true;
    }
}

package mindustry.web;

import arc.*;
import arc.backend.web.*;
import arc.graphics.*;
import mindustry.*;
import mindustry.core.*;

/** First executable bridge between current Mindustry/Arc bytecode and a browser frame loop. */
public final class Bootstrap{
    private static final String settingsKey = "mindustry.web.settings.v1";
    private static final String smokeBundle = "bundles/bundle.properties";

    private Bootstrap(){}

    public static void main(String[] args){
        installAndVerifyFiles();
        installAndVerifySettings();

        WebConfig config = new WebConfig();

        new BrowserApplication(new ApplicationListener(){
            private final WebClientLauncher launcher = new WebClientLauncher();
            private int frames;

            @Override
            public void init(){
                // BrowserApplication installs Core.app/graphics/gl/input before listener init,
                // so this is the first test that executes the real browser-specific Mindustry
                // startup rather than merely making it reachable to TeaVM.
                launcher.setup();

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

                BrowserCanvas.setStatus("initialized", "Mindustry clientSetup initialized; item/liquid/status/unit/block registries ready; assets@Core.files; settings@localStorage; waiting for animation frames...");
            }

            @Override
            public void update(){
                double seconds = System.currentTimeMillis() / 1000.0;
                float pulse = 0.08f + 0.03f * (float)(Math.sin(seconds) * 0.5 + 0.5);
                Core.graphics.clear(pulse, pulse, pulse + 0.02f, 1f);

                if(++frames == 3){
                    String glVersion = Core.gl20.glGetString(GL20.GL_VERSION);
                    BrowserCanvas.setStatus("ready", "Mindustry core " + Version.buildString() + " + clientSetup + Arc GL20 ready; content=copper,water,burning,dagger,copper-wall,core-shard,duo; assets@Core.files; settings@localStorage: " + glVersion);
                }
            }
        }, config);
    }

    private static void installAndVerifyFiles(){
        BrowserFiles files = new BrowserFiles("assets");
        files.preloadText(smokeBundle);
        Core.files = files;

        String bundle = Core.files.internal(smokeBundle).readString();
        if(bundle.length() < 1000
        || !bundle.contains("credits = Credits")
        || !bundle.contains("gameover = Game Over")
        || Core.files.internal(smokeBundle).length() < 1000){
            throw new IllegalStateException("Mindustry packaged asset failed Core.files/Fi round-trip");
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

    private static boolean binaryMatches(byte[] value){
        byte[] expected = {0, 1, 15, 16, 127, -1};
        if(value.length != expected.length) return false;
        for(int i = 0; i < expected.length; i++){
            if(value[i] != expected[i]) return false;
        }
        return true;
    }
}

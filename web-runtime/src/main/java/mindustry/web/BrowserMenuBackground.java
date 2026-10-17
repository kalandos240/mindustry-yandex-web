package mindustry.web;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.ui.layout.Scl;
import arc.util.*;
import mindustry.core.*;
import mindustry.graphics.MenuRenderer;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/** The pinned game's actual menu map, shadow cache, flying units and logo. */
public final class BrowserMenuBackground{
    private static MenuRenderer renderer;
    private static int frames;

    private BrowserMenuBackground(){}

    public static void install(Group parent){
        if(renderer != null) return;
        // MenuRenderer.generate() uses Vars.world while building its immutable
        // caches. Keep that temporary scenery out of the live campaign world.
        World previous = world;
        try{
            world = new World();
            renderer = new MenuRenderer();
        }finally{
            world = previous;
        }
        Element background = parent.fill((x, y, w, h) -> {
            renderer.render();
            if(++frames == 1 || (frames & 63) == 0) markRendered(frames);
        });
        background.toBack();
        parent.fill((x, y, w, h) -> drawLogo());
        Core.app.addListener(new ApplicationListener(){
            @Override
            public void dispose(){
                renderer.dispose();
            }
        });
    }

    // Same placement, scale and version styling as MenuFragment.build().
    private static void drawLogo(){
        TextureRegion logo = Core.atlas.find("logo");
        float width = Core.graphics.getWidth(), height = Core.graphics.getHeight() - Core.scene.marginTop;
        float logoscl = Scl.scl(1) * logo.scale;
        float logow = Math.min(logo.width * logoscl, Core.graphics.getWidth() - Scl.scl(20));
        float logoh = logow * (float)logo.height / logo.width;
        float fx = (int)(width / 2f);
        float fy = (int)(height - 6 - logoh) + logoh / 2 - (Core.graphics.isPortrait() ? Scl.scl(30f) : 0f);
        if(Core.settings.getBool("macnotch")) fy -= Scl.scl(macNotchHeight);
        Draw.color();
        Draw.rect(logo, fx, fy, logow, logoh);
        String versionText = ((Version.build == -1) ? "[#fc8140aa]" : "[#ffffffba]") + Version.combined();
        Fonts.outline.setColor(Color.white);
        Fonts.outline.draw(versionText, fx, fy - logoh / 2f - Scl.scl(2f), Align.center);
    }

    @JSBody(params={"frames"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-menu-background','original-MenuRenderer');
        root.setAttribute('data-mindustry-menu-background-frames',String(frames));
        root.setAttribute('data-mindustry-menu-logo','original-MenuFragment');
        """)
    private static native void markRendered(int frames);
}

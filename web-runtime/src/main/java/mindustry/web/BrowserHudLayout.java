package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.layout.*;
import arc.scene.ui.ScrollPane;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/** Fit the original HUD actors to the actual portal Scene, independently of input mode. */
public final class BrowserHudLayout{
    private static boolean checked, diagnostics;
    private static int frames;
    private static boolean inventoryFixture;

    private BrowserHudLayout(){}

    public static boolean compact(){
        return Core.scene != null && (Core.scene.getWidth() < 600f || Core.scene.getHeight() < 480f);
    }

    public static void fitPlacement(Table frame){
        boolean small = compact();
        // A fixed information slot prevents hover/selection from moving the
        // native buttons under the pointer. Long original info remains scrollable.
        ScrollPane info = frame.find("web-placement-info");
        if(info != null){
            float height = small ? Scl.scl(60f) : info.getWidget().getPrefHeight();
            var cell = frame.getCell(info);
            if(cell.minHeight() != height){
                cell.height(height / Scl.scl(1f));
                frame.invalidateHierarchy();
            }
            info.setScrollingDisabled(false, false);
            info.setOverscroll(false, false);
        }
        float scale = small ? Math.min(1f, Math.min(
            Math.max(1f, Core.scene.getWidth() - 10f) / Math.max(1f, frame.getWidth()),
            Math.max(1f, Core.scene.getHeight() - 176f) / Math.max(1f, frame.getHeight()))) : 1f;
        frame.setTransform(small);
        frame.setOrigin(frame.getWidth(), 0f);
        frame.setScale(scale);
    }

    public static void sample(){
        if(!checked){ diagnostics = requested(); checked = true; }
        if(!diagnostics || state == null || !state.isGame() || (++frames & 15) != 0) return;
        if(!inventoryFixture && player.team().core() != null){
            // Test-only world fixture: exercise the native multi-row item list.
            for(var item : content.items()) player.team().core().items.add(item, 500);
            inventoryFixture = true;
        }
        report("minimap", "web-hud-minimap", true);
        report("waves", "web-hud-wave-panel", false);
        report("health", "web-hud-player-health", false);
        report("items", "web-hud-core-items", false);
        report("placement", "web-placement-frame", false);
        report("category", "category-distribution", true);
        report("conveyor", "block-conveyor", true);
        markState(compact(), control.input.block == null ? "none" : control.input.block.name, ui.minimapfrag.shown());
    }

    private static void report(String key, String name, boolean action){
        Element actor = Core.scene.root.find(name);
        if(actor == null || actor.getWidth() <= 1f || actor.getHeight() <= 1f) return;
        Vec2 lower = actor.localToStageCoordinates(new Vec2());
        Vec2 upper = actor.localToStageCoordinates(new Vec2(actor.getWidth(), actor.getHeight()));
        Vec2 center = actor.localToStageCoordinates(new Vec2(actor.getWidth()/2f, actor.getHeight()/2f));
        Element hit = Core.scene.hit(center.x, center.y, true);
        boolean exposed = !action || hit == actor || hit != null && hit.isDescendantOf(actor);
        Core.scene.getViewport().project(lower);
        Core.scene.getViewport().project(upper);
        markRect(key, lower.x, lower.y, upper.x, upper.y, exposed);
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryHudLayoutSmoke') === '1';")
    private static native boolean requested();

    @JSBody(params={"compact","block","fullMap"}, script="var r=document.documentElement;r.setAttribute('data-mindustry-hud-layout',compact?'compact':'normal');r.setAttribute('data-mindustry-hud-layout-block',block);r.setAttribute('data-mindustry-hud-layout-map-open',fullMap?'yes':'no');")
    private static native void markState(boolean compact, String block, boolean fullMap);

    @JSBody(params={"key","left","bottom","right","top","exposed"}, script="var r=document.documentElement,p='data-mindustry-hud-layout-'+key;r.setAttribute(p,JSON.stringify([left,bottom,right,top]));r.setAttribute(p+'-exposed',exposed?'yes':'no');")
    private static native void markRect(String key, float left, float bottom, float right, float top, boolean exposed);
}

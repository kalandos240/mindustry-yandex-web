package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import mindustry.gen.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Opt-in native DesktopInput HUD planet-map acceptance. Reproduces clicking
 * the actual Icon.map inside a live campaign, rather than opening planets
 * from the main menu or invoking ui.planet.show() from a test shortcut.
 */
public final class BrowserHudPlanetSmoke{
    private static boolean checked, enabled, complete;
    private static int frames, waiting;
    private static boolean armed;

    private BrowserHudPlanetSmoke(){}

    public static void update(){
        if(!requested() || complete || state == null || !state.isCampaign()
        || !BrowserCampaignRuntime.active() || Core.scene == null || ui == null
        || ui.planet == null) return;
        if(++frames > 950){
            throw new IllegalStateException("Campaign HUD original planet map stalled; armed="+armed);
        }
        if(armed){
            if(ui.planet.isShown()){
                if(++waiting > 30){
                    complete = true;
                    markOpened();
                }
            }
            return;
        }
        if(!state.isPlaying() || ui.hudGroup == null) return;
        Element table = Core.scene.root.find("inputTable");
        Element icon = findMapButton(table);
        if(!(icon instanceof Button button) || button.isDisabled() || !button.visible) return;
        Vec2 stage = button.localToStageCoordinates(new Vec2(
            button.getWidth()/2f, button.getHeight()/2f));
        Element hit = Core.scene.hit(stage.x,stage.y,true);
        if(hit != button && (hit == null || !hit.isDescendantOf(button))) return;
        Vec2 point = Core.scene.getViewport().project(stage);
        if(point.x <= 0f || point.y <= 0f
        || point.x >= Core.graphics.getWidth()
        || point.y >= Core.graphics.getHeight()) return;
        armed = true;
        markHitpoint(point.x,point.y);
    }

    private static Element findMapButton(Element node){
        if(node instanceof ImageButton button &&
            button.getStyle().imageUp == Icon.map) return button;
        if(node instanceof Group group){
            for(Element child : group.getChildren()){
                Element found = findMapButton(child);
                if(found != null) return found;
            }
        }
        return null;
    }

    private static boolean requested(){
        if(!checked){enabled = queryRequested(); checked = true;}
        return enabled;
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryHudPlanetSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params={"x","y"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-hud-planet-ready','yes');
        root.setAttribute('data-mindustry-hud-planet-x',String(x));
        root.setAttribute('data-mindustry-hud-planet-y',String(y));
        """)
    private static native void markHitpoint(float x,float y);

    @JSBody(script="document.documentElement.setAttribute('data-mindustry-hud-planet-open','yes');")
    private static native void markOpened();
}

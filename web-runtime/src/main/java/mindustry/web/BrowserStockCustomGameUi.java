package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import mindustry.game.*;
import mindustry.maps.Map;
import mindustry.ui.dialogs.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/** Test-only discovery of actual stock actors; never invokes an action for the test. */
public final class BrowserStockCustomGameUi{
    private static boolean checked, enabled;

    public static void sample(){
        if(!checked){ enabled = requested(); checked = true; }
        if(!enabled || ui.custom == null || !ui.custom.isShown()) return;
        Dialog top = Core.scene.getDialog();
        // After Play the stock dialog closes before the test sampler's next
        // frame. Element.isDescendantOf(null) throws in Arc, even though the
        // live map is already running. Do not let QA-only marker sampling
        // crash the actual game during the menu -> world transition.
        if(top == null || (top != ui.custom && !(top instanceof CustomRulesDialog) && !(top instanceof MapPlayDialog))) return;
        String page = top instanceof CustomRulesDialog ? "rules" : top instanceof MapPlayDialog ? "map" : "catalog";
        int previews = 0;
        for(Map map : BrowserLocalMapRuntime.catalog()) if(map.texture != null) previews++;
        markPage(page, previews);
        final String firstMap = maps.all().find(m -> m.file.nameWithoutExtension().equals("archipelago")).name();
        Core.scene.root.forEach(actor -> {
            if(!(actor instanceof Button button) || !button.visible || !button.isDescendantOf(top)
                || button.getWidth() <= 1f || button.getHeight() <= 1f) return;
            String name = null;
            if(button instanceof TextButton text){
                String label = text.getText().toString();
                if(label.equals(Core.bundle.get("play"))) name = "launch";
                else if(label.equals(Core.bundle.get("customize"))) name = "customize";
                else if(label.equals(Core.bundle.get("back"))) name = "back";
                for(Gamemode mode : Gamemode.all) if(label.equals(mode.toString())) name = mode.name();
            }
            if(name == null){
                boolean[] match = {false};
                String label = page.equals("catalog") ? firstMap : page.equals("rules") ? Core.bundle.get("rules.wavetimer") : "";
                if(!label.isEmpty()) button.forEach(child -> {
                    if(child instanceof Label text && text.getText().toString().equals(label)) match[0] = true;
                });
                if(match[0]) name = page.equals("catalog") ? "first-map" : "wave-timer";
            }
            if(name == null) return;
            Vec2 center = button.localToStageCoordinates(new Vec2(button.getWidth()/2f, button.getHeight()/2f));
            Element hit = Core.scene.hit(center.x, center.y, true);
            if(hit != button && (hit == null || !hit.isDescendantOf(button))) return;
            Vec2 screen = Core.scene.getViewport().project(center);
            Vec2 lower = Core.scene.getViewport().project(button.localToStageCoordinates(new Vec2()));
            Vec2 upper = Core.scene.getViewport().project(button.localToStageCoordinates(new Vec2(button.getWidth(), button.getHeight())));
            markPoint(name, screen.x, screen.y, lower.x, lower.y, upper.x, upper.y);
        });
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryCustomSmoke')==='1';")
    private static native boolean requested();

    @JSBody(params={"page","previews"}, script="document.documentElement.setAttribute('data-mindustry-custom-page',page);document.documentElement.setAttribute('data-mindustry-custom-previews',String(previews));")
    private static native void markPage(String page, int previews);

    @JSBody(params={"name","x","y","left","bottom","right","top"}, script="""
        const r=document.documentElement, p='data-mindustry-custom-'+name+'-';
        r.setAttribute(p+'x',String(x));r.setAttribute(p+'y',String(y));
        r.setAttribute(p+'left',String(left));r.setAttribute(p+'bottom',String(bottom));
        r.setAttribute(p+'right',String(right));r.setAttribute(p+'top',String(top));
        """)
    private static native void markPoint(String name, float x, float y, float left, float bottom, float right, float top);
}

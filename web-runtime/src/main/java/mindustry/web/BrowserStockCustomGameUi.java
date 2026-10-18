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
        String page = top instanceof CustomRulesDialog ? "rules" : top instanceof MapPlayDialog ? "map" : "catalog";
        int previews = 0;
        for(Map map : BrowserLocalMapRuntime.catalog()) if(map.texture != null) previews++;
        markPage(page, previews);
        final String maze = maps.all().find(m -> m.file.nameWithoutExtension().equals("maze")).name();
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
                String label = page.equals("catalog") ? maze : page.equals("rules") ? Core.bundle.get("rules.wavetimer") : "";
                if(!label.isEmpty()) button.forEach(child -> {
                    if(child instanceof Label text && text.getText().toString().equals(label)) match[0] = true;
                });
                if(match[0]) name = page.equals("catalog") ? "maze" : "wave-timer";
            }
            if(name == null) return;
            Vec2 center = button.localToStageCoordinates(new Vec2(button.getWidth()/2f, button.getHeight()/2f));
            Element hit = Core.scene.hit(center.x, center.y, true);
            if(hit != button && (hit == null || !hit.isDescendantOf(button))) return;
            Vec2 screen = Core.scene.getViewport().project(center);
            markPoint(name, screen.x, screen.y);
        });
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryCustomSmoke')==='1';")
    private static native boolean requested();

    @JSBody(params={"page","previews"}, script="document.documentElement.setAttribute('data-mindustry-custom-page',page);document.documentElement.setAttribute('data-mindustry-custom-previews',String(previews));")
    private static native void markPage(String page, int previews);

    @JSBody(params={"name","x","y"}, script="document.documentElement.setAttribute('data-mindustry-custom-'+name+'-x',String(x));document.documentElement.setAttribute('data-mindustry-custom-'+name+'-y',String(y));")
    private static native void markPoint(String name, float x, float y);
}

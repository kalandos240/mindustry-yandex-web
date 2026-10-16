package mindustry.ui.dialogs;

import mindustry.type.*;
import arc.*;
import arc.scene.*;
import arc.math.geom.*;
import mindustry.web.*;
import org.teavm.jso.JSBody;

/**
 * Runs the original Mindustry PlanetDialog (planet meshes/camera, sectors,
 * search and info) but swaps only the unsupported desktop sector launch
 * transition with the tested single-player browser campaign bridge.
 */
public class BrowserPlanetDialog extends PlanetDialog{
    private boolean firstFrame;
    private int uiSampleFrames;
    private String lastPlanet = "";

    @Override
    void addTech(){
        // Reuse the fully verified browser TechTree while the stock
        // ResearchDialog's eager desktop dependencies are being adapted.
        // The original planet view keeps its native tree button and icon.
        buttons.button("@techtree", mindustry.gen.Icon.tree, () -> {
            hide();
            BrowserResearchUi.show();
        }).size(200f, 54f).visible(() -> mode == Mode.look).pad(2).bottom().name("web-planet-tech");
    }

    @Override
    public void viewPlanet(Planet planet, boolean pan){
        // Observe the actual stock planet-selection callback. This is not
        // called by JavaScript and does not change the upstream action.
        markNativeChoice(planet.name);
        super.viewPlanet(planet, pan);
    }

    @Override
    void playSelected(){
        Sector sector = selected;
        if(sector == null) return;

        if(mode == Mode.look){
            if(sector.isBeingPlayed()){
                hide();
            }else{
                BrowserPlanetBridge.launchSector(sector);
            }
            return;
        }

        // Original select/planetLaunch callback semantics are preserved.
        listener.get(sector);
        hide();
    }

    @Override
    public void draw(){
        super.draw();
        if(!firstFrame){
            firstFrame = true;
            markFirstRender();
        }

        // The exact Arc Scene hitboxes and real visible planet are sampled
        // sparingly, solely to let the browser integration test click stock
        // touch/mouse buttons, rather than invoking a test-only Java shortcut.
        if(++uiSampleFrames % 18 == 0 && isShown()){
            String planet = state.planet == null ? "none" : state.planet.name;
            if(!planet.equals(lastPlanet)){
                lastPlanet = planet;
                markViewedPlanet(planet);
            }
            sampleButton("web-planet-choice-serpulo", "serpulo");
            sampleButton("web-planet-choice-erekir", "erekir");
            sampleButton("web-planet-tech", "tech");
            markZoom(state.zoom);
        }
    }

    private void sampleButton(String name, String marker){
        Element button = find(name);
        if(button != null && button.getWidth() > 0f && button.getHeight() > 0f){
            Vec2 at = button.localToStageCoordinates(new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
            Core.scene.getViewport().project(at);
            markButtonPosition(marker, at.x, at.y);
        }
    }

    @JSBody(params={"planet"}, script="document.documentElement.setAttribute('data-mindustry-planet-native-choice',planet);")
    private static native void markNativeChoice(String planet);

    @JSBody(params={"planet"}, script="document.documentElement.setAttribute('data-mindustry-planet-viewed',planet);")
    private static native void markViewedPlanet(String planet);

    @JSBody(params={"target","x","y"}, script="""
        var root=document.documentElement;
        root.setAttribute('data-mindustry-planet-hit-'+target+'-x',String(x));
        root.setAttribute('data-mindustry-planet-hit-'+target+'-y',String(y));
        """)
    private static native void markButtonPosition(String target, float x, float y);

    @JSBody(params={"zoom"}, script="document.documentElement.setAttribute('data-mindustry-planet-zoom',String(zoom));")
    private static native void markZoom(float zoom);

    @JSBody(script="document.documentElement.setAttribute('data-mindustry-vanilla-planet-frame','rendered');")
    private static native void markFirstRender();
}

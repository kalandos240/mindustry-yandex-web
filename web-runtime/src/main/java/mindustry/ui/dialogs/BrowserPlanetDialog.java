package mindustry.ui.dialogs;

import mindustry.type.*;
import mindustry.web.*;
import org.teavm.jso.JSBody;

/**
 * Runs the original Mindustry PlanetDialog (planet meshes/camera, sectors,
 * search and info) but swaps only the unsupported desktop sector launch
 * transition with the tested single-player browser campaign bridge.
 */
public class BrowserPlanetDialog extends PlanetDialog{
    private boolean firstFrame;

    @Override
    void addTech(){
        // Reuse the fully verified browser TechTree while the stock
        // ResearchDialog's eager desktop dependencies are being adapted.
        // The original planet view keeps its native tree button and icon.
        buttons.button("@techtree", mindustry.gen.Icon.tree, () -> {
            hide();
            BrowserResearchUi.show();
        }).size(200f, 54f).visible(() -> mode == Mode.look).pad(2).bottom();
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
    }

    @JSBody(script="document.documentElement.setAttribute('data-mindustry-vanilla-planet-frame','rendered');")
    private static native void markFirstRender();
}

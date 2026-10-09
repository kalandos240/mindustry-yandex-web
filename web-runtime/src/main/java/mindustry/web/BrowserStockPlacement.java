package mindustry.web;

import arc.*;
import arc.scene.*;
import mindustry.ui.fragments.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * The original Mindustry PlacementFragment is the authoritative build HUD:
 * textured block icons, category selection, block costs, and unit commands.
 * Keep the compact Web palette as a boot fallback until this fragment is
 * successfully attached to the same HUD group used by stock InputHandler.
 */
public final class BrowserStockPlacement{
    private static boolean active;

    private BrowserStockPlacement(){}

    public static boolean active(){
        return active;
    }

    public static void install(Group parent){
        if(active) return;
        if(parent == null || ui == null || control == null || control.input == null){
            throw new IllegalStateException("Stock Mindustry placement HUD requires core input and HUD group");
        }
        String stage = "create-HudFragment";
        try{
            if(ui.hudfrag == null) ui.hudfrag = new HudFragment();
            // Build the upstream Mindustry actor graph, not a Web-only clone.
            stage = "build-PlacementFragment";
            ui.hudfrag.blockfrag.build(parent);
            stage = "verify-inputTable";
            // The native inputTable owns stock placement/configuration controls.
            if(parent.find("inputTable") == null){
                throw new IllegalStateException("Stock PlacementFragment did not mount native placement inputTable");
            }
            stage = "mark-ready";
            active = true;
            markNativePlacementReady();
        }catch(Throwable error){
            active = false;
            throw new IllegalStateException("Native placement setup failed at " + stage, error);
        }
    }

    @JSBody(script = "const r=document.documentElement; r.setAttribute('data-mindustry-stock-placement','ready'); r.setAttribute('data-mindustry-stock-placement-source','mindustry.ui.fragments.PlacementFragment'); r.setAttribute('data-mindustry-stock-placement-input','ready');")
    private static native void markNativePlacementReady();
}

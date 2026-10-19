package mindustry.web;

import arc.Core;
import mindustry.type.SectorPreset;
import org.teavm.jso.*;

/** Keep the current world intact while a deferred campaign map is fetched. */
public final class BrowserCampaignAssets{
    private static boolean pending;

    @JSFunctor
    public interface Completion extends JSObject{
        void complete(String error);
    }

    private BrowserCampaignAssets(){}

    /** Returns true when the action is deferred (or another launch is pending). */
    public static boolean defer(SectorPreset preset, Runnable action){
        if(pending) return true;
        if(preset == null || preset.sector == null) return false;
        String path = "maps/" + preset.sector.planet.name + "/" + preset.name + ".msav";
        if(Core.files.internal(path).length() >= 128) return false;
        pending = true;
        prepare(path, error -> Core.app.post(() -> {
            pending = false;
            if(error != null && !error.isEmpty()){
                showFailure(error);
                return;
            }
            try{
                action.run();
            }catch(Throwable failure){
                showFailure(failure.getClass().getName() + ": " + failure.getMessage());
            }
        }));
        return true;
    }

    @JSBody(params={"path", "done"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-campaign-map-request',path);
        root.setAttribute('data-mindustry-campaign-map-request-state','loading');
        Promise.resolve().then(() => globalThis.__mindustryPrepareCampaignMap(path)).then(() => {
            root.setAttribute('data-mindustry-campaign-map-request-state','ready');
            done('');
        }, error => {
            root.setAttribute('data-mindustry-campaign-map-request-state','failed');
            done(String(error));
        });
        """)
    private static native void prepare(String path, Completion done);

    @JSBody(params={"error"}, script="""
        document.documentElement.setAttribute('data-mindustry-planet-launch-error',error);
        console.error('Mindustry campaign map request failed:',error);
        const status=document.getElementById('mindustry-web-status');
        if(status){
            status.textContent='Sector could not start: '+error;
            status.style.opacity='1';
            status.style.color='#ffb2a8';
            status.style.pointerEvents='none';
        }
        """)
    private static native void showFailure(String error);
}

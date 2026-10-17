package mindustry.ui.dialogs;

import mindustry.type.*;
import arc.*;
import arc.scene.*;
import arc.scene.ui.*;
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
    private Sector waitingForMap;

    @Override
    Sector findLauncher(Sector to){
        // Original renderOverProjections calls this while drawing hovered
        // sectors, BEFORE the browser launch action can bind their maps.
        if(!BrowserCampaignRuntime.preparePresetGenerator(to)) return null;
        return super.findLauncher(to);
    }

    @Override
    boolean hasNoCandidate(Sector sector){
        return !sector.hasBase() && !BrowserCampaignRuntime.preparePresetGenerator(sector) || super.hasNoCandidate(sector);
    }

    @Override
    public void selectSector(Sector sector){
        waitingForMap = sector == null || sector.hasBase() || BrowserCampaignRuntime.preparePresetGenerator(sector) ? null : sector;
        super.selectSector(sector);
        if(waitingForMap != null){
            BrowserCampaignAssets.defer(sector.preset, () -> {
                if(selected == sector && BrowserCampaignRuntime.preparePresetGenerator(sector)){
                    waitingForMap = null;
                    updateSelected();
                }
            });
        }
    }

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
    public Dialog show(){
        // In desktop vanilla the first-run portrait chooser selects/centers
        // the starting sector. Web deliberately omits that obstructed overlay,
        // so make the REAL stock sectorTop launch panel visible immediately.
        // Otherwise a fresh player sees only a globe and no way to begin
        // either campaign without accurately clicking a tiny sector on it.
        Dialog dialog = super.show();
        focusStartingSector();
        return dialog;
    }

    private void focusStartingSector(){
        if(mode != Mode.look || state.planet == null || state.planet.getStartSector() == null) return;
        Sector sector = state.planet.getStartSector();
        lookAt(sector);
        selectSector(sector);
        markStartSectorVisible(state.planet.name, sector.id);
    }

    @Override
    public void viewPlanet(Planet planet, boolean pan){
        // Keep the native Serpulo/Erekir selector, AND retarget the sector
        // action to this planet. The starting sector never stays pointing at
        // a different planet after a browser globe switch.
        markNativeChoice(planet.name);
        super.viewPlanet(planet, pan);
        focusStartingSector();
    }

    @JSBody(params={"planet","id"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-planet-start-sector-planet',planet);
        r.setAttribute('data-mindustry-planet-start-sector-id',String(id));
        r.setAttribute('data-mindustry-planet-start-sector-ready','yes');
        """)
    private static native void markStartSectorVisible(String planet,int id);

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

    /** Identify the actual stock Arc Scene phase causing the user-reported
     * planet-map NullPointerException. TeaVM's default NPE stack reports only
     * its type, so record the last entered UI stage before it is propagated. */
    @Override
    void setup(){
        markPlanetFramePhase("setup-enter");
        try{
            super.setup();
            markPlanetFramePhase("setup-finished");
        }catch(Throwable crash){
            markPlanetFrameFailure("setup", crash);
            throw crash;
        }
    }

    @Override
    public void act(float delta){
        markPlanetFramePhase("act-enter");
        try{
            super.act(delta);
            if(waitingForMap != null && selected == waitingForMap
            && BrowserCampaignRuntime.preparePresetGenerator(waitingForMap)){
                waitingForMap = null;
                updateSelected();
            }
            markPlanetFramePhase("act-finished");
        }catch(Throwable crash){
            markPlanetFrameFailure("act", crash);
            throw crash;
        }
    }

    @JSBody(params={"phase"},script="document.documentElement.setAttribute('data-mindustry-planet-frame-phase',phase);")
    private static native void markPlanetFramePhase(String phase);

    @JSBody(params={"phase","type"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-planet-frame-failure',phase+' / '+type);
        """)
    private static native void markPlanetFrameFailureNative(String phase, String type);

    private static void markPlanetFrameFailure(String phase, Throwable error){
        markPlanetFrameFailureNative(phase,error.getClass().getName()
            + ": " + String.valueOf(error.getMessage()));
    }

    @Override
    public void draw(){
        markPlanetFramePhase("draw-enter");
        try{
            super.draw();
            markPlanetFramePhase("draw-super-finished");
        }catch(Throwable crash){
            markPlanetFrameFailure("draw-super",crash);
            throw crash;
        }
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
            sampleButton("web-planet-launch", "launch");
            markZoom(state.zoom);
            markFirstIntroStep(Core.settings.getString("web-planet-intro-step", "not-clicked"));

            // The stock first-run campaign chooser is a *second* BaseDialog
            // above PlanetDialog. Preserve it and sample its native hitboxes
            // separately before the test interacts with the underlying map.
            var topDialog = Core.scene.getDialog();
            boolean firstChooser = topDialog != null && topDialog != this
                && topDialog.find("web-campaign-first-choice-serpulo") != null;
            markFirstChooser(firstChooser);
            markFirstRunActions(Core.settings.getString("web-first-choice", "none"),
                Core.settings.getString("web-first-ok", "none"),
                Core.settings.getString("web-first-stage", "none"));
            markFirstSceneDown(Core.settings.getString("web-first-scene-down", "none"));
            markFirstRootDown(Core.settings.getString("web-first-root-down", "none"));
            markInputRoute(Core.settings.getString("web-first-queue-route", "none"));
            markQueueEvent("data-mindustry-planet-first-queue-enqueued",
                Core.settings.getString("web-first-queue-enqueued", "none"));
            markQueueEvent("data-mindustry-planet-first-queue-drained",
                Core.settings.getString("web-first-queue-drained", "none"));
            if(firstChooser){
                sampleButton("web-campaign-first-choice-serpulo", "intro-serpulo");
                sampleButton("web-campaign-first-choice-erekir", "intro-erekir");
                sampleButton("web-campaign-first-confirm", "intro-confirm");
            }
        }
    }

    @JSBody(params={"name","value"}, script="document.documentElement.setAttribute(name,value);")
    private static native void markQueueEvent(String name, String value);

    private void sampleButton(String name, String marker){
        Element button = name.startsWith("web-campaign-first-")
            ? Core.scene.getDialog() == null ? null : Core.scene.getDialog().find(name)
            : find(name);
        if(button != null && button.getWidth() > 0f && button.getHeight() > 0f){
            if(button instanceof Button stockButton){
                markButtonState(marker, stockButton.isChecked(), stockButton.isDisabled());
                int clickListeners = 0, totalTaps = 0, stillPressed = 0;
                for(var listener : stockButton.getListeners()){
                    if(listener instanceof arc.scene.event.ClickListener c){
                        clickListeners++;
                        totalTaps += c.getTapCount();
                        if(c.isPressed()) stillPressed++;
                    }
                }
                markClickListeners(marker, clickListeners, totalTaps, stillPressed);
            }
            Vec2 at = button.localToStageCoordinates(new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
            Element receiver = Core.scene.hit(at.x, at.y, true);
            boolean exposed = receiver == button || receiver != null
                && receiver.isDescendantOf(actor -> actor == button);
            StringBuilder ancestry = new StringBuilder();
            Element ancestor = receiver;
            for(int i = 0; i < 8 && ancestor != null; i++, ancestor = ancestor.parent){
                if(i > 0) ancestry.append(">");
                ancestry.append(ancestor.name == null ? ancestor.getClass().getSimpleName() : ancestor.name);
            }
            markHitReceiver(marker, ancestry.toString(), exposed);
            Core.scene.getViewport().project(at);
            markButtonPosition(marker, at.x, at.y);
        }
    }

    @JSBody(params={"choice","ok","phase"}, script="""
        var root=document.documentElement;
        root.setAttribute('data-mindustry-planet-first-selected',choice);
        root.setAttribute('data-mindustry-planet-first-confirm',ok);
        root.setAttribute('data-mindustry-planet-first-stage',phase);
        """)
    private static native void markFirstRunActions(String choice, String ok, String phase);

    @JSBody(params={"target"}, script="document.documentElement.setAttribute('data-mindustry-planet-first-scene-down',target);")
    private static native void markFirstSceneDown(String target);

    @JSBody(params={"target"}, script="document.documentElement.setAttribute('data-mindustry-planet-first-root-down',target);")
    private static native void markFirstRootDown(String target);

    @JSBody(params={"route"}, script="document.documentElement.setAttribute('data-mindustry-planet-first-queue-route',route);")
    private static native void markInputRoute(String route);

    @JSBody(params={"step"}, script="document.documentElement.setAttribute('data-mindustry-planet-first-intro-step',step);")
    private static native void markFirstIntroStep(String step);

    @JSBody(params={"shown"}, script="document.documentElement.setAttribute('data-mindustry-planet-first-chooser',shown ? 'open' : 'closed');")
    private static native void markFirstChooser(boolean shown);

    @JSBody(params={"planet"}, script="document.documentElement.setAttribute('data-mindustry-planet-native-choice',planet);")
    private static native void markNativeChoice(String planet);

    @JSBody(params={"planet"}, script="document.documentElement.setAttribute('data-mindustry-planet-viewed',planet);")
    private static native void markViewedPlanet(String planet);

    @JSBody(params={"target","checked","disabled"}, script="""
        var root=document.documentElement;
        root.setAttribute('data-mindustry-planet-hit-'+target+'-checked',checked ? 'yes' : 'no');
        root.setAttribute('data-mindustry-planet-hit-'+target+'-disabled',disabled ? 'yes' : 'no');
        """)
    private static native void markButtonState(String target, boolean checked, boolean disabled);

    @JSBody(params={"target","listeners","taps","pressed"}, script="""
        var root=document.documentElement, base='data-mindustry-planet-hit-'+target;
        root.setAttribute(base+'-listeners',String(listeners));
        root.setAttribute(base+'-taps',String(taps));
        root.setAttribute(base+'-pressed',String(pressed));
        """)
    private static native void markClickListeners(String target, int listeners, int taps, int pressed);

    @JSBody(params={"target","receiver","exposed"}, script="""
        var root=document.documentElement;
        root.setAttribute('data-mindustry-planet-hit-'+target+'-receiver',receiver);
        root.setAttribute('data-mindustry-planet-hit-'+target+'-exposed',exposed ? 'yes' : 'no');
        """)
    private static native void markHitReceiver(String target, String receiver, boolean exposed);

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

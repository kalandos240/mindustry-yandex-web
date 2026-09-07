package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.assets.loaders.*;
import arc.files.*;
import arc.struct.*;
import mindustry.core.ContentLoader;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import org.teavm.jso.JSBody;

import java.io.*;

import static mindustry.Vars.*;

/**
 * Local-only production map catalog and continuous play loop for the browser port.
 *
 * This deliberately registers only the maps that pinned Mindustry v159.7 exposes from
 * Maps.defaultMapNames. The extra canyon.msav present in the asset directory is not part
 * of that stock list and therefore remains packaged-but-hidden, matching upstream.
 *
 * The first production-map milestone keeps optional gameplay branches (waves, fog,
 * weather, game-over and AI builders) disabled because Logic.updateWebPlayingCore() has
 * not enabled those reachability branches yet. Map loading, stock entities/buildings,
 * player input, rendering, pathfinding and browser persistence all remain real.
 */
public final class BrowserLocalMapRuntime{
    private static final String[] builtinSlugs = {
        "maze", "fortress", "labyrinth", "islands", "tendrils", "caldera",
        "wasteland", "shattered", "fork", "triad", "mudFlats", "moltenLake",
        "archipelago", "debrisField", "domain", "veins", "glacier", "passage"
    };

    private static final Seq<Map> catalog = new Seq<>();
    private static boolean initialized;
    private static boolean active;
    private static boolean testStartChecked;
    private static Map current;
    private static int frames;

    private BrowserLocalMapRuntime(){}

    /** Load metadata for the exact stock built-in map catalog; no custom/workshop/mod scan. */
    public static void init(){
        if(initialized) return;
        if(Core.files == null || Core.assets == null || content == null || waves == null){
            throw new IllegalStateException("Browser local maps require packaged files, content and Waves");
        }

        // The desktop bootstrap normally registers ContentLoader through AssetManager.loadRun
        // before Maps is constructed. This lean launcher bypasses that queue, but the stock
        // Maps constructor still expects the loader object. Register an inert browser loader;
        // it is never queued or executed, so no external/custom map scan is introduced.
        if(Core.assets.getLoader(ContentLoader.class) == null){
            Core.assets.setLoader(ContentLoader.class, new CustomLoader(){
                @Override
                public void loadAsync(AssetManager manager, String fileName, Fi file, AssetLoaderParameters parameter){
                    // Browser content was already created synchronously by WebClientLauncher.
                }
            });
        }

        // Maps is retained only for Map.filters()/readFilters semantics. Do not call load():
        // that stock method scans custom/workshop/mod sources that are intentionally absent.
        if(maps == null) maps = new Maps();
        if(!maps.all().isEmpty()){
            throw new IllegalStateException("Browser local map catalog must start from an empty Maps registry");
        }

        for(String slug : builtinSlugs){
            Fi file = Core.files.internal("maps/default/" + slug + "." + mapExtension);
            if(!file.exists()){
                throw new IllegalStateException("Pinned built-in map is missing from the Web package: " + slug);
            }

            try{
                Map map = MapIO.createMap(file, false);
                if(map.name() == null || map.name().trim().isEmpty()){
                    throw new IllegalStateException("Pinned built-in map has no display name: " + slug);
                }
                catalog.add(map);
                maps.all().add(map);
            }catch(IOException error){
                throw new IllegalStateException("Failed to read packaged built-in map metadata: " + slug, error);
            }
        }

        catalog.sort();
        maps.all().sort();
        if(catalog.size != builtinSlugs.length || maps.all().size != builtinSlugs.length){
            throw new IllegalStateException("Browser built-in map catalog count mismatch");
        }

        initialized = true;
        markCatalogReady(catalog.size);
    }

    public static Seq<Map> catalog(){
        if(!initialized) throw new IllegalStateException("Browser local map catalog is not initialized");
        return catalog;
    }

    public static boolean active(){
        return active;
    }

    public static Map current(){
        return current;
    }

    /** Start the selected packaged map through the stock local world/play lifecycle. */
    public static void start(Map map){
        if(!initialized) throw new IllegalStateException("Browser local map catalog is not initialized");
        if(active) throw new IllegalStateException("A browser local map session is already active");
        if(map == null || !catalog.contains(map, true)){
            throw new IllegalArgumentException("Map is not part of the pinned browser catalog");
        }
        if(state == null || !state.isMenu() || logic == null || world == null || control == null
        || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("Browser local map start requires a stable production menu runtime");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("Browser local map start escaped permanent single-player mode");
        }

        String slug = slug(map);
        markPhase("reset");
        logic.reset();

        Rules rules = map.applyRules(Gamemode.survival);
        stageCoreRules(rules);

        // World.loadMap() intentionally converts any SaveIO failure into the single
        // invalidMap flag for desktop UI. That is too opaque for the browser port: a
        // legacy .msav deserialization failure and a genuine no-core map otherwise look
        // identical in CI. Run the same stock FilterContext/SaveIO boundary directly,
        // preserve filters and SaveLoadEvent semantics, then apply the equivalent
        // single-player core validation with an explicit diagnostic marker.
        markPhase("world-load");
        try{
            SaveIO.load(map.file, world.new FilterContext(map));
        }catch(Throwable error){
            String reason = failureReason(error);
            markLoadDiagnostic(slug, "save-exception", reason, world.width(), world.height(), 0);
            markFailed(slug, reason);
            throw new IllegalStateException("Failed to load packaged browser map " + slug + ": " + reason, error);
        }
        state.map = map;

        int defaultCores = state.teams.cores(rules.defaultTeam).size;
        markLoadDiagnostic(slug, "save-loaded", rules.defaultTeam.name, world.width(), world.height(), defaultCores);
        if(defaultCores == 0){
            String reason = "no-default-core:" + rules.defaultTeam.name;
            markFailed(slug, reason);
            throw new IllegalStateException("Packaged browser map has no core for default team after SaveIO.load: " + slug + " / " + rules.defaultTeam.name);
        }

        // Match Control.playMap(): retain content fields decoded from the real map file,
        // then install the selected rules. Re-apply the staged optional-branch gate after
        // retainContentFields() because weather is one of the retained fields.
        Rules loadedRules = state.rules;
        rules.retainContentFields(loadedRules);
        stageCoreRules(rules);
        state.rules = rules;
        state.map = map;
        state.rules.sector = null;
        state.rules.editor = false;

        current = map;
        frames = 0;
        active = true;

        try{
            markPhase("play-event");
            logic.play();
            Events.fire(Trigger.newGame);
        }catch(Throwable error){
            active = false;
            current = null;
            markFailed(slug, error.getClass().getName());
            throw error;
        }

        if(!state.isPlaying() || !player.isAdded() || state.rules.defaultTeam.core() == null){
            active = false;
            current = null;
            throw new IllegalStateException("Built-in browser map did not enter a valid local playing state: " + slug);
        }

        Core.camera.position.set(state.rules.defaultTeam.core());
        markStarted(slug, map.plainName(), world.width(), world.height());
    }

    /** One production browser frame. Unlike BrowserPlayingRuntime this never auto-restores. */
    public static void updateFrame(){
        if(!active || current == null || !state.isPlaying()){
            throw new IllegalStateException("Browser production map frame requires an active playing session");
        }

        long beforeUpdateId = state.updateId;

        markPhase("logic");
        logic.updateWebPlayingCore();
        markPhase("logic-ready");

        pathfinder.updateWeb();
        controlPath.updateWeb();

        markPhase("control");
        control.update();
        markPhase("control-ready");

        markPhase("renderer");
        renderer.update();
        markPhase("renderer-ready");

        markPhase("ui");
        ui.update();
        markPhase("ui-ready");

        // A local HUD action may intentionally return to the map menu during Scene.act().
        if(!active || state.isMenu()) return;
        if(!state.isPlaying()){
            throw new IllegalStateException("Browser production map unexpectedly left playing state");
        }
        if(state.updateId != beforeUpdateId + 1L){
            throw new IllegalStateException("Browser production map update clock advanced incorrectly");
        }

        frames++;
        markFrame(frames, state.updateId, player.unit() == null ? "spawning" : player.unit().type.name);
        if(frames >= 3) markLive(frames);
    }

    /** Return to the stable local map selector without touching any remote service. */
    public static void returnToMenu(){
        if(!active) return;
        String previous = current == null ? "unknown" : slug(current);
        active = false;
        current = null;
        frames = 0;
        logic.reset();
        markReturned(previous);
    }

    /** Test-only URL hook; production without the query remains entirely user-controlled. */
    public static void maybeStartTestMap(){
        if(testStartChecked) return;
        testStartChecked = true;

        String requested = requestedTestMap();
        if(requested == null || requested.isEmpty()) return;

        Map map = bySlug(requested);
        if(map == null){
            throw new IllegalArgumentException("Unknown mindustryMapSmoke built-in map: " + requested);
        }
        markTestRequested(requested);
        start(map);
    }

    private static Map bySlug(String requested){
        for(Map map : catalog){
            if(slug(map).equalsIgnoreCase(requested)) return map;
        }
        return null;
    }

    private static String slug(Map map){
        return map.file.nameWithoutExtension();
    }

    private static String failureReason(Throwable error){
        Throwable root = error;
        int depth = 0;
        while(root.getCause() != null && root.getCause() != root && depth++ < 8){
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getName() + (message == null || message.isEmpty() ? "" : ":" + message);
    }

    /**
     * Keep only gameplay branches already proven on TeaVM. This is an explicit staged
     * porting gate, not a replacement rule set; waves/AI/weather are enabled in later
     * milestones by expanding Logic.updateWebPlayingCore().
     */
    private static void stageCoreRules(Rules rules){
        rules.waves = false;
        rules.waveTimer = false;
        rules.fog = false;
        rules.staticFog = false;
        rules.canGameOver = false;
        rules.attackMode = false;
        rules.pvp = false;
        rules.weather.clear();
        rules.editor = false;
        rules.sector = null;

        for(Team team : Team.all){
            Rules.TeamRule teamRules = rules.teams.get(team);
            teamRules.fillItems = false;
            teamRules.buildAi = false;
            teamRules.rtsAi = false;
            teamRules.prebuildAi = false;
        }
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();

    @JSBody(params = {"count"}, script = "document.documentElement.setAttribute('data-mindustry-map-catalog', 'ready'); document.documentElement.setAttribute('data-mindustry-map-count', String(count)); document.documentElement.setAttribute('data-mindustry-map-source', 'pinned-builtin-local-only');")
    private static native void markCatalogReady(int count);

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-phase', phase);")
    private static native void markPhase(String phase);

    @JSBody(params = {"slug", "status", "detail", "width", "height", "cores"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-load-status', status); document.documentElement.setAttribute('data-mindustry-local-map-load-detail', detail); document.documentElement.setAttribute('data-mindustry-local-map-load-world', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-local-map-load-cores', String(cores)); document.documentElement.setAttribute('data-mindustry-local-map-slug', slug);")
    private static native void markLoadDiagnostic(String slug, String status, String detail, int width, int height, int cores);

    @JSBody(params = {"slug", "name", "width", "height"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-state', 'playing'); document.documentElement.setAttribute('data-mindustry-local-map-slug', slug); document.documentElement.setAttribute('data-mindustry-local-map-name', name); document.documentElement.setAttribute('data-mindustry-local-map-world', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-local-map-player', 'added'); document.documentElement.setAttribute('data-mindustry-local-map-loop', 'starting');")
    private static native void markStarted(String slug, String name, int width, int height);

    @JSBody(params = {"frames", "updateId", "unit"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-local-map-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-map-unit', unit); document.documentElement.setAttribute('data-mindustry-local-map-module-order', 'logic-pathfinding-control-renderer-ui');")
    private static native void markFrame(int frames, long updateId, String unit);

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-loop', 'live'); document.documentElement.setAttribute('data-mindustry-local-map-frames', String(frames));")
    private static native void markLive(int frames);

    @JSBody(params = {"slug"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-state', 'menu'); document.documentElement.setAttribute('data-mindustry-local-map-returned-from', slug); document.documentElement.setAttribute('data-mindustry-local-map-loop', 'stopped');")
    private static native void markReturned(String slug);

    @JSBody(params = {"slug", "reason"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-state', 'error'); document.documentElement.setAttribute('data-mindustry-local-map-slug', slug); document.documentElement.setAttribute('data-mindustry-local-map-error', reason);")
    private static native void markFailed(String slug, String reason);

    @JSBody(params = {"slug"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-test', slug);")
    private static native void markTestRequested(String slug);
}

package mindustry.web;

import arc.*;
import arc.files.*;
import arc.math.*;
import arc.struct.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.game.EventType.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.world.*;
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
 * Browser build overlays expand this base runtime with the already-proven stock survival
 * wave lifecycle and local core-loss Game Over handling. Fog, weather, campaign/PvP and
 * builder/RTS AI remain explicit later milestones. Map loading, stock entities/buildings,
 * player input, rendering, pathfinding and browser persistence are real and local-only.
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
    private static Unit enemyPathUnit;
    private static float enemyPathStartX, enemyPathStartY, enemyPathStartDistance;
    private static int enemyPathFrames;
    private static boolean enemyPathComplete;

    private BrowserLocalMapRuntime(){}

    /** Load metadata for the exact stock built-in map catalog; no custom/workshop/mod scan. */
    public static void init(){
        if(initialized) return;
        if(Core.files == null || content == null || waves == null){
            throw new IllegalStateException("Browser local maps require packaged files, content and Waves");
        }

        // Maps is retained only for Map.filters()/readFilters semantics. Its constructor
        // is patched for Web and no longer registers the desktop preview ContentLoader
        // callback, so the lean browser launcher does not need an inert AssetManager shim.
        // Do not call load(): that stock method scans custom/workshop/mod sources that are
        // intentionally absent from the self-contained Yandex package.
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
        startEnemyPathSmoke();
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
        updateEnemyPathSmoke();

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
        enemyPathUnit = null;
        enemyPathFrames = 0;
        enemyPathComplete = false;
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

    private static void startEnemyPathSmoke(){
        if(!enemyPathSmokeRequested()) return;

        Building core = state.rules.defaultTeam.core();
        Team enemyTeam = state.rules.waveTeam;
        if(core == null || enemyTeam == state.rules.defaultTeam){
            throw new IllegalStateException("Enemy path smoke requires distinct local default/wave teams and a core");
        }

        Unit enemy = UnitTypes.dagger.create(enemyTeam);
        if(!(enemy.controller() instanceof GroundAI)){
            throw new IllegalStateException("Web enemy path smoke did not receive stock GroundAI");
        }

        float minimumDistance = tilesize * 24f;
        float bestDistance = Float.MAX_VALUE;
        Tile spawn = null;
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile tile = world.tile(x, y);
                if(tile == null || !enemy.canPass(x, y)) continue;
                float distance = Mathf.dst(core.x, core.y, tile.worldx(), tile.worldy());
                if(distance >= minimumDistance && distance < bestDistance){
                    bestDistance = distance;
                    spawn = tile;
                }
            }
        }

        if(spawn == null){
            throw new IllegalStateException("Enemy path smoke found no passable tile far enough from the local core");
        }

        enemy.set(spawn.worldx(), spawn.worldy());
        enemy.add();
        if(!enemy.isAdded() || !enemy.isValid()){
            throw new IllegalStateException("Enemy path smoke could not add the stock dagger entity");
        }

        enemyPathUnit = enemy;
        enemyPathStartX = enemy.x;
        enemyPathStartY = enemy.y;
        enemyPathStartDistance = bestDistance;
        enemyPathFrames = 0;
        enemyPathComplete = false;
        markEnemyPathStarted(enemy.id, enemyTeam.name);
    }

    private static void updateEnemyPathSmoke(){
        Unit enemy = enemyPathUnit;
        if(enemy == null || enemyPathComplete) return;
        if(!enemy.isAdded() || !enemy.isValid() || !(enemy.controller() instanceof GroundAI)){
            throw new IllegalStateException("Stock GroundAI enemy became invalid during Web path smoke");
        }

        Building core = state.rules.defaultTeam.core();
        if(core == null){
            throw new IllegalStateException("Local core disappeared during Web enemy path smoke");
        }

        enemyPathFrames++;
        float moved = Mathf.dst(enemyPathStartX, enemyPathStartY, enemy.x, enemy.y);
        float distance = Mathf.dst(core.x, core.y, enemy.x, enemy.y);

        if(moved > tilesize * 1.5f && distance < enemyPathStartDistance - tilesize){
            enemyPathComplete = true;
            markEnemyPathMoved(enemyPathFrames);
            return;
        }

        if(enemyPathFrames >= 360){
            throw new IllegalStateException(
                "Stock GroundAI/Pathfinder did not move the local enemy toward core: moved=" + moved +
                ", startDistance=" + enemyPathStartDistance + ", distance=" + distance
            );
        }
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

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();

    @JSBody(params = {"id", "team"}, script = "document.documentElement.setAttribute('data-mindustry-enemy-path-smoke','spawned'); document.documentElement.setAttribute('data-mindustry-enemy-path-source','stock-ground-ai-flow-field'); document.documentElement.setAttribute('data-mindustry-enemy-path-unit','dagger'); document.documentElement.setAttribute('data-mindustry-enemy-path-controller','GroundAI'); document.documentElement.setAttribute('data-mindustry-enemy-path-field','core'); document.documentElement.setAttribute('data-mindustry-enemy-path-unit-id',String(id)); document.documentElement.setAttribute('data-mindustry-enemy-path-team',team);")
    private static native void markEnemyPathStarted(int id, String team);

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-enemy-path-smoke','moved'); document.documentElement.setAttribute('data-mindustry-enemy-path-frames',String(frames));")
    private static native void markEnemyPathMoved(int frames);

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

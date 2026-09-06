package mindustry.web;

import arc.*;
import arc.files.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.Map;
import org.teavm.jso.JSBody;

import java.io.*;

import static mindustry.Vars.*;

/**
 * CI gate for the first real packaged Mindustry .msav world on Web.
 *
 * Unlike the deterministic generated 8x8 smoke, this path reads a pinned builtin map
 * through BrowserFi -> InflaterInputStream -> MapIO metadata -> SaveIO/World.loadMap,
 * applies local sandbox rules, crosses the real WorldLoadEvent graph, binds a vanilla
 * core-spawned alpha to the local player, and executes three separate browser frames in
 * the production Logic -> Control -> Renderer -> UI order.
 */
public final class BrowserBuiltinMapRuntime{
    private static final String mapPath = "maps/default/maze.msav";
    private static final int targetFrames = 3;

    private static boolean active;
    private static boolean complete;
    private static Map map;
    private static Unit unit;
    private static int frames;
    private static long startUpdateId;

    private BrowserBuiltinMapRuntime(){}

    public static void begin(){
        if(active || complete) return;
        if(!state.isMenu() || world == null || logic == null || control == null || renderer == null || ui == null
        || maps == null || player == null || pathfinder == null || controlPath == null){
            throw new IllegalStateException("Builtin map Web smoke requires the complete local client/map substrate");
        }
        if(netServer != null || netClient != null || net.active()){
            throw new IllegalStateException("Builtin map Web smoke must remain permanent local single-player");
        }

        Fi file = Core.files.internal(mapPath);
        if(file == null || !file.exists() || file.length() <= 0L){
            throw new IllegalStateException("Packaged builtin map is unavailable through BrowserFi: " + mapPath);
        }

        try{
            map = MapIO.createMap(file, false);
        }catch(IOException error){
            throw new IllegalStateException("MapIO failed to parse packaged builtin map metadata", error);
        }

        if(map.width <= 8 || map.height <= 8 || map.name() == null || map.name().isEmpty()){
            throw new IllegalStateException("Packaged builtin map metadata is invalid: " + map.width + "x" + map.height);
        }
        markMetadata(mapPath, map.plainName(), map.width, map.height, map.version, map.build);

        // Mirror the gameplay-critical half of Control.playMap without UI.loadfrag or
        // autosave, which belong to later UI/save-selection milestones.
        Rules rules = map.applyRules(Gamemode.sandbox);
        rules.defaultTeam = Team.sharded;
        rules.waveTeam = Team.crux;
        rules.pvp = false;
        rules.waves = false;
        rules.waveTimer = false;
        rules.fog = false;
        rules.staticFog = false;
        rules.canGameOver = false;
        rules.attackMode = false;
        rules.weather.clear();
        rules.sector = null;
        rules.editor = false;

        logic.reset();
        world.loadMap(map, rules);
        if(world.isInvalidMap()){
            throw new IllegalStateException("Stock World.loadMap rejected packaged builtin map: " + mapPath);
        }

        Rules loadedRules = state.rules;
        rules.retainContentFields(loadedRules);
        state.rules = rules;
        state.rules.sector = null;
        state.rules.editor = false;

        if(state.map != map || world.width() != map.width || world.height() != map.height){
            throw new IllegalStateException(
                "Stock World.loadMap dimensions/state mismatch: map=" + map.width + "x" + map.height
                + ", world=" + world.width() + "x" + world.height()
            );
        }
        if(controlPath == null || !controlPath.webActive()){
            throw new IllegalStateException("Builtin WorldLoadEvent did not activate browser ControlPathfinder");
        }

        player.team(state.rules.defaultTeam);
        Building core = player.bestCore();
        if(core == null){
            throw new IllegalStateException("Packaged builtin map has no local default-team core");
        }

        unit = UnitTypes.alpha.create(state.rules.defaultTeam);
        unit.set(core.x, core.y);
        player.set(core);
        player.unit(unit);
        unit.spawnedByCore(true);
        unit.add();
        Core.camera.position.set(core);

        if(player.unit() != unit || !unit.spawnedByCore() || !unit.isAdded() || !unit.isValid() || unit.controller() != player){
            throw new IllegalStateException("Builtin map alpha/player binding failed before Web play");
        }

        frames = 0;
        startUpdateId = state.updateId;
        logic.play();
        Events.fire(Trigger.newGame);

        if(!state.isPlaying() || state.map != map || player.unit() != unit || !player.isAdded()){
            throw new IllegalStateException("Builtin map Logic.play/Trigger.newGame transition failed on Web");
        }

        active = true;
        markLoaded(map.plainName(), world.width(), world.height(), core.tileX(), core.tileY(), startUpdateId, unit.id);
    }

    public static void updateFrame(){
        if(!active || map == null || unit == null || !state.isPlaying()){
            throw new IllegalStateException("Builtin map Web frame ran without an active playing map");
        }

        long beforeUpdateId = state.updateId;

        logic.updateWebPlayingCore();
        assertMapState("logic");

        pathfinder.updateWeb();
        controlPath.updateWeb();
        assertMapState("pathfinding");

        control.update();
        assertMapState("control");

        renderer.update();
        assertMapState("renderer");

        ui.update();
        assertMapState("ui");

        if(state.updateId != beforeUpdateId + 1L){
            throw new IllegalStateException(
                "Builtin map frame advanced GameState updateId incorrectly: before=" + beforeUpdateId
                + ", after=" + state.updateId
            );
        }

        frames++;
        if(state.updateId != startUpdateId + frames){
            throw new IllegalStateException(
                "Builtin map update clock drifted: start=" + startUpdateId + ", frames=" + frames
                + ", current=" + state.updateId
            );
        }

        markFrame(frames, state.updateId, unit.id);
        if(frames >= targetFrames){
            restoreMenu();
        }
    }

    private static void assertMapState(String phase){
        if(state.map != map || world.width() != map.width || world.height() != map.height){
            throw new IllegalStateException("Builtin map world changed during " + phase);
        }
        if(player.unit() != unit || !unit.isAdded() || !unit.isValid() || unit.controller() != player){
            throw new IllegalStateException(
                "Builtin map player unit became invalid during " + phase
                + ": added=" + unit.isAdded() + ", valid=" + unit.isValid()
                + ", controllerIsPlayer=" + (unit.controller() == player)
            );
        }
    }

    private static void restoreMenu(){
        long finalUpdateId = state.updateId;
        unit.remove();
        player.clearUnit();
        state.set(State.menu);

        if(!state.isMenu() || player.unit() != null || state.map != map){
            throw new IllegalStateException("Builtin map Web smoke failed to restore stable menu state");
        }

        active = false;
        complete = true;
        markStable(frames, finalUpdateId, map.plainName(), world.width(), world.height());
        unit = null;
    }

    public static boolean active(){
        return active;
    }

    public static boolean complete(){
        return complete;
    }

    @JSBody(params = {"path", "name", "width", "height", "version", "build"}, script = "document.documentElement.setAttribute('data-mindustry-builtin-map', 'metadata-ready'); document.documentElement.setAttribute('data-mindustry-builtin-map-path', path); document.documentElement.setAttribute('data-mindustry-builtin-map-name', name); document.documentElement.setAttribute('data-mindustry-builtin-map-width', String(width)); document.documentElement.setAttribute('data-mindustry-builtin-map-height', String(height)); document.documentElement.setAttribute('data-mindustry-builtin-map-version', String(version)); document.documentElement.setAttribute('data-mindustry-builtin-map-build', String(build));")
    private static native void markMetadata(String path, String name, int width, int height, int version, int build);

    @JSBody(params = {"name", "width", "height", "coreX", "coreY", "updateId", "unitId"}, script = "document.documentElement.setAttribute('data-mindustry-builtin-map', 'loaded'); document.documentElement.setAttribute('data-mindustry-builtin-map-name', name); document.documentElement.setAttribute('data-mindustry-builtin-map-size', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-builtin-map-core', String(coreX) + ',' + String(coreY)); document.documentElement.setAttribute('data-mindustry-builtin-map-start-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-builtin-map-unit-id', String(unitId)); document.documentElement.setAttribute('data-mindustry-builtin-map-loop', 'playing'); document.documentElement.setAttribute('data-mindustry-builtin-map-target-frames', '3'); document.documentElement.setAttribute('data-mindustry-builtin-map-module-order', 'logic-control-renderer-ui');")
    private static native void markLoaded(String name, int width, int height, int coreX, int coreY, long updateId, int unitId);

    @JSBody(params = {"frames", "updateId", "unitId"}, script = "document.documentElement.setAttribute('data-mindustry-builtin-map-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-builtin-map-frame-index', String(frames)); document.documentElement.setAttribute('data-mindustry-builtin-map-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-builtin-map-unit-id', String(unitId));")
    private static native void markFrame(int frames, long updateId, int unitId);

    @JSBody(params = {"frames", "updateId", "name", "width", "height"}, script = "document.documentElement.setAttribute('data-mindustry-builtin-map', 'ready'); document.documentElement.setAttribute('data-mindustry-builtin-map-loop', 'stable'); document.documentElement.setAttribute('data-mindustry-builtin-map-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-builtin-map-frame-index', String(frames)); document.documentElement.setAttribute('data-mindustry-builtin-map-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-builtin-map-name', name); document.documentElement.setAttribute('data-mindustry-builtin-map-size', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-builtin-map-state', 'restored-menu');")
    private static native void markStable(int frames, long updateId, String name, int width, int height);
}

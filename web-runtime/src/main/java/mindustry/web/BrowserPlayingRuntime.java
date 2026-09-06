package mindustry.web;

import arc.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * One-shot browser gate for the first real Mindustry playing client frame.
 *
 * This does not fake gameplay by assigning GameState directly. It enters the already
 * loaded deterministic world through stock Logic.play(), which fires PlayEvent and the
 * normal Control player-registration path. A real vanilla alpha unit is attached to the
 * local Player through the generated entity/controller API, then the production playing
 * core and stock Control -> Renderer -> UI order run for one frame.
 *
 * The Logic entry point is a Web-specific extraction of the stock playing branch with
 * runtime assertions that fog/waves/weather/campaign/team AI are disabled. This keeps
 * those impossible optional branches out of TeaVM reachability while preserving the real
 * state clock, team stats, GlobalVars, entity physics/update and gameplay events.
 *
 * The smoke deliberately restores menu state afterward. Continuous playing remains the
 * next milestone; keeping this one-shot makes failures attributable while the remaining
 * pause/chat/minimap dialog controls are still intentionally deferred on Web/Yandex.
 */
public final class BrowserPlayingRuntime{
    private static boolean complete;

    private BrowserPlayingRuntime(){}

    public static void runOneFrame(){
        if(complete) return;
        if(state == null || logic == null || control == null || renderer == null || ui == null
        || world == null || player == null || pathfinder == null || controlPath == null){
            throw new IllegalStateException("Browser playing smoke requires the complete local client substrate");
        }
        if(!state.isMenu() || world.width() != 8 || world.height() != 8){
            throw new IllegalStateException("Browser playing smoke requires the real loaded 8x8 menu world");
        }
        if(netServer != null || netClient != null || net.active()){
            throw new IllegalStateException("Browser playing smoke must remain permanent local single-player");
        }

        state.rules.defaultTeam = Team.sharded;
        state.rules.waveTeam = Team.crux;
        state.rules.pvp = false;
        state.rules.waves = false;
        state.rules.waveTimer = false;
        state.rules.fog = false;
        state.rules.staticFog = false;
        state.rules.canGameOver = false;
        state.rules.attackMode = false;
        state.rules.weather.clear();

        Unit unit = UnitTypes.alpha.create(Team.sharded);
        float x = world.unitWidth() / 2f;
        float y = world.unitHeight() / 2f;
        unit.set(x, y);
        player.team(Team.sharded);
        player.set(x, y);
        player.unit(unit);
        // CoreBlock player spawning marks the controlled unit as core-spawned before
        // adding it. This is semantically important: player core units are exempt from
        // the normal unit cap, while this deterministic smoke world intentionally has no
        // core and therefore keeps Rules.unitCap at its stock zero value.
        unit.spawnedByCore(true);
        unit.add();
        Core.camera.position.set(unit);

        if(player.unit() != unit || unit.type != UnitTypes.alpha || unit.team() != Team.sharded
        || !unit.spawnedByCore() || !unit.isAdded() || !unit.isValid() || unit.controller() != player){
            throw new IllegalStateException("Vanilla core-spawned alpha/player controller binding failed before Web playing frame");
        }

        long beforeUpdateId = state.updateId;
        markPhase("play-event");
        logic.play();
        if(!state.isPlaying() || !player.isAdded() || player.unit() != unit){
            throw new IllegalStateException("Stock Logic.play/PlayEvent failed Web playing transition");
        }

        markPhase("logic");
        logic.updateWebPlayingCore();
        markPhase("logic-ready");
        assertOwnership("logic", unit);

        // JVM worker schedulers are replaced by explicit browser-frame steps; the
        // underlying Pathfinder and ControlPathfinder algorithms remain stock.
        pathfinder.updateWeb();
        controlPath.updateWeb();
        assertOwnership("pathfinding", unit);

        // Diagnostic preflight: execute the ordinary playing-only Control components
        // individually so a TeaVM NPE reports an exact stock subphase. This is temporary
        // instrumentation; the authoritative production call remains control.update().
        markPhase("control-input-state");
        control.input.updateState();
        markPhase("control-input-state-ready");
        assertOwnership("control-input-state", unit);

        markPhase("control-sound");
        control.sound.update();
        markPhase("control-sound-ready");
        assertOwnership("control-sound", unit);

        markPhase("control-input");
        control.input.update();
        markPhase("control-input-ready");
        assertOwnership("control-input", unit);

        markPhase("control-quadtree");
        control.input.updateSelectQuadtree();
        markPhase("control-quadtree-ready");
        assertOwnership("control-quadtree", unit);

        markPhase("control-indicators");
        control.indicators.update();
        markPhase("control-indicators-ready");
        assertOwnership("control-indicators", unit);

        markPhase("control-stock");
        control.update();
        markPhase("control-ready");
        assertOwnership("control", unit);

        // Temporary renderer phase hook narrows the first world-render failure without
        // duplicating Renderer.update(). The hook is removed with the Control preflight
        // once this one-shot playing gate is fully green.
        renderer.webPhaseHook = BrowserPlayingRuntime::markPhase;
        markPhase("renderer");
        renderer.update();
        renderer.webPhaseHook = null;
        markPhase("renderer-ready");
        assertOwnership("renderer", unit);

        markPhase("ui");
        ui.update();
        markPhase("ui-ready");
        assertOwnership("ui", unit);

        if(!state.isPlaying()){
            throw new IllegalStateException("Real Web playing client frame unexpectedly left playing state");
        }
        if(state.updateId <= beforeUpdateId){
            throw new IllegalStateException("Real Web playing client frame did not advance GameState updateId: before=" + beforeUpdateId + ", after=" + state.updateId);
        }

        markReady(state.updateId, unit.id, unit.type.name);

        unit.remove();
        player.clearUnit();
        state.set(State.menu);
        if(!state.isMenu() || player.unit() != null){
            throw new IllegalStateException("Browser playing smoke failed to restore stable menu state");
        }

        complete = true;
        markRestored();
    }

    private static void assertOwnership(String phase, Unit expected){
        Unit actual = player.unit();
        if(actual != expected){
            throw new IllegalStateException(
                "Web playing ownership changed during " + phase
                + ": actual=" + (actual == null ? "null" : actual.type.name + "#" + actual.id)
                + ", expected=" + expected.type.name + "#" + expected.id
                + ", expectedAdded=" + expected.isAdded()
                + ", expectedValid=" + expected.isValid()
                + ", expectedControllerIsPlayer=" + (expected.controller() == player)
                + ", playerAdded=" + player.isAdded()
            );
        }
        if(!expected.isAdded() || !expected.isValid() || expected.controller() != player){
            throw new IllegalStateException(
                "Web playing unit became invalid during " + phase
                + ": added=" + expected.isAdded()
                + ", valid=" + expected.isValid()
                + ", controllerIsPlayer=" + (expected.controller() == player)
            );
        }
    }

    public static boolean complete(){
        return complete;
    }

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-playing-phase', phase);")
    private static native void markPhase(String phase);

    @JSBody(params = {"updateId", "unitId", "unitType"}, script = "document.documentElement.setAttribute('data-mindustry-playing-frame', 'ready'); document.documentElement.setAttribute('data-mindustry-playing-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-playing-unit-id', String(unitId)); document.documentElement.setAttribute('data-mindustry-playing-unit', unitType); document.documentElement.setAttribute('data-mindustry-playing-module-order', 'logic-control-renderer-ui');")
    private static native void markReady(long updateId, int unitId, String unitType);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-playing-state', 'restored-menu');")
    private static native void markRestored();
}

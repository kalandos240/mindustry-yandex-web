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
 * local Player through the generated entity/controller API, then the stock client module
 * order runs for one playing frame.
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
        unit.add();
        Core.camera.position.set(unit);

        if(player.unit() != unit || unit.type != UnitTypes.alpha || unit.team() != Team.sharded || !unit.isAdded()){
            throw new IllegalStateException("Vanilla alpha/player controller binding failed before Web playing frame");
        }

        long beforeUpdateId = state.updateId;
        markPhase("play-event");
        logic.play();
        if(!state.isPlaying() || !player.isAdded() || player.unit() != unit){
            throw new IllegalStateException("Stock Logic.play/PlayEvent failed Web playing transition");
        }

        markPhase("logic");
        logic.update();
        markPhase("logic-ready");

        // JVM worker schedulers are replaced by explicit browser-frame steps; the
        // underlying Pathfinder and ControlPathfinder algorithms remain stock.
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

        if(!state.isPlaying() || state.updateId <= beforeUpdateId || player.unit() != unit || !unit.isAdded()){
            throw new IllegalStateException("Real Web playing client frame did not advance stock game state/entity ownership");
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

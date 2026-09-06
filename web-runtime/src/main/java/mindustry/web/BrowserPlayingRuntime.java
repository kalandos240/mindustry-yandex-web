package mindustry.web;

import arc.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.gen.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Browser gate for a short continuous real Mindustry playing session.
 *
 * begin() performs the stock Logic.play()/PlayEvent transition and leaves the real game
 * in State.playing. updateFrame() is then called by BrowserGameplayRuntime on separate
 * browser application frames, executing the production Logic -> Control -> Renderer -> UI
 * order exactly once per frame. This proves that gameplay state, player ownership and the
 * entity graph remain valid across requestAnimationFrame boundaries instead of only for a
 * one-shot call stack.
 *
 * The deterministic CI session runs three consecutive playing frames and then restores
 * menu state. Full user-controlled continuous play/HUD navigation remains the next UI
 * milestone; this gate deliberately keeps the same optional gameplay subsystems disabled
 * as updateWebPlayingCore().
 */
public final class BrowserPlayingRuntime{
    private static final int targetFrames = 3;

    private static boolean active;
    private static boolean complete;
    private static Unit unit;
    private static int frames;
    private static long startUpdateId;

    private BrowserPlayingRuntime(){}

    /** Enter real playing state and keep it active for subsequent browser frames. */
    public static void begin(){
        if(active || complete) return;
        if(state == null || logic == null || control == null || renderer == null || ui == null
        || world == null || player == null || pathfinder == null || controlPath == null){
            throw new IllegalStateException("Browser continuous playing smoke requires the complete local client substrate");
        }
        if(!state.isMenu() || world.width() != 8 || world.height() != 8){
            throw new IllegalStateException("Browser continuous playing smoke requires the real loaded 8x8 menu world");
        }
        if(netServer != null || netClient != null || net.active()){
            throw new IllegalStateException("Browser continuous playing smoke must remain permanent local single-player");
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

        unit = UnitTypes.alpha.create(Team.sharded);
        float x = world.unitWidth() / 2f;
        float y = world.unitHeight() / 2f;
        unit.set(x, y);
        player.team(Team.sharded);
        player.set(x, y);
        player.unit(unit);

        // Match stock CoreBlock player spawning. Core-spawned player units are exempt
        // from the normal unit cap; the deterministic smoke world intentionally has no
        // core and therefore keeps Rules.unitCap at its stock zero value.
        unit.spawnedByCore(true);
        unit.add();
        Core.camera.position.set(unit);

        if(player.unit() != unit || unit.type != UnitTypes.alpha || unit.team() != Team.sharded
        || !unit.spawnedByCore() || !unit.isAdded() || !unit.isValid() || unit.controller() != player){
            throw new IllegalStateException("Vanilla core-spawned alpha/player binding failed before continuous Web play");
        }

        frames = 0;
        startUpdateId = state.updateId;
        markPhase("play-event");
        logic.play();
        if(!state.isPlaying() || !player.isAdded() || player.unit() != unit){
            throw new IllegalStateException("Stock Logic.play/PlayEvent failed continuous Web playing transition");
        }

        active = true;
        markStarted(startUpdateId, unit.id, unit.type.name, targetFrames);
    }

    /** Execute exactly one production client frame while the smoke remains in playing. */
    public static void updateFrame(){
        if(!active || unit == null){
            throw new IllegalStateException("Continuous Web playing frame ran without an active session");
        }
        if(!state.isPlaying()){
            throw new IllegalStateException("Continuous Web playing session unexpectedly left playing state before its frame");
        }

        long beforeUpdateId = state.updateId;

        markPhase("logic");
        logic.updateWebPlayingCore();
        markPhase("logic-ready");
        assertOwnership("logic", unit);

        // JVM worker schedulers are replaced by explicit browser-frame steps; the
        // underlying Pathfinder and ControlPathfinder algorithms remain stock.
        pathfinder.updateWeb();
        controlPath.updateWeb();
        assertOwnership("pathfinding", unit);

        markPhase("control");
        control.update();
        markPhase("control-ready");
        assertOwnership("control", unit);

        markPhase("renderer");
        renderer.update();
        markPhase("renderer-ready");
        assertOwnership("renderer", unit);

        markPhase("ui");
        ui.update();
        markPhase("ui-ready");
        assertOwnership("ui", unit);

        if(!state.isPlaying()){
            throw new IllegalStateException("Continuous Web playing client frame unexpectedly left playing state");
        }
        if(state.updateId != beforeUpdateId + 1L){
            throw new IllegalStateException(
                "Continuous Web playing frame advanced GameState updateId incorrectly: before="
                + beforeUpdateId + ", after=" + state.updateId
            );
        }

        frames++;
        if(state.updateId != startUpdateId + frames){
            throw new IllegalStateException(
                "Continuous Web playing update clock drifted across frames: start=" + startUpdateId
                + ", frames=" + frames + ", current=" + state.updateId
            );
        }

        markFrame(state.updateId, unit.id, unit.type.name, frames);
        if(frames == 1){
            markLive(frames);
        }

        if(frames >= targetFrames){
            long finalUpdateId = state.updateId;
            markStable(frames, finalUpdateId);
            restoreMenu(finalUpdateId);
        }
    }

    private static void restoreMenu(long finalUpdateId){
        unit.remove();
        player.clearUnit();
        state.set(State.menu);
        if(!state.isMenu() || player.unit() != null){
            throw new IllegalStateException("Continuous Web playing smoke failed to restore stable menu state");
        }

        active = false;
        complete = true;
        unit = null;
        markRestored(frames, finalUpdateId);
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

    public static boolean active(){
        return active;
    }

    public static boolean complete(){
        return complete;
    }

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-playing-phase', phase);")
    private static native void markPhase(String phase);

    @JSBody(params = {"updateId", "unitId", "unitType", "target"}, script = "document.documentElement.setAttribute('data-mindustry-playing-loop', 'starting'); document.documentElement.setAttribute('data-mindustry-playing-frame', 'waiting'); document.documentElement.setAttribute('data-mindustry-playing-start-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-playing-unit-id', String(unitId)); document.documentElement.setAttribute('data-mindustry-playing-unit', unitType); document.documentElement.setAttribute('data-mindustry-playing-target-frames', String(target)); document.documentElement.setAttribute('data-mindustry-playing-module-order', 'logic-control-renderer-ui'); document.documentElement.setAttribute('data-mindustry-playing-state', 'playing');")
    private static native void markStarted(long updateId, int unitId, String unitType, int target);

    @JSBody(params = {"updateId", "unitId", "unitType", "frame"}, script = "document.documentElement.setAttribute('data-mindustry-playing-frame', 'ready'); document.documentElement.setAttribute('data-mindustry-playing-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-playing-unit-id', String(unitId)); document.documentElement.setAttribute('data-mindustry-playing-unit', unitType); document.documentElement.setAttribute('data-mindustry-playing-frame-index', String(frame)); document.documentElement.setAttribute('data-mindustry-playing-module-order', 'logic-control-renderer-ui');")
    private static native void markFrame(long updateId, int unitId, String unitType, int frame);

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-playing-loop', 'live'); document.documentElement.setAttribute('data-mindustry-playing-frames', String(frames));")
    private static native void markLive(int frames);

    @JSBody(params = {"frames", "updateId"}, script = "document.documentElement.setAttribute('data-mindustry-playing-loop', 'stable'); document.documentElement.setAttribute('data-mindustry-playing-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-playing-update-id', String(updateId));")
    private static native void markStable(int frames, long updateId);

    @JSBody(params = {"frames", "updateId"}, script = "document.documentElement.setAttribute('data-mindustry-playing-state', 'restored-menu'); document.documentElement.setAttribute('data-mindustry-playing-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-playing-update-id', String(updateId));")
    private static native void markRestored(int frames, long updateId);
}

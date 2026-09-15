package mindustry.web;

import mindustry.gen.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only proof that browser DOM keyboard events reach stock desktop gameplay input.
 *
 * No unit position, velocity, controller or binding is mutated by this class. Under the
 * explicit mindustryPlayerInputSmoke query it dispatches a normal DOM KeyD keydown/up;
 * BrowserInputBridge -> WebInput -> Arc bindings -> DesktopInput must move the real local
 * player unit. Production launches never enable this probe.
 */
public final class BrowserPlayerInputSmoke{
    private static final int maxSpawnFrames = 900;
    private static final int maxHeldFrames = 180;
    private static final float minPositiveX = 0.10f;

    private static boolean queryChecked;
    private static boolean enabled;
    private static boolean armed;
    private static boolean completed;
    private static boolean keyDown;
    private static int spawnFrames;
    private static int heldFrames;
    private static int unitId = -1;
    private static float startX;
    private static float startY;

    private BrowserPlayerInputSmoke(){}

    /** Called once after each normal application frame by the staged Web build. */
    public static void update(){
        if(!enabled() || completed) return;

        if(state == null || player == null || !BrowserLocalMapRuntime.active() || !state.isPlaying()){
            releaseKey();
            return;
        }

        Unit unit = player.unit();
        if(unit == null || !unit.isAdded() || !unit.isValid()){
            spawnFrames++;
            markWaiting(spawnFrames);
            if(spawnFrames >= maxSpawnFrames){
                throw new IllegalStateException("Player-input smoke never received a real local player unit");
            }
            return;
        }

        if(!armed){
            armed = true;
            unitId = unit.id;
            startX = unit.x;
            startY = unit.y;
            markReady(unitId, unit.type.name, startX, startY);

            // This enters the exact same window keydown listener as a physical keyboard.
            // The queued Arc event is intentionally consumed on the next animation frame.
            dispatchMovementKey(true);
            keyDown = true;
            markKeyState("down");
            return;
        }

        if(unit.id != unitId || player.unit() != unit){
            releaseKey();
            throw new IllegalStateException("Player-input smoke changed controlled unit while KeyD was held");
        }

        heldFrames++;
        float dx = unit.x - startX;
        float dy = unit.y - startY;
        markProgress(heldFrames, unit.x, unit.y, dx, dy);

        // D is the positive side of stock Binding.moveX. Requiring +X instead of only
        // nonzero displacement prevents unrelated spawn settling from producing a pass.
        if(dx > minPositiveX){
            releaseKey();
            completed = true;
            markMoved(unitId, unit.type.name, startX, startY, unit.x, unit.y, dx, dy, heldFrames);
            return;
        }

        if(heldFrames >= maxHeldFrames){
            releaseKey();
            throw new IllegalStateException(
                "DOM KeyD reached the browser but stock DesktopInput did not move the local player unit: dx=" + dx + ", dy=" + dy
            );
        }
    }

    private static boolean enabled(){
        if(!queryChecked){
            queryChecked = true;
            enabled = requested();
            if(enabled) markRequested();
        }
        return enabled;
    }

    private static void releaseKey(){
        if(!keyDown) return;
        dispatchMovementKey(false);
        keyDown = false;
        markKeyState("up");
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPlayerInputSmoke') === '1';")
    private static native boolean requested();

    @JSBody(params = {"down"}, script = """
        const type = down ? 'keydown' : 'keyup';
        const event = new KeyboardEvent(type, {
            code: 'KeyD',
            key: 'd',
            bubbles: true,
            cancelable: true,
            repeat: false
        });
        window.dispatchEvent(event);
        """)
    private static native void dispatchMovementKey(boolean down);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-player-input-smoke', 'requested'); document.documentElement.setAttribute('data-mindustry-player-input-source', 'dom-keyboard-event'); document.documentElement.setAttribute('data-mindustry-player-input-key', 'KeyD');")
    private static native void markRequested();

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-player-input-smoke', 'waiting-unit'); document.documentElement.setAttribute('data-mindustry-player-input-wait-frames', String(frames));")
    private static native void markWaiting(int frames);

    @JSBody(params = {"id", "type", "x", "y"}, script = "document.documentElement.setAttribute('data-mindustry-player-input-smoke', 'ready'); document.documentElement.setAttribute('data-mindustry-player-input-unit-id', String(id)); document.documentElement.setAttribute('data-mindustry-player-input-unit', type); document.documentElement.setAttribute('data-mindustry-player-input-start-x', String(x)); document.documentElement.setAttribute('data-mindustry-player-input-start-y', String(y));")
    private static native void markReady(int id, String type, float x, float y);

    @JSBody(params = {"state"}, script = "document.documentElement.setAttribute('data-mindustry-player-input-key-state', state);")
    private static native void markKeyState(String state);

    @JSBody(params = {"frames", "x", "y", "dx", "dy"}, script = "document.documentElement.setAttribute('data-mindustry-player-input-held-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-player-input-current-x', String(x)); document.documentElement.setAttribute('data-mindustry-player-input-current-y', String(y)); document.documentElement.setAttribute('data-mindustry-player-input-dx', String(dx)); document.documentElement.setAttribute('data-mindustry-player-input-dy', String(dy));")
    private static native void markProgress(int frames, float x, float y, float dx, float dy);

    @JSBody(params = {"id", "type", "sx", "sy", "x", "y", "dx", "dy", "frames"}, script = "document.documentElement.setAttribute('data-mindustry-player-input-smoke', 'moved'); document.documentElement.setAttribute('data-mindustry-player-input-source', 'dom-keyboard-event'); document.documentElement.setAttribute('data-mindustry-player-input-key', 'KeyD'); document.documentElement.setAttribute('data-mindustry-player-input-key-state', 'up'); document.documentElement.setAttribute('data-mindustry-player-input-unit-id', String(id)); document.documentElement.setAttribute('data-mindustry-player-input-unit', type); document.documentElement.setAttribute('data-mindustry-player-input-start-x', String(sx)); document.documentElement.setAttribute('data-mindustry-player-input-start-y', String(sy)); document.documentElement.setAttribute('data-mindustry-player-input-end-x', String(x)); document.documentElement.setAttribute('data-mindustry-player-input-end-y', String(y)); document.documentElement.setAttribute('data-mindustry-player-input-dx', String(dx)); document.documentElement.setAttribute('data-mindustry-player-input-dy', String(dy)); document.documentElement.setAttribute('data-mindustry-player-input-held-frames', String(frames));")
    private static native void markMoved(int id, String type, float sx, float sy, float x, float y, float dx, float dy, int frames);
}

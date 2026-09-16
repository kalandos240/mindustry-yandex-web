package mindustry.web;

import arc.*;
import mindustry.gen.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only proof that a real browser pointer drives stock desktop aiming and firing.
 *
 * This class never mutates player/unit combat state directly. It emits ordinary DOM
 * PointerEvents at the canvas and then observes BrowserInputBridge -> WebInput ->
 * DesktopInput -> Unit weapon state. Success additionally requires a real Bullet owned
 * by the local player unit to enter Groups.bullet.
 */
public final class BrowserPlayerCombatSmoke{
    private static final int maxSpawnFrames = 900;
    private static final int maxAimFrames = 90;
    private static final int maxFireFrames = 240;

    private static boolean queryChecked;
    private static boolean enabled;
    private static boolean completed;
    private static boolean pointerDown;
    private static int stage;
    private static int spawnFrames;
    private static int aimFrames;
    private static int fireFrames;
    private static int unitId = -1;
    private static int startOwnedBullets;
    private static float startAimX, startAimY;

    private BrowserPlayerCombatSmoke(){}

    /** Called after each normal application frame; inert unless explicitly requested. */
    public static void update(){
        if(!enabled() || completed) return;

        if(state == null || player == null || !BrowserLocalMapRuntime.active() || !state.isPlaying()){
            releasePointer();
            return;
        }

        Unit unit = player.unit();
        if(unit == null || !unit.isAdded() || !unit.isValid()){
            spawnFrames++;
            markWaiting(spawnFrames);
            if(spawnFrames >= maxSpawnFrames){
                throw new IllegalStateException("Player-combat smoke never received a real local player unit");
            }
            return;
        }

        if(unitId != -1 && (unit.id != unitId || player.unit() != unit)){
            releasePointer();
            throw new IllegalStateException("Player-combat smoke changed controlled unit during pointer test");
        }

        if(stage == 0){
            if(!unit.hasWeapons() || unit.mounts == null || unit.mounts.length == 0){
                throw new IllegalStateException("Local player unit has no weapon mounts for pointer firing smoke: " + unit.type.name);
            }

            unitId = unit.id;
            startAimX = unit.aimX();
            startAimY = unit.aimY();
            startOwnedBullets = ownedBullets(unit);

            dispatchPointer("pointermove", 0.78f, 0.42f, -1);
            stage = 1;
            markAiming(unitId, unit.type.name, startAimX, startAimY, startOwnedBullets);
            return;
        }

        if(stage == 1){
            aimFrames++;
            float mx = Core.input.mouseX();
            float my = Core.input.mouseY();
            float ax = unit.aimX();
            float ay = unit.aimY();
            markAimProgress(aimFrames, mx, my, ax, ay);

            boolean pointerReachedInput = mx > Core.graphics.getWidth() * 0.60f;
            boolean validAim = !Float.isNaN(ax) && !Float.isInfinite(ax) &&
                !Float.isNaN(ay) && !Float.isInfinite(ay) &&
                (Math.abs(ax - unit.x) > 1f || Math.abs(ay - unit.y) > 1f);

            if(pointerReachedInput && validAim){
                dispatchPointer("pointerdown", 0.78f, 0.42f, 0);
                pointerDown = true;
                stage = 2;
                markPointerDown(mx, my, ax, ay);
                return;
            }

            if(aimFrames >= maxAimFrames){
                throw new IllegalStateException(
                    "DOM pointermove did not reach stock player aiming: mouse=" + mx + "," + my +
                    " aim=" + ax + "," + ay + " unit=" + unit.x + "," + unit.y
                );
            }
            return;
        }

        fireFrames++;
        int owned = ownedBullets(unit);
        boolean mountShooting = false;
        for(var mount : unit.mounts){
            mountShooting |= mount.shoot;
        }
        markFireProgress(fireFrames, player.shooting, mountShooting, owned, unit.aimX(), unit.aimY());

        if(owned > startOwnedBullets){
            releasePointer();
            completed = true;
            markFired(
                unitId, unit.type.name, fireFrames, owned - startOwnedBullets,
                unit.aimX(), unit.aimY(), player.shooting, mountShooting
            );
            return;
        }

        if(fireFrames >= maxFireFrames){
            releasePointer();
            throw new IllegalStateException(
                "DOM mouse-left reached combat smoke but no local-unit Bullet was created: playerShooting=" +
                player.shooting + ", mountShooting=" + mountShooting + ", ownedBullets=" + owned +
                ", baseline=" + startOwnedBullets
            );
        }
    }

    private static int ownedBullets(Unit unit){
        return Groups.bullet.count(b -> b.owner == unit);
    }

    private static boolean enabled(){
        if(!queryChecked){
            queryChecked = true;
            enabled = requested();
            if(enabled) markRequested();
        }
        return enabled;
    }

    private static void releasePointer(){
        if(!pointerDown) return;
        dispatchPointer("pointerup", 0.78f, 0.42f, 0);
        pointerDown = false;
        markPointerState("up");
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPlayerCombatSmoke') === '1';")
    private static native boolean requested();

    @JSBody(params = {"type", "nx", "ny", "button"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('Mindustry canvas missing for combat smoke');
        const rect = canvas.getBoundingClientRect();
        const clientX = rect.left + rect.width * nx;
        const clientY = rect.top + rect.height * ny;
        const down = type === 'pointerdown';
        const event = new PointerEvent(type, {
            pointerId: 1,
            pointerType: 'mouse',
            isPrimary: true,
            clientX: clientX,
            clientY: clientY,
            button: button < 0 ? -1 : button,
            buttons: down ? 1 : 0,
            bubbles: true,
            cancelable: true
        });
        canvas.dispatchEvent(event);
        """)
    private static native void dispatchPointer(String type, float nx, float ny, int button);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-player-combat-smoke', 'requested'); document.documentElement.setAttribute('data-mindustry-player-combat-source', 'dom-pointer-event');")
    private static native void markRequested();

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-player-combat-smoke', 'waiting-unit'); document.documentElement.setAttribute('data-mindustry-player-combat-wait-frames', String(frames));")
    private static native void markWaiting(int frames);

    @JSBody(params = {"id", "type", "ax", "ay", "bullets"}, script = "document.documentElement.setAttribute('data-mindustry-player-combat-smoke', 'aiming'); document.documentElement.setAttribute('data-mindustry-player-combat-unit-id', String(id)); document.documentElement.setAttribute('data-mindustry-player-combat-unit', type); document.documentElement.setAttribute('data-mindustry-player-combat-start-aim-x', String(ax)); document.documentElement.setAttribute('data-mindustry-player-combat-start-aim-y', String(ay)); document.documentElement.setAttribute('data-mindustry-player-combat-start-bullets', String(bullets));")
    private static native void markAiming(int id, String type, float ax, float ay, int bullets);

    @JSBody(params = {"frames", "mx", "my", "ax", "ay"}, script = "document.documentElement.setAttribute('data-mindustry-player-combat-aim-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-player-combat-mouse-x', String(mx)); document.documentElement.setAttribute('data-mindustry-player-combat-mouse-y', String(my)); document.documentElement.setAttribute('data-mindustry-player-combat-aim-x', String(ax)); document.documentElement.setAttribute('data-mindustry-player-combat-aim-y', String(ay));")
    private static native void markAimProgress(int frames, float mx, float my, float ax, float ay);

    @JSBody(params = {"mx", "my", "ax", "ay"}, script = "document.documentElement.setAttribute('data-mindustry-player-combat-smoke', 'pointer-down'); document.documentElement.setAttribute('data-mindustry-player-combat-pointer-state', 'down'); document.documentElement.setAttribute('data-mindustry-player-combat-mouse-x', String(mx)); document.documentElement.setAttribute('data-mindustry-player-combat-mouse-y', String(my)); document.documentElement.setAttribute('data-mindustry-player-combat-aim-x', String(ax)); document.documentElement.setAttribute('data-mindustry-player-combat-aim-y', String(ay));")
    private static native void markPointerDown(float mx, float my, float ax, float ay);

    @JSBody(params = {"frames", "playerShoot", "mountShoot", "bullets", "ax", "ay"}, script = "document.documentElement.setAttribute('data-mindustry-player-combat-fire-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-player-combat-player-shooting', String(playerShoot)); document.documentElement.setAttribute('data-mindustry-player-combat-mount-shooting', String(mountShoot)); document.documentElement.setAttribute('data-mindustry-player-combat-owned-bullets', String(bullets)); document.documentElement.setAttribute('data-mindustry-player-combat-aim-x', String(ax)); document.documentElement.setAttribute('data-mindustry-player-combat-aim-y', String(ay));")
    private static native void markFireProgress(int frames, boolean playerShoot, boolean mountShoot, int bullets, float ax, float ay);

    @JSBody(params = {"state"}, script = "document.documentElement.setAttribute('data-mindustry-player-combat-pointer-state', state);")
    private static native void markPointerState(String state);

    @JSBody(params = {"id", "type", "frames", "bullets", "ax", "ay", "playerShoot", "mountShoot"}, script = "document.documentElement.setAttribute('data-mindustry-player-combat-smoke', 'fired'); document.documentElement.setAttribute('data-mindustry-player-combat-source', 'dom-pointer-event'); document.documentElement.setAttribute('data-mindustry-player-combat-pointer-state', 'up'); document.documentElement.setAttribute('data-mindustry-player-combat-unit-id', String(id)); document.documentElement.setAttribute('data-mindustry-player-combat-unit', type); document.documentElement.setAttribute('data-mindustry-player-combat-fire-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-player-combat-bullets-created', String(bullets)); document.documentElement.setAttribute('data-mindustry-player-combat-aim-x', String(ax)); document.documentElement.setAttribute('data-mindustry-player-combat-aim-y', String(ay)); document.documentElement.setAttribute('data-mindustry-player-combat-player-shooting-at-fire', String(playerShoot)); document.documentElement.setAttribute('data-mindustry-player-combat-mount-shooting-at-fire', String(mountShoot));")
    private static native void markFired(int id, String type, int frames, int bullets, float ax, float ay, boolean playerShoot, boolean mountShoot);
}

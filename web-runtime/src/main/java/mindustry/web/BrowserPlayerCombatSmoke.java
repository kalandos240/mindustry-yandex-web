package mindustry.web;

import arc.*;
import arc.math.geom.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.world.*;
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
    private static float targetNx = 0.5f, targetNy = 0.5f;

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
                throw new IllegalStateException("combat:no-player");
            }
            return;
        }

        if(unitId != -1 && (unit.id != unitId || player.unit() != unit)){
            releasePointer();
            throw new IllegalStateException("combat:unit-changed");
        }

        if(stage == 0){
            if(!unit.hasWeapons() || unit.mounts == null || unit.mounts.length == 0){
                throw new IllegalStateException("combat:no-weapons:" + unit.type.name);
            }

            unitId = unit.id;
            startAimX = unit.aimX();
            startAimY = unit.aimY();
            startOwnedBullets = ownedBullets(unit);

            chooseSafeTarget(unit);
            dispatchPointer("pointermove", targetNx, targetNy, -1);
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

            float expectedMouseX = Core.graphics.getWidth() * targetNx;
            float expectedMouseY = Core.graphics.getHeight() * (1f - targetNy);
            boolean pointerReachedInput = Math.abs(mx - expectedMouseX) <= 2f &&
                Math.abs(my - expectedMouseY) <= 2f;
            boolean validAim = !Float.isNaN(ax) && !Float.isInfinite(ax) &&
                !Float.isNaN(ay) && !Float.isInfinite(ay) &&
                (Math.abs(ax - unit.x) > 1f || Math.abs(ay - unit.y) > 1f);

            if(pointerReachedInput && validAim){
                dispatchPointer("pointerdown", targetNx, targetNy, 0);
                pointerDown = true;
                stage = 2;
                markPointerDown(mx, my, ax, ay);
                return;
            }

            if(aimFrames >= maxAimFrames){
                throw new IllegalStateException(
                    "combat:aim mouse=" + mx + "," + my +
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
                "combat:no-bullet ps=" +
                player.shooting + ", ms=" + mountShooting + ", ob=" + owned +
                ", base=" + startOwnedBullets
            );
        }
    }

    /**
     * Choose a visible gameplay point that stock DesktopInput cannot reinterpret as a
     * HUD click, building tap or mining action. Fixed screen coordinates are brittle:
     * Maze ores/buildings and Web HUD geometry can legitimately consume mouse-left.
     */
    private static void chooseSafeTarget(Unit unit){
        int width = Core.graphics.getWidth();
        int height = Core.graphics.getHeight();

        // Search the visible gameplay viewport rather than relying on one map-specific
        // coordinate. Stock desktop "tap player" range is 11 world units, so 18 leaves
        // a safety margin without incorrectly rejecting an entire zoomed-in corridor.
        int uiRejected = 0, blockedRejected = 0, mineRejected = 0, nearRejected = 0;
        for(int yi = 2; yi <= 8; yi++){
            float ny = yi / 10f;
            for(int xi = 2; xi <= 8; xi++){
                float nx = xi / 10f;
                float screenX = width * nx;
                float screenY = height * (1f - ny);

                if(Core.scene != null){
                    Vec2 stagePoint = Core.scene.screenToStageCoordinates(new Vec2(screenX, screenY));
                    if(Core.scene.hasMouse(stagePoint.x, stagePoint.y)){
                        uiRejected++;
                        continue;
                    }
                }

                Vec2 worldPoint = Core.camera.unproject(screenX, screenY);
                Tile tile = world.tileWorld(worldPoint.x, worldPoint.y);
                if(tile == null || tile.build != null || tile.block() != Blocks.air){
                    blockedRejected++;
                    continue;
                }
                if(tile.drop() != null){
                    mineRejected++;
                    continue;
                }

                float tx = tile.worldx(), ty = tile.worldy();
                float distance = unit.dst(tx, ty);
                if(distance < 18f){
                    nearRejected++;
                    continue;
                }

                targetNx = nx;
                targetNy = ny;
                markSafeTarget(tile.x, tile.y, screenX, screenY, distance);
                return;
            }
        }

        throw new IllegalStateException(
            "combat:no-target ui=" + uiRejected +
            " b=" + blockedRejected + " m=" + mineRejected + " n=" + nearRejected
        );
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
        dispatchPointer("pointerup", targetNx, targetNy, 0);
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

    @JSBody(params = {"x", "y", "sx", "sy", "distance"}, script = "document.documentElement.setAttribute('data-mindustry-player-combat-target', 'safe-floor'); document.documentElement.setAttribute('data-mindustry-player-combat-target-tile', String(x) + ',' + String(y)); document.documentElement.setAttribute('data-mindustry-player-combat-target-screen', String(Math.round(sx)) + ',' + String(Math.round(sy))); document.documentElement.setAttribute('data-mindustry-player-combat-target-distance', String(distance));")
    private static native void markSafeTarget(int x, int y, float sx, float sy, float distance);

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

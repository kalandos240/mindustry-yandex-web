package mindustry.web;

import arc.*;
import arc.math.geom.*;
import mindustry.game.*;
import mindustry.core.World;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only end-to-end proof for local player mining and item deposit.
 *
 * This probe never assigns mineTile, changes an ItemModule or invokes an InputHandler
 * transfer method directly. It emits ordinary DOM PointerEvents and observes stock
 * DesktopInput -> MinerComp -> player drag/drop -> transferInventory semantics.
 */
public final class BrowserPlayerMiningSmoke{
    private static final int maxSpawnFrames = 900;
    private static final int maxMineFrames = 900;
    private static final int maxAimFrames = 120;
    private static final int maxApproachFrames = 1800;
    private static final int maxDepositFrames = 180;

    private static boolean queryChecked;
    private static boolean enabled;
    private static boolean completed;
    private static boolean pointerDown;
    private static int stage;
    private static int spawnFrames;
    private static int mineFrames;
    private static int mineClickAttempts;
    private static int aimFrames;
    private static int approachFrames;
    private static int depositFrames;
    private static int targetX = -1, targetY = -1;
    private static int coreStartItems;
    private static int minedStack;
    private static float targetScreenX, targetScreenY;
    private static float playerScreenX, playerScreenY;
    private static float coreScreenX, coreScreenY;
    private static String movementKey;
    private static Item targetItem;
    private static Building core;

    private BrowserPlayerMiningSmoke(){}

    /** Called after each normal application frame; inert unless explicitly requested. */
    public static void update(){
        if(!enabled() || completed) return;

        if(state == null || player == null || !BrowserLocalMapRuntime.active() || !state.isPlaying()){
            releasePointer();
            return;
        }

        Unit unit = player.unit();
        if(unit == null || !unit.isAdded() || !unit.isValid()){
            if(++spawnFrames >= maxSpawnFrames){
                throw new IllegalStateException("mining:no-player");
            }
            return;
        }
        if(!unit.canMine()){
            throw new IllegalStateException("mining:cannot:" + unit.type.name);
        }

        if(stage == 0){
            Core.settings.put("smoothcamera", false);
            // This is an isolated CI profile. Keep the probe deterministic and verify
            // the normal single-click stock mining path instead of racing the optional
            // double-tap preference against a heavy TeaVM animation frame.
            Core.settings.put("doubletapmine", false);
            if(unit.stack.amount != 0){
                throw new IllegalStateException("mining:stack-not-empty");
            }
            core = unit.closestCore();
            if(core == null || core.items == null){
                throw new IllegalStateException("mining:no-core-items");
            }
            if(!findTarget(unit)){
                throw new IllegalStateException("mining:no-target");
            }

            coreStartItems = core.items.get(targetItem);
            stage = 1;
            markTarget(targetX, targetY);
            return;
        }

        if(stage == 1){
            Tile target = world.tile(targetX, targetY);
            if(target == null) throw new IllegalStateException("mining:target-lost-approach");

            // Approach to one tile so the ore projects near the free camera center.
            float safeRange = tilesize;
            if(!unit.within(target.worldx(), target.worldy(), safeRange)){
                moveToward(unit.x, unit.y, target.worldx(), target.worldy());
                if(++approachFrames >= maxApproachFrames){
                    stopMovement();
                    throw new IllegalStateException(
                        "mining:approach unit=" +
                        unit.x + "," + unit.y + " target=" + target.worldx() + "," + target.worldy()
                    );
                }
                return;
            }

            // Stop first, then give the normal camera-follow path a frame to settle.
            // Projecting the ore in the same observer callback as the final movement key-up
            // can leave the world point under the bottom-right build palette.
            stopMovement();
            aimFrames = 0;
            stage = 5;
            return;
        }

        if(stage == 5){
            Tile target = world.tile(targetX, targetY);
            if(target == null) throw new IllegalStateException("mining:target-lost-aim");

            Vec2 projected = Core.camera.project(new Vec2(target.worldx(), target.worldy()));
            if(sceneCovered(projected.x, projected.y)){
                if(++aimFrames >= maxAimFrames){
                    throw new IllegalStateException("mining:ui-covered");
                }
                return;
            }

            targetScreenX = projected.x;
            targetScreenY = projected.y;
            dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
            stage = 2;
            return;
        }

        if(stage == 2){
            Tile target = world.tile(targetX, targetY);
            if(target == null) throw new IllegalStateException("mining:target-lost-down");

            // Camera follow can still advance between the pointermove and the next input
            // update. Re-project until DOM input and the exact ore tile agree in one frame.
            Vec2 projected = Core.camera.project(new Vec2(target.worldx(), target.worldy()));
            if(sceneCovered(projected.x, projected.y)){
                stage = 5;
                if(++aimFrames >= maxAimFrames){
                    throw new IllegalStateException("mining:ui-moved");
                }
                return;
            }
            if(Math.abs(projected.x - targetScreenX) > 2f || Math.abs(projected.y - targetScreenY) > 2f){
                targetScreenX = projected.x;
                targetScreenY = projected.y;
                dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
                if(++aimFrames >= maxAimFrames){
                    throw new IllegalStateException("mining:projection");
                }
                return;
            }

            verifyWorldPointer(targetScreenX, targetScreenY, "mine target");
            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 0, true);
            pointerDown = true;
            stage = 3;
            return;
        }

        if(stage == 3){
            dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
            pointerDown = false;
            stage = 4;
            return;
        }

        if(stage == 4){
            // PointerEvents are queued after the application frame. Do not synthesize
            // an immediate second click: on a slow browser frame that can arrive before
            // the first stock click is consumed and toggle mining back off.
            stage = 6;
            return;
        }

        if(stage == 6){
            Tile target = world.tile(targetX, targetY);
            if(target == null) throw new IllegalStateException("mining:target-lost");
            if(unit.mineTile != target && unit.stack.amount == 0 && core.items.get(targetItem) == coreStartItems){
                mineFrames++;

                // DOM clicks are edge gestures. A slow headless TeaVM frame can leave
                // one down/up pair outside DesktopInput's consumption window. Retry a
                // few real pointer gestures only while stock MinerComp still has not
                // accepted the tile; never assign mineTile or grant items directly.
                if(mineFrames % 12 == 0 && mineClickAttempts < 3){
                    Vec2 projected = Core.camera.project(new Vec2(target.worldx(), target.worldy()));
                    if(!sceneCovered(projected.x, projected.y)){
                        targetScreenX = projected.x;
                        targetScreenY = projected.y;
                        dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
                        dispatchPointer("pointerdown", targetScreenX, targetScreenY, 0, true);
                        dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
                        mineClickAttempts++;
                        markMineRetry(mineClickAttempts);
                    }
                }

                if(mineFrames >= 60){
                    throw new IllegalStateException(
                        "mining:not-started:" +
                        (mineClickAttempts + 1) + " clicks"
                    );
                }
                return;
            }

            int coreNow = core.items.get(targetItem);
            if(coreNow > coreStartItems){
                completed = true;
                markAutoDeposited(targetItem.name, coreNow - coreStartItems);
                return;
            }

            if(unit.item() == targetItem && unit.stack.amount > 0){
                minedStack = unit.stack.amount;
                approachFrames = 0;
                stage = 7;
                return;
            }

            mineFrames++;
            if(mineFrames >= maxMineFrames){
                throw new IllegalStateException(
                    "mining:no-item i=" + targetItem.name +
                    ", s=" + unit.stack.amount + ", d=" + (coreNow - coreStartItems)
                );
            }
            return;
        }

        if(stage == 7){
            if(!player.within(core, itemTransferRange * 0.8f)){
                moveToward(unit.x, unit.y, core.x, core.y);
                if(++approachFrames >= maxApproachFrames){
                    stopMovement();
                    throw new IllegalStateException(
                        "mining:return-core"
                    );
                }
                return;
            }

            stopMovement();
            Vec2 up = Core.camera.project(new Vec2(unit.x, unit.y));
            Vec2 cp = Core.camera.project(new Vec2(core.x, core.y));
            playerScreenX = up.x;
            playerScreenY = up.y;
            coreScreenX = cp.x;
            coreScreenY = cp.y;
            dispatchPointer("pointermove", playerScreenX, playerScreenY, -1, false);
            stage = 8;
            return;
        }

        if(stage == 8){
            verifyWorldPointer(playerScreenX, playerScreenY, "player item drag source");
            dispatchPointer("pointerdown", playerScreenX, playerScreenY, 0, true);
            pointerDown = true;
            stage = 9;
            return;
        }

        if(stage == 9){
            if(!control.input.isDroppingItem()){
                if(++depositFrames >= maxDepositFrames){
                    releasePointer();
                    throw new IllegalStateException("mining:no-drop-mode");
                }
                return;
            }

            dispatchPointer("pointermove", coreScreenX, coreScreenY, 0, true);
            stage = 10;
            return;
        }

        if(stage == 10){
            verifyWorldPointer(coreScreenX, coreScreenY, "core deposit target");
            dispatchPointer("pointerup", coreScreenX, coreScreenY, 0, false);
            pointerDown = false;
            stage = 11;
            return;
        }

        int coreNow = core.items.get(targetItem);
        if(coreNow > coreStartItems && unit.stack.amount < minedStack){
            completed = true;
            markDeposited(targetItem.name, coreStartItems, coreNow, targetX, targetY);
            return;
        }

        depositFrames++;
        if(depositFrames >= maxDepositFrames){
            throw new IllegalStateException(
                "mining:no-deposit i=" + targetItem.name +
                ", s=" + unit.stack.amount + "/" + minedStack +
                ", c=" + coreNow + "/" + coreStartItems
            );
        }
    }

    private static boolean findTarget(Unit unit){
        if(Core.camera == null) return false;

        int ux = World.toTile(unit.x), uy = World.toTile(unit.y);
        int maxRadius = 96;
        boolean doubleTap = Core.settings.getBool("doubletapmine");

        for(int r = 1; r <= maxRadius; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    Tile tile = world.tile(ux + dx, uy + dy);
                    if(tile == null) continue;

                    Item item = unit.getMineResult(tile);
                    if(item == null || !unit.acceptsItem(item)) continue;
                    if(!doubleTap && tile.floor().playerUnmineable && tile.overlay().itemDrop == null) continue;
                    if(!doubleTap && tile.overlay().playerUnmineable && tile.overlay().itemDrop != null) continue;

                    targetX = tile.x;
                    targetY = tile.y;
                    targetItem = item;
                    Vec2 projected = Core.camera.project(new Vec2(tile.worldx(), tile.worldy()));
                    targetScreenX = projected.x;
                    targetScreenY = projected.y;
                    return true;
                }
            }
        }
        return false;
    }

    private static void moveToward(float fromX, float fromY, float toX, float toY){
        float dx = toX - fromX, dy = toY - fromY;
        String key;
        if(Math.abs(dx) > Math.abs(dy)){
            key = dx >= 0f ? "KeyD" : "KeyA";
        }else{
            key = dy >= 0f ? "KeyW" : "KeyS";
        }

        if(key.equals(movementKey)) return;
        stopMovement();
        movementKey = key;
        dispatchKey("keydown", key, key.equals("KeyD") ? "d" : key.equals("KeyA") ? "a" : key.equals("KeyW") ? "w" : "s");
    }

    private static void stopMovement(){
        if(movementKey == null) return;
        String key = movementKey;
        movementKey = null;
        dispatchKey("keyup", key, key.equals("KeyD") ? "d" : key.equals("KeyA") ? "a" : key.equals("KeyW") ? "w" : "s");
    }

    private static boolean sceneCovered(float x, float y){
        if(Core.scene == null) return false;
        Vec2 stagePoint = Core.scene.screenToStageCoordinates(new Vec2(x, y));
        return Core.scene.hasMouse(stagePoint.x, stagePoint.y);
    }

    private static void verifyWorldPointer(float x, float y, String label){
        if(sceneCovered(x, y)){
            throw new IllegalStateException(label + " ui-covered");
        }
        if(Math.abs(Core.input.mouseX() - x) > 4f || Math.abs(Core.input.mouseY() - y) > 4f){
            throw new IllegalStateException(
                "pointer-miss:" + label + ":in=" +
                Core.input.mouseX() + "," + Core.input.mouseY() + " exp=" + x + "," + y
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

    private static void releasePointer(){
        stopMovement();
        if(!pointerDown) return;
        dispatchPointer("pointerup", Core.input.mouseX(), Core.input.mouseY(), 0, false);
        pointerDown = false;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPlayerMiningSmoke') === '1';")
    private static native boolean requested();

    /** Stage coordinates use bottom-left origin, while DOM clientY uses top-left. */
    @JSBody(params = {"type", "sx", "sy", "button", "down"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('Mindustry canvas missing for mining smoke');
        const rect = canvas.getBoundingClientRect();
        const event = new PointerEvent(type, {
            pointerId: 1,
            pointerType: 'mouse',
            isPrimary: true,
            clientX: rect.left + sx,
            clientY: rect.top + rect.height - sy,
            button: button < 0 ? -1 : button,
            buttons: down ? 1 : 0,
            bubbles: true,
            cancelable: true
        });
        canvas.dispatchEvent(event);
        """)
    private static native void dispatchPointer(String type, float sx, float sy, int button, boolean down);

    @JSBody(params = {"type", "code", "key"}, script = """
        window.dispatchEvent(new KeyboardEvent(type, {
            code: code,
            key: key,
            bubbles: true,
            cancelable: true
        }));
        """)
    private static native void dispatchKey(String type, String code, String key);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'requested'); document.documentElement.setAttribute('data-mindustry-player-mining-source', 'dom-pointer-event');")
    private static native void markRequested();

    @JSBody(params = {"x", "y"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-player-mining-tile-y', String(y));")
    private static native void markTarget(int x, int y);

    @JSBody(params = {"attempt"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-click-retry', String(attempt));")
    private static native void markMineRetry(int attempt);

    @JSBody(params = {"item", "delta"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'deposited'); document.documentElement.setAttribute('data-mindustry-player-mining-transfer', 'miner-auto'); document.documentElement.setAttribute('data-mindustry-player-mining-item', item); document.documentElement.setAttribute('data-mindustry-player-mining-core-delta', String(delta));")
    private static native void markAutoDeposited(String item, int delta);

    @JSBody(params = {"item", "before", "after", "x", "y"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke','deposited'); document.documentElement.setAttribute('data-mindustry-player-mining-transfer','player-drag-core'); document.documentElement.setAttribute('data-mindustry-player-mining-item',item); document.documentElement.setAttribute('data-mindustry-player-mining-core-delta',String(after-before)); document.documentElement.setAttribute('data-mindustry-player-mining-tile-x',String(x)); document.documentElement.setAttribute('data-mindustry-player-mining-tile-y',String(y));")
    private static native void markDeposited(String item, int before, int after, int x, int y);
}

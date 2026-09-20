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
    private static final int maxApproachFrames = 1800;
    private static final int maxDepositFrames = 180;

    private static boolean queryChecked;
    private static boolean enabled;
    private static boolean completed;
    private static boolean pointerDown;
    private static int stage;
    private static int spawnFrames;
    private static int mineFrames;
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
                throw new IllegalStateException("Player-mining smoke never received a real local unit");
            }
            return;
        }
        if(!unit.canMine()){
            throw new IllegalStateException("Local player unit cannot mine: " + unit.type.name);
        }

        if(stage == 0){
            Core.settings.put("smoothcamera", false);
            if(unit.stack.amount != 0){
                throw new IllegalStateException("Player-mining smoke requires an initially empty local unit stack");
            }
            core = unit.closestCore();
            if(core == null || core.items == null){
                throw new IllegalStateException("Player-mining smoke requires a real local core item inventory");
            }
            if(!findTarget(unit)){
                throw new IllegalStateException("No real mineable tile exists within the mining smoke search radius");
            }

            coreStartItems = core.items.get(targetItem);
            stage = 1;
            markTarget(targetX, targetY);
            return;
        }

        if(stage == 1){
            Tile target = world.tile(targetX, targetY);
            if(target == null) throw new IllegalStateException("Mine target disappeared before approach");

            // Approach to one tile so the ore projects near the free camera center.
            float safeRange = tilesize;
            if(!unit.within(target.worldx(), target.worldy(), safeRange)){
                moveToward(unit.x, unit.y, target.worldx(), target.worldy());
                if(++approachFrames >= maxApproachFrames){
                    stopMovement();
                    throw new IllegalStateException(
                        "DOM WASD could not move local player into mineRange: unit=" +
                        unit.x + "," + unit.y + " target=" + target.worldx() + "," + target.worldy()
                    );
                }
                return;
            }

            stopMovement();
            Vec2 projected = Core.camera.project(new Vec2(target.worldx(), target.worldy()));
            targetScreenX = projected.x;
            targetScreenY = projected.y;
            dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
            stage = 2;
            return;
        }

        if(stage == 2){
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
            if(unit.mineTile == world.tile(targetX, targetY)){
                stage = 6;
                return;
            }

            // Stock default is single-click mining. If the user setting requires
            // double-tap, only issue the second click after proving the first did
            // not start mining; never toggle an already-active mineTile back off.
            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 0, true);
            pointerDown = true;
            stage = 5;
            return;
        }

        if(stage == 5){
            dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
            pointerDown = false;
            stage = 6;
            return;
        }

        if(stage == 6){
            Tile target = world.tile(targetX, targetY);
            if(target == null) throw new IllegalStateException("Mine target disappeared");
            if(unit.mineTile != target && unit.stack.amount == 0 && core.items.get(targetItem) == coreStartItems){
                if(++mineFrames >= 30){
                    throw new IllegalStateException("DOM ore click did not start stock unit mining");
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
                    "Stock mining produced no local item: item=" + targetItem.name +
                    ", stack=" + unit.stack.amount + ", coreDelta=" + (coreNow - coreStartItems)
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
                        "DOM WASD could not return mined player to core itemTransferRange"
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
                    throw new IllegalStateException("DOM player press did not enter stock droppingItem mode");
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
                "Stock DOM player->core deposit did not transfer mined item: item=" + targetItem.name +
                ", stack=" + unit.stack.amount + "/" + minedStack +
                ", core=" + coreNow + "/" + coreStartItems
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

    private static void verifyWorldPointer(float x, float y, String label){
        if(Core.scene.hasMouse(x, y)){
            throw new IllegalStateException(label + " is covered by an Arc Scene actor");
        }
        if(Math.abs(Core.input.mouseX() - x) > 4f || Math.abs(Core.input.mouseY() - y) > 4f){
            throw new IllegalStateException(
                "DOM pointermove did not reach " + label + ": input=" +
                Core.input.mouseX() + "," + Core.input.mouseY() + " expected=" + x + "," + y
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

    @JSBody(params = {"item", "delta"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'deposited'); document.documentElement.setAttribute('data-mindustry-player-mining-transfer', 'miner-auto'); document.documentElement.setAttribute('data-mindustry-player-mining-item', item); document.documentElement.setAttribute('data-mindustry-player-mining-core-delta', String(delta));")
    private static native void markAutoDeposited(String item, int delta);

    @JSBody(params = {"item", "before", "after", "x", "y"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke','deposited'); document.documentElement.setAttribute('data-mindustry-player-mining-transfer','player-drag-core'); document.documentElement.setAttribute('data-mindustry-player-mining-item',item); document.documentElement.setAttribute('data-mindustry-player-mining-core-delta',String(after-before)); document.documentElement.setAttribute('data-mindustry-player-mining-tile-x',String(x)); document.documentElement.setAttribute('data-mindustry-player-mining-tile-y',String(y));")
    private static native void markDeposited(String item, int before, int after, int x, int y);
}

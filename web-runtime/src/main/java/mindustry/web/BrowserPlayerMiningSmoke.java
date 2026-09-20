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
    private static final int maxDepositFrames = 180;

    private static boolean queryChecked;
    private static boolean enabled;
    private static boolean completed;
    private static boolean pointerDown;
    private static int stage;
    private static int spawnFrames;
    private static int mineFrames;
    private static int depositFrames;
    private static int targetX = -1, targetY = -1;
    private static int coreStartItems;
    private static int minedStack;
    private static float targetScreenX, targetScreenY;
    private static float playerScreenX, playerScreenY;
    private static float coreScreenX, coreScreenY;
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
            markWaiting("unit", spawnFrames);
            return;
        }
        if(!unit.canMine()){
            throw new IllegalStateException("Local player unit cannot mine: " + unit.type.name);
        }

        if(stage == 0){
            if(unit.stack.amount != 0){
                throw new IllegalStateException("Player-mining smoke requires an initially empty local unit stack");
            }
            core = unit.closestCore();
            if(core == null || core.items == null){
                throw new IllegalStateException("Player-mining smoke requires a real local core item inventory");
            }
            if(!findTarget(unit)){
                throw new IllegalStateException("No real mineable visible tile exists inside local unit mineRange");
            }

            coreStartItems = core.items.get(targetItem);
            dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
            stage = 1;
            markTarget(targetX, targetY, targetItem.name, targetScreenX, targetScreenY, coreStartItems);
            return;
        }

        if(stage == 1){
            verifyWorldPointer(targetScreenX, targetScreenY, "mine target");
            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 0, true);
            pointerDown = true;
            stage = 2;
            markStage("mine-down");
            return;
        }

        if(stage == 2){
            dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
            pointerDown = false;
            stage = 3;
            markStage("mine-up");
            return;
        }

        if(stage == 3){
            if(unit.mineTile == world.tile(targetX, targetY)){
                stage = 5;
                markMiningStarted(false);
                return;
            }

            // Stock default is single-click mining. If the user setting requires
            // double-tap, only issue the second click after proving the first did
            // not start mining; never toggle an already-active mineTile back off.
            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 0, true);
            pointerDown = true;
            stage = 4;
            markStage("mine-second-down");
            return;
        }

        if(stage == 4){
            dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
            pointerDown = false;
            stage = 5;
            markMiningStarted(true);
            return;
        }

        if(stage == 5){
            Tile target = world.tile(targetX, targetY);
            if(target == null) throw new IllegalStateException("Mine target disappeared");
            if(unit.mineTile != target && unit.stack.amount == 0 && core.items.get(targetItem) == coreStartItems){
                if(++mineFrames >= 30){
                    throw new IllegalStateException("DOM ore click did not start stock unit mining");
                }
                markMiningProgress(mineFrames, unit.stack.amount, core.items.get(targetItem));
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
                Vec2 up = Core.camera.project(new Vec2(unit.x, unit.y));
                Vec2 cp = Core.camera.project(new Vec2(core.x, core.y));
                playerScreenX = up.x;
                playerScreenY = up.y;
                coreScreenX = cp.x;
                coreScreenY = cp.y;

                if(!player.within(core, itemTransferRange)){
                    throw new IllegalStateException(
                        "Mined item cannot exercise stock manual deposit because player is outside core itemTransferRange"
                    );
                }

                dispatchPointer("pointermove", playerScreenX, playerScreenY, -1, false);
                stage = 6;
                markMined(targetItem.name, minedStack);
                return;
            }

            mineFrames++;
            markMiningProgress(mineFrames, unit.stack.amount, coreNow);
            if(mineFrames >= maxMineFrames){
                throw new IllegalStateException(
                    "Stock mining produced no local item: item=" + targetItem.name +
                    ", stack=" + unit.stack.amount + ", coreDelta=" + (coreNow - coreStartItems)
                );
            }
            return;
        }

        if(stage == 6){
            verifyWorldPointer(playerScreenX, playerScreenY, "player item drag source");
            dispatchPointer("pointerdown", playerScreenX, playerScreenY, 0, true);
            pointerDown = true;
            stage = 7;
            markStage("deposit-player-down");
            return;
        }

        if(stage == 7){
            if(!control.input.isDroppingItem()){
                if(++depositFrames >= maxDepositFrames){
                    releasePointer();
                    throw new IllegalStateException("DOM player press did not enter stock droppingItem mode");
                }
                markDepositProgress("waiting-drag", depositFrames, unit.stack.amount, core.items.get(targetItem));
                return;
            }

            dispatchPointer("pointermove", coreScreenX, coreScreenY, 0, true);
            stage = 8;
            markStage("deposit-core-hover");
            return;
        }

        if(stage == 8){
            verifyWorldPointer(coreScreenX, coreScreenY, "core deposit target");
            dispatchPointer("pointerup", coreScreenX, coreScreenY, 0, false);
            pointerDown = false;
            stage = 9;
            markStage("deposit-core-up");
            return;
        }

        int coreNow = core.items.get(targetItem);
        if(coreNow > coreStartItems && unit.stack.amount < minedStack){
            completed = true;
            markDeposited(
                targetItem.name, minedStack, unit.stack.amount,
                coreStartItems, coreNow, targetX, targetY
            );
            return;
        }

        depositFrames++;
        markDepositProgress("waiting-transfer", depositFrames, unit.stack.amount, coreNow);
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
        int radius = Math.max(1, (int)Math.ceil(unit.type.mineRange / tilesize));
        int width = Core.graphics.getWidth(), height = Core.graphics.getHeight();
        boolean doubleTap = Core.settings.getBool("doubletapmine");

        for(int r = 1; r <= radius; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    Tile tile = world.tile(ux + dx, uy + dy);
                    if(tile == null || !unit.validMine(tile)) continue;

                    Item item = unit.getMineResult(tile);
                    if(item == null || !unit.acceptsItem(item)) continue;
                    if(!doubleTap && tile.floor().playerUnmineable && tile.overlay().itemDrop == null) continue;
                    if(!doubleTap && tile.overlay().playerUnmineable && tile.overlay().itemDrop != null) continue;

                    Vec2 projected = Core.camera.project(new Vec2(tile.worldx(), tile.worldy()));
                    if(projected.x < width * 0.12f || projected.x > width * 0.78f) continue;
                    if(projected.y < height * 0.16f || projected.y > height * 0.84f) continue;

                    targetX = tile.x;
                    targetY = tile.y;
                    targetItem = item;
                    targetScreenX = projected.x;
                    targetScreenY = projected.y;
                    return true;
                }
            }
        }
        return false;
    }

    private static void verifyWorldPointer(float x, float y, String label){
        if(Core.scene.hasMouse()){
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

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'requested'); document.documentElement.setAttribute('data-mindustry-player-mining-source', 'dom-pointer-event');")
    private static native void markRequested();

    @JSBody(params = {"what", "frames"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'waiting-' + what); document.documentElement.setAttribute('data-mindustry-player-mining-wait-frames', String(frames));")
    private static native void markWaiting(String what, int frames);

    @JSBody(params = {"x", "y", "item", "sx", "sy", "coreItems"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'targeted'); document.documentElement.setAttribute('data-mindustry-player-mining-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-player-mining-tile-y', String(y)); document.documentElement.setAttribute('data-mindustry-player-mining-item', item); document.documentElement.setAttribute('data-mindustry-player-mining-pointer-x', String(sx)); document.documentElement.setAttribute('data-mindustry-player-mining-pointer-y', String(sy)); document.documentElement.setAttribute('data-mindustry-player-mining-core-start', String(coreItems));")
    private static native void markTarget(int x, int y, String item, float sx, float sy, int coreItems);

    @JSBody(params = {"stage"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', stage);")
    private static native void markStage(String stage);

    @JSBody(params = {"doubleTap"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'mining'); document.documentElement.setAttribute('data-mindustry-player-mining-doubletap', String(doubleTap));")
    private static native void markMiningStarted(boolean doubleTap);

    @JSBody(params = {"frames", "stack", "coreItems"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-mine-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-player-mining-stack', String(stack)); document.documentElement.setAttribute('data-mindustry-player-mining-core-now', String(coreItems));")
    private static native void markMiningProgress(int frames, int stack, int coreItems);

    @JSBody(params = {"item", "stack"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'mined'); document.documentElement.setAttribute('data-mindustry-player-mining-item', item); document.documentElement.setAttribute('data-mindustry-player-mining-mined-stack', String(stack));")
    private static native void markMined(String item, int stack);

    @JSBody(params = {"phase", "frames", "stack", "coreItems"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', phase); document.documentElement.setAttribute('data-mindustry-player-mining-deposit-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-player-mining-stack', String(stack)); document.documentElement.setAttribute('data-mindustry-player-mining-core-now', String(coreItems));")
    private static native void markDepositProgress(String phase, int frames, int stack, int coreItems);

    @JSBody(params = {"item", "delta"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'deposited'); document.documentElement.setAttribute('data-mindustry-player-mining-transfer', 'miner-auto'); document.documentElement.setAttribute('data-mindustry-player-mining-item', item); document.documentElement.setAttribute('data-mindustry-player-mining-core-delta', String(delta));")
    private static native void markAutoDeposited(String item, int delta);

    @JSBody(params = {"item", "mined", "remaining", "before", "after", "x", "y"}, script = "document.documentElement.setAttribute('data-mindustry-player-mining-smoke', 'deposited'); document.documentElement.setAttribute('data-mindustry-player-mining-transfer', 'player-drag-core'); document.documentElement.setAttribute('data-mindustry-player-mining-item', item); document.documentElement.setAttribute('data-mindustry-player-mining-mined-stack', String(mined)); document.documentElement.setAttribute('data-mindustry-player-mining-final-stack', String(remaining)); document.documentElement.setAttribute('data-mindustry-player-mining-core-start', String(before)); document.documentElement.setAttribute('data-mindustry-player-mining-core-now', String(after)); document.documentElement.setAttribute('data-mindustry-player-mining-core-delta', String(after - before)); document.documentElement.setAttribute('data-mindustry-player-mining-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-player-mining-tile-y', String(y));")
    private static native void markDeposited(String item, int mined, int remaining, int before, int after, int x, int y);
}

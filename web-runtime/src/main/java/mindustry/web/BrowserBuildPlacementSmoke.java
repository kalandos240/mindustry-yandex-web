package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import mindustry.content.*;
import mindustry.core.World;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.world.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only end-to-end proof for the browser construction path.
 *
 * This probe never assigns InputHandler.block, never appends a BuildPlan and never
 * changes a world tile. It clicks the real Arc build-palette button and a real canvas
 * world position through DOM PointerEvents, then observes stock DesktopInput and the
 * local builder until the selected conveyor is actually constructed.
 */
public final class BrowserBuildPlacementSmoke{
    private static final int maxSpawnFrames = 900;
    private static final int maxUiFrames = 180;
    private static final int maxBuildFrames = 900;

    private static boolean queryChecked;
    private static boolean enabled;
    private static boolean completed;
    private static boolean pointerDown;
    private static boolean planObserved;
    private static boolean buildSoundObserved;
    private static boolean removeAfterBuild;
    private static boolean rotateAfterBuild;
    private static int originalRotation;
    private static boolean breakPlanObserved;
    private static int pointerButton;
    private static int stage;
    private static int spawnFrames;
    private static int uiFrames;
    private static int buildFrames;
    private static int breakFrames;
    private static int targetX = -1, targetY = -1;
    private static float targetScreenX, targetScreenY;

    private BrowserBuildPlacementSmoke(){}

    /** Called after each normal application frame; inert unless explicitly requested. */
    public static void update(){
        if(!enabled() || completed) return;

        if(state == null || player == null || !BrowserLocalMapRuntime.active() || !state.isPlaying()){
            releasePointer();
            return;
        }

        Unit unit = player.unit();
        if(unit == null || !unit.isAdded() || !unit.isValid() || !player.isBuilder()){
            spawnFrames++;
            markWaiting("unit", spawnFrames);
            if(spawnFrames >= maxSpawnFrames){
                throw new IllegalStateException("Build-placement smoke never received a real local builder unit");
            }
            return;
        }

        if(stage == 0){
            Element button = Core.scene == null ? null : Core.scene.find("web-block-conveyor");
            if(button == null || button.getWidth() <= 1f || button.getHeight() <= 1f){
                if(++uiFrames >= maxUiFrames){
                    throw new IllegalStateException("Build-placement smoke could not resolve laid-out web-block-conveyor palette button");
                }
                markWaiting("palette", uiFrames);
                return;
            }

            Vec2 point = button.localToStageCoordinates(new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
            dispatchPointer("pointermove", point.x, point.y, -1, false);
            stage = 1;
            markStage("palette-hover", point.x, point.y);
            return;
        }

        if(stage == 1){
            Element button = Core.scene.find("web-block-conveyor");
            if(button == null) throw new IllegalStateException("Conveyor palette button disappeared before DOM pointerdown");
            Vec2 point = button.localToStageCoordinates(new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
            dispatchPointer("pointerdown", point.x, point.y, 0, true);
            pointerDown = true;
            stage = 2;
            markStage("palette-down", point.x, point.y);
            return;
        }

        if(stage == 2){
            Element button = Core.scene.find("web-block-conveyor");
            if(button == null) throw new IllegalStateException("Conveyor palette button disappeared before DOM pointerup");
            Vec2 point = button.localToStageCoordinates(new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
            dispatchPointer("pointerup", point.x, point.y, 0, false);
            pointerDown = false;
            stage = 3;
            markStage("palette-up", point.x, point.y);
            return;
        }

        if(stage == 3){
            if(control == null || control.input == null || control.input.block != Blocks.conveyor){
                if(++uiFrames >= maxUiFrames){
                    throw new IllegalStateException("DOM click on real conveyor palette button did not select Blocks.conveyor");
                }
                markWaiting("selection", uiFrames);
                return;
            }

            if(!findTarget(unit)){
                throw new IllegalStateException("Build-placement smoke found no visible valid conveyor tile within local builder range");
            }

            dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
            stage = 4;
            markTarget(targetX, targetY, targetScreenX, targetScreenY);
            return;
        }

        if(stage == 4){
            // The world click must not be intercepted by the Arc HUD. This also proves
            // the pointermove reached WebInput before placement starts.
            if(Core.scene.hasMouse()){
                throw new IllegalStateException("Chosen build tile is covered by an Arc Scene actor: " + targetX + "," + targetY);
            }
            if(Math.abs(Core.input.mouseX() - targetScreenX) > 3f || Math.abs(Core.input.mouseY() - targetScreenY) > 3f){
                throw new IllegalStateException(
                    "DOM pointermove did not reach build target: input=" + Core.input.mouseX() + "," + Core.input.mouseY() +
                    " expected=" + targetScreenX + "," + targetScreenY
                );
            }

            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 0, true);
            pointerDown = true;
            stage = 5;
            markStage("world-down", targetScreenX, targetScreenY);
            return;
        }

        if(stage == 5){
            dispatchPointer("pointerup", targetScreenX, targetScreenY, 0, false);
            pointerDown = false;
            stage = 6;
            markStage("world-up", targetScreenX, targetScreenY);
            return;
        }

        Tile tile = world.tile(targetX, targetY);
        if(tile == null) throw new IllegalStateException("Build target tile disappeared from loaded world");

        if(removeAfterBuild && stage >= 7){
            updateRemoval(unit, tile);
            return;
        }
        if(rotateAfterBuild && stage >= 20){
            updateRotate(tile);
            return;
        }

        for(BuildPlan plan : unit.plans()){
            if(!plan.breaking && plan.block == Blocks.conveyor && plan.x == targetX && plan.y == targetY){
                planObserved = true;
                break;
            }
        }
        if(planObserved) markPlanObserved(targetX, targetY, unit.plans().size);

        if(planObserved && Sounds.loopBuild instanceof BrowserSound){
            int voices = Sounds.loopBuild.countPlaying();
            if(voices > 0){
                buildSoundObserved = true;
                markBuildSoundObserved(voices);
            }
        }

        if(tile.block() == Blocks.conveyor && tile.build != null && tile.build.team == player.team()){
            if(!planObserved){
                throw new IllegalStateException("Conveyor completed without smoke observing the stock player BuildPlan");
            }
            if(!buildSoundObserved){
                throw new IllegalStateException("Conveyor completed without an observed stock loopBuild BrowserAudio voice");
            }
            if(removeAfterBuild){
                if(control.input.block != Blocks.conveyor){
                    throw new IllegalStateException("Conveyor selection disappeared before stock right-click deselect test");
                }
                uiFrames = 0;
                stage = 7;
                markRemovalStage("built-before-removal", targetX, targetY);
            }else if(rotateAfterBuild){
                control.input.block = null;
                originalRotation = tile.build.rotation;
                stage = 20;
                markRotateStage("built-before-rotate", originalRotation);
            }else{
                completed = true;
                control.input.block = null; // cleanup only after stock construction has completed.
                markBuilt(targetX, targetY, buildFrames, unit.id, unit.type.name);
            }
            return;
        }

        buildFrames++;
        markBuildProgress(buildFrames, unit.plans().size, tile.block().name, planObserved);
        if(buildFrames >= maxBuildFrames){
            throw new IllegalStateException(
                "Stock builder did not complete DOM-placed conveyor: tile=" + tile.block().name +
                ", plans=" + unit.plans().size + ", planObserved=" + planObserved
            );
        }
    }

    private static void updateRotate(Tile tile){
        if(tile.build == null || tile.block() != Blocks.conveyor){
            throw new IllegalStateException("Rotate smoke lost the completed conveyor");
        }

        if(stage == 20){
            Vec2 projected = Core.camera.project(new Vec2(targetX * tilesize + tilesize / 2f, targetY * tilesize + tilesize / 2f));
            targetScreenX = projected.x;
            targetScreenY = projected.y;
            dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
            stage = 21;
            markRotateStage("hover", tile.build.rotation);
            return;
        }

        if(stage == 21){
            if(Core.scene.hasMouse()) throw new IllegalStateException("Rotate target is covered by an Arc Scene actor");
            dispatchKey("keydown", "KeyR", "r");
            uiFrames = 0;
            stage = 22;
            markRotateStage("r-down", tile.build.rotation);
            return;
        }

        if(stage == 22){
            // Browser DOM input is queued after the application frame. On heavy TeaVM
            // frames, do not emit the wheel until the exact stock binding reports R down.
            if(!Core.input.keyDown(Binding.rotatePlaced)){
                if(++uiFrames >= maxUiFrames){
                    dispatchKey("keyup", "KeyR", "r");
                    throw new IllegalStateException("DOM KeyR never reached stock rotatePlaced binding");
                }
                return;
            }

            dispatchWheel(0f, 100f);
            uiFrames = 0;
            stage = 23;
            markRotateStage("wheel", tile.build.rotation);
            return;
        }

        if(stage == 23){
            int rotation = tile.build.rotation;
            if(rotation != originalRotation){
                dispatchKey("keyup", "KeyR", "r");
                completed = true;
                markRotated(originalRotation, rotation, targetX, targetY);
                return;
            }

            // The wheel event is queued through the same WebInput bridge. Keep R held
            // until stock DesktopInput has consumed axisTap(Binding.rotate).
            if(++uiFrames >= maxUiFrames){
                dispatchKey("keyup", "KeyR", "r");
                throw new IllegalStateException("Stock R + wheel rotate input did not change conveyor rotation");
            }
        }
    }

    private static void updateRemoval(Unit unit, Tile tile){
        if(stage == 7){
            Vec2 projected = Core.camera.project(new Vec2(targetX * tilesize + tilesize / 2f, targetY * tilesize + tilesize / 2f));
            targetScreenX = projected.x;
            targetScreenY = projected.y;
            dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
            stage = 8;
            markRemovalStage("deselect-hover", targetX, targetY);
            return;
        }

        if(stage == 8){
            if(Core.scene.hasMouse()) throw new IllegalStateException("Removal target is covered by an Arc Scene actor");
            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 2, true);
            pointerDown = true;
            pointerButton = 2;
            stage = 9;
            markRemovalStage("deselect-down", targetX, targetY);
            return;
        }

        if(stage == 9){
            dispatchPointer("pointerup", targetScreenX, targetScreenY, 2, false);
            pointerDown = false;
            stage = 10;
            markRemovalStage("deselect-up", targetX, targetY);
            return;
        }

        if(stage == 10){
            if(control.input.block != null){
                if(++uiFrames >= maxUiFrames){
                    throw new IllegalStateException("First stock right-click did not deselect the conveyor palette block");
                }
                return;
            }
            uiFrames = 0;
            dispatchPointer("pointermove", targetScreenX, targetScreenY, -1, false);
            stage = 11;
            markRemovalStage("break-hover", targetX, targetY);
            return;
        }

        if(stage == 11){
            if(Core.scene.hasMouse()) throw new IllegalStateException("Break target is covered by an Arc Scene actor");
            dispatchPointer("pointerdown", targetScreenX, targetScreenY, 2, true);
            pointerDown = true;
            pointerButton = 2;
            stage = 12;
            markRemovalStage("break-down", targetX, targetY);
            return;
        }

        if(stage == 12){
            // Keep the physical right button held until stock DesktopInput has consumed
            // Binding.breakBlock and entered breaking mode. On heavy TeaVM frames, a
            // fixed one-frame press can otherwise release before gameplay input sees it.
            if(!control.input.isBreaking()){
                if(++uiFrames >= maxUiFrames){
                    releasePointer();
                    throw new IllegalStateException("Second stock right-click never entered DesktopInput breaking mode");
                }
                return;
            }

            dispatchPointer("pointerup", targetScreenX, targetScreenY, 2, false);
            pointerDown = false;
            stage = 13;
            markRemovalStage("break-up", targetX, targetY);
            return;
        }

        for(BuildPlan plan : unit.plans()){
            if(plan.breaking && plan.x == targetX && plan.y == targetY){
                breakPlanObserved = true;
                break;
            }
        }
        if(breakPlanObserved) markBreakPlanObserved(targetX, targetY, unit.plans().size);

        if(tile.block() == Blocks.air && tile.build == null){
            if(!breakPlanObserved){
                throw new IllegalStateException("Conveyor disappeared without smoke observing a stock breaking BuildPlan");
            }
            completed = true;
            markRemoved(targetX, targetY, breakFrames, unit.id, unit.type.name);
            return;
        }

        breakFrames++;
        markBreakProgress(breakFrames, unit.plans().size, tile.block().name, breakPlanObserved);
        if(breakFrames >= maxBuildFrames){
            throw new IllegalStateException(
                "Stock builder did not remove DOM-selected conveyor: tile=" + tile.block().name +
                ", plans=" + unit.plans().size + ", breakPlanObserved=" + breakPlanObserved
            );
        }
    }

    private static boolean findTarget(Unit unit){
        if(Core.camera == null) return false;

        int ux = World.toTile(unit.x), uy = World.toTile(unit.y);
        int width = Core.graphics.getWidth(), height = Core.graphics.getHeight();
        float maxRange = Math.max(32f, unit.type.buildRange - tilesize * 2f);

        for(int radius = 3; radius <= 10; radius++){
            for(int dx = -radius; dx <= radius; dx++){
                for(int dy = -radius; dy <= radius; dy++){
                    if(Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
                    int x = ux + dx, y = uy + dy;
                    float wx = x * tilesize + tilesize / 2f;
                    float wy = y * tilesize + tilesize / 2f;
                    if(!unit.within(wx, wy, maxRange)) continue;
                    if(!Build.validPlace(Blocks.conveyor, player.team(), x, y, 0)) continue;

                    Vec2 projected = Core.camera.project(new Vec2(wx, wy));
                    // Stay away from the bottom-right palette and common edge HUD.
                    if(projected.x < width * 0.12f || projected.x > width * 0.62f) continue;
                    if(projected.y < height * 0.16f || projected.y > height * 0.84f) continue;

                    targetX = x;
                    targetY = y;
                    targetScreenX = projected.x;
                    targetScreenY = projected.y;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean enabled(){
        if(!queryChecked){
            queryChecked = true;
            removeAfterBuild = removalRequested();
            rotateAfterBuild = rotateRequested();
            enabled = requested() || removeAfterBuild || rotateAfterBuild;
            if(enabled) markRequested();
        }
        return enabled;
    }

    private static void releasePointer(){
        if(!pointerDown) return;
        dispatchPointer("pointerup", targetScreenX, targetScreenY, pointerButton, false);
        pointerDown = false;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryBuildPlacementSmoke') === '1';")
    private static native boolean requested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryBuildRemovalSmoke') === '1';")
    private static native boolean removalRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryBuildRotateSmoke') === '1';")
    private static native boolean rotateRequested();

    /** Stage coordinates use bottom-left origin, while DOM clientY uses top-left. */
    @JSBody(params = {"type", "sx", "sy", "button", "down"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('Mindustry canvas missing for build-placement smoke');
        const rect = canvas.getBoundingClientRect();
        const clientX = rect.left + sx;
        const clientY = rect.top + rect.height - sy;
        const event = new PointerEvent(type, {
            pointerId: 1,
            pointerType: 'mouse',
            isPrimary: true,
            clientX: clientX,
            clientY: clientY,
            button: button < 0 ? -1 : button,
            buttons: down ? (button === 2 ? 2 : (button === 1 ? 4 : 1)) : 0,
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

    @JSBody(params = {"dx", "dy"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('Mindustry canvas missing for rotate smoke');
        canvas.dispatchEvent(new WheelEvent('wheel', {
            deltaX: dx,
            deltaY: dy,
            deltaMode: 0,
            bubbles: true,
            cancelable: true
        }));
        """)
    private static native void dispatchWheel(float dx, float dy);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-build-placement-smoke', 'requested'); document.documentElement.setAttribute('data-mindustry-build-placement-source', 'dom-pointer-event'); document.documentElement.setAttribute('data-mindustry-build-placement-block', 'conveyor');")
    private static native void markRequested();

    @JSBody(params = {"what", "frames"}, script = "document.documentElement.setAttribute('data-mindustry-build-placement-smoke', 'waiting-' + what); document.documentElement.setAttribute('data-mindustry-build-placement-wait-frames', String(frames));")
    private static native void markWaiting(String what, int frames);

    @JSBody(params = {"stage", "x", "y"}, script = "document.documentElement.setAttribute('data-mindustry-build-placement-smoke', stage); document.documentElement.setAttribute('data-mindustry-build-placement-pointer-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-placement-pointer-y', String(y));")
    private static native void markStage(String stage, float x, float y);

    @JSBody(params = {"x", "y", "sx", "sy"}, script = "document.documentElement.setAttribute('data-mindustry-build-placement-smoke', 'targeted'); document.documentElement.setAttribute('data-mindustry-build-placement-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-placement-tile-y', String(y)); document.documentElement.setAttribute('data-mindustry-build-placement-pointer-x', String(sx)); document.documentElement.setAttribute('data-mindustry-build-placement-pointer-y', String(sy));")
    private static native void markTarget(int x, int y, float sx, float sy);

    @JSBody(params = {"x", "y", "plans"}, script = "document.documentElement.setAttribute('data-mindustry-build-placement-plan-observed', 'true'); document.documentElement.setAttribute('data-mindustry-build-placement-plan-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-placement-plan-y', String(y)); document.documentElement.setAttribute('data-mindustry-build-placement-plans', String(plans));")
    private static native void markPlanObserved(int x, int y, int plans);

    @JSBody(params = {"frames", "plans", "tile", "observed"}, script = "document.documentElement.setAttribute('data-mindustry-build-placement-smoke', 'building'); document.documentElement.setAttribute('data-mindustry-build-placement-build-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-build-placement-plans', String(plans)); document.documentElement.setAttribute('data-mindustry-build-placement-current-tile', tile); document.documentElement.setAttribute('data-mindustry-build-placement-plan-observed', String(observed));")
    private static native void markBuildProgress(int frames, int plans, String tile, boolean observed);

    @JSBody(params = {"voices"}, script = "document.documentElement.setAttribute('data-mindustry-build-placement-audio', 'loopBuild-browser-voice'); document.documentElement.setAttribute('data-mindustry-build-placement-audio-voices', String(voices));")
    private static native void markBuildSoundObserved(int voices);

    @JSBody(params = {"x", "y", "frames", "id", "type"}, script = "document.documentElement.setAttribute('data-mindustry-build-placement-smoke', 'built'); document.documentElement.setAttribute('data-mindustry-build-placement-source', 'dom-pointer-event'); document.documentElement.setAttribute('data-mindustry-build-placement-block', 'conveyor'); document.documentElement.setAttribute('data-mindustry-build-placement-plan-observed', 'true'); document.documentElement.setAttribute('data-mindustry-build-placement-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-placement-tile-y', String(y)); document.documentElement.setAttribute('data-mindustry-build-placement-build-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-build-placement-unit-id', String(id)); document.documentElement.setAttribute('data-mindustry-build-placement-unit', type);")
    private static native void markBuilt(int x, int y, int frames, int id, String type);

    @JSBody(params = {"stage", "rotation"}, script = "document.documentElement.setAttribute('data-mindustry-build-rotate-smoke', stage); document.documentElement.setAttribute('data-mindustry-build-rotate-current', String(rotation)); document.documentElement.setAttribute('data-mindustry-build-rotate-source', 'dom-key-wheel');")
    private static native void markRotateStage(String stage, int rotation);

    @JSBody(params = {"before", "after", "x", "y"}, script = "document.documentElement.setAttribute('data-mindustry-build-rotate-smoke', 'rotated'); document.documentElement.setAttribute('data-mindustry-build-rotate-source', 'dom-key-wheel'); document.documentElement.setAttribute('data-mindustry-build-rotate-before', String(before)); document.documentElement.setAttribute('data-mindustry-build-rotate-after', String(after)); document.documentElement.setAttribute('data-mindustry-build-rotate-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-rotate-tile-y', String(y));")
    private static native void markRotated(int before, int after, int x, int y);

    @JSBody(params = {"stage", "x", "y"}, script = "document.documentElement.setAttribute('data-mindustry-build-removal-smoke', stage); document.documentElement.setAttribute('data-mindustry-build-removal-source', 'dom-pointer-event'); document.documentElement.setAttribute('data-mindustry-build-removal-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-removal-tile-y', String(y));")
    private static native void markRemovalStage(String stage, int x, int y);

    @JSBody(params = {"x", "y", "plans"}, script = "document.documentElement.setAttribute('data-mindustry-build-removal-plan-observed', 'true'); document.documentElement.setAttribute('data-mindustry-build-removal-plan-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-removal-plan-y', String(y)); document.documentElement.setAttribute('data-mindustry-build-removal-plans', String(plans));")
    private static native void markBreakPlanObserved(int x, int y, int plans);

    @JSBody(params = {"frames", "plans", "tile", "observed"}, script = "document.documentElement.setAttribute('data-mindustry-build-removal-smoke', 'breaking'); document.documentElement.setAttribute('data-mindustry-build-removal-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-build-removal-plans', String(plans)); document.documentElement.setAttribute('data-mindustry-build-removal-current-tile', tile); document.documentElement.setAttribute('data-mindustry-build-removal-plan-observed', String(observed));")
    private static native void markBreakProgress(int frames, int plans, String tile, boolean observed);

    @JSBody(params = {"x", "y", "frames", "id", "type"}, script = "document.documentElement.setAttribute('data-mindustry-build-removal-smoke', 'removed'); document.documentElement.setAttribute('data-mindustry-build-removal-source', 'dom-pointer-event'); document.documentElement.setAttribute('data-mindustry-build-removal-plan-observed', 'true'); document.documentElement.setAttribute('data-mindustry-build-removal-final-tile', 'air'); document.documentElement.setAttribute('data-mindustry-build-removal-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-removal-tile-y', String(y)); document.documentElement.setAttribute('data-mindustry-build-removal-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-build-removal-unit-id', String(id)); document.documentElement.setAttribute('data-mindustry-build-removal-unit', type);")
    private static native void markRemoved(int x, int y, int frames, int id, String type);
}

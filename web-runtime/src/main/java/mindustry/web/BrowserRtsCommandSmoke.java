package mindustry.web;

import arc.*;
import arc.math.geom.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.input.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only end-to-end stock RTS command probe on an actual loaded local map.
 *
 * A real allied Dagger is created using the original UnitType factory only when
 * ?mindustryRtsCommandSmoke=1 is explicitly present. Its selection uses a DOM
 * KeyG event through BrowserInputBridge/WebInput/DesktopInput, then a DOM mouse
 * right-click enters DesktopInput.touchDown -> InputHandler.commandTap ->
 * Call.commandUnits. No selectedUnits or CommandAI order is changed by this probe.
 *
 * RTS mode is entered through a genuine DOM ShiftLeft keydown, matching the
 * stock command binding (hold or toggle). A separate browser HUD gate also
 * checks that the click-to-toggle button is wired to the same command field.
 * Neither probe runs in a normal Yandex release.
 */
public final class BrowserRtsCommandSmoke{
    private static boolean checked, enabled, done, shiftHeld, groupMode, rectMode;
    private static int stage, frames;
    private static Unit probe, second;
    private static float targetX, targetY, targetScreenX, targetScreenY, dragStartX, dragStartY, dragEndX, dragEndY;

    private BrowserRtsCommandSmoke(){}

    public static boolean requested(){
        if(!checked){
            rectMode = queryRectRequested();
            groupMode = queryGroupRequested() || rectMode;
            enabled = queryRequested() || groupMode;
            checked = true;
            if(enabled) markStage("requested");
        }
        return enabled;
    }

    public static void update(){
        if(!requested() || done) return;
        if(state == null || !state.isPlaying() || state.gameOver || !BrowserLocalMapRuntime.active()
            || player == null || player.unit() == null || control == null
            || !(control.input instanceof DesktopInput)){
            return;
        }

        if(stage == 0){
            if(!player.unit().isAdded()) return;
            probe = UnitTypes.dagger.create(player.team());
            probe.set(rectMode ? Core.camera.position.x + 10f : player.unit().x + 24f,
                rectMode ? Core.camera.position.y + 4f : player.unit().y + 12f);
            probe.add();
            if(groupMode){
                second = UnitTypes.dagger.create(player.team());
                second.set(rectMode ? Core.camera.position.x + 30f : player.unit().x + 36f,
                    rectMode ? Core.camera.position.y + 16f : player.unit().y + 20f);
                second.add();
                if(!(second.controller() instanceof CommandAI) || !second.isCommandable()){
                    throw new IllegalStateException("Stock RTS group smoke requires a second controllable Dagger");
                }
            }
            if(!(probe.controller() instanceof CommandAI) || !probe.isCommandable()){
                throw new IllegalStateException("Real RTS smoke requires a stock controllable Dagger");
            }
            // Stock RTS mode is reached through the same ShiftLeft event as
            // a player's physical keyboard, not by writing commandMode.
            dispatchShift(true);
            shiftHeld = true;
            stage = 1;
            frames = 0;
            markStage("unit-spawned");
            return;
        }

        if(!probe.isAdded() || !probe.isValid() || (groupMode && (!second.isAdded() || !second.isValid()))){
            throw new IllegalStateException("RTS smoke test unit despawned");
        }

        if(stage == 1){
            if(control.input.commandMode){
                if(rectMode){
                    // The rectangle is laid out around the two actual units,
                    // inside the gameplay camera view. No selection state is
                    // mutated; the native pointer queue must select both.
                    Vec2 start = Core.camera.project(new Vec2(
                        Math.min(probe.x, second.x) - 12f, Math.min(probe.y, second.y) - 12f));
                    Vec2 end = Core.camera.project(new Vec2(
                        Math.max(probe.x, second.x) + 12f, Math.max(probe.y, second.y) + 12f));
                    dragStartX = start.x;
                    dragStartY = start.y;
                    dragEndX = end.x;
                    dragEndY = end.y;
                    if(dragStartX < 50f || dragEndX > Core.graphics.getWidth() - 50f
                        || dragStartY < 50f || dragEndY > Core.graphics.getHeight() - 50f){
                        throw new IllegalStateException("RTS group-drag test rectangle falls outside the canvas");
                    }
                    dispatchDragPointer("pointerdown", dragStartX, dragStartY);
                    stage = 4;
                    markStage("dom-rect-down");
                }else{
                    dispatchKey(true);
                    stage = 2;
                    markStage("dom-g-down");
                }
                frames = 0;
                return;
            }
            if(++frames > 90){
                releaseShift();
                throw new IllegalStateException("DOM ShiftLeft did not enter stock DesktopInput RTS mode");
            }
            return;
        }

        if(stage == 4){
            dispatchDragPointer("pointermove", dragEndX, dragEndY);
            stage = 5;
            markStage("dom-rect-dragged");
            return;
        }

        if(stage == 5){
            dispatchDragPointer("pointerup", dragEndX, dragEndY);
            stage = 2;
            frames = 0;
            markStage("dom-rect-up");
            return;
        }

        if(stage == 2){
            if(control.input.selectedUnits.contains(probe) &&
                (!groupMode || control.input.selectedUnits.contains(second))){
                if(!rectMode) dispatchKey(false);
                if(rectMode) markRectSelected(control.input.selectedUnits.size);
                // The world can be much larger than the visible canvas.
                // Use the actual camera center as the DOM target instead of
                // assuming a +68 world-unit move remains on-screen at every
                // desktop viewport/zoom. Only ordinary mouse input creates
                // the order; this code never writes the CommandAI target.
                // Use a meaningful travel distance: a target only ~10px from
                // the selected Dagger can be reached and cleared by CommandAI
                // before the next frame observes its targetPos.
                targetX = Math.max(16f, Math.min((world.width() - 2) * tilesize,
                    Core.camera.position.x + Core.camera.width * 0.26f));
                targetY = Math.max(16f, Math.min((world.height() - 2) * tilesize,
                    Core.camera.position.y + Core.camera.height * 0.14f));
                Vec2 screen = Core.camera.project(new Vec2(targetX, targetY));
                if(screen.x < 48f || screen.x > Core.graphics.getWidth() - 48f
                    || screen.y < 48f || screen.y > Core.graphics.getHeight() - 48f){
                    throw new IllegalStateException("RTS smoke target is outside gameplay viewport: " +
                        screen + ", camera=" + Core.camera.position +
                        ", size=" + Core.camera.width + "x" + Core.camera.height);
                }
                // Dispatch move/down/up in separate real browser frames.
                // WebInput receives the queued pointer event on the following
                // Arc update, just like a person positioning and clicking a
                // physical mouse. Same-tick down/up can elide the pressed edge.
                targetScreenX = screen.x;
                targetScreenY = screen.y;
                dispatchRightPointer("pointermove", targetScreenX, targetScreenY);
                stage = 6;
                frames = 0;
                markStage("dom-right-hover");
                return;
            }
            if(++frames > 90){
                if(!rectMode) dispatchKey(false);
                releaseShift();
                throw new IllegalStateException(rectMode
                    ? "DOM left-button rectangle did not select both stock Daggers"
                    : "DOM KeyG did not select the stock Dagger through DesktopInput");
            }
            return;
        }

        if(stage == 6){
            dispatchRightPointer("pointerdown", targetScreenX, targetScreenY);
            stage = 7;
            markStage("dom-right-down");
            return;
        }

        if(stage == 7){
            dispatchRightPointer("pointerup", targetScreenX, targetScreenY);
            stage = 3;
            frames = 0;
            markStage("dom-right-up");
            return;
        }

        if(stage == 3){
            if(probe.controller() instanceof CommandAI ai && ai.targetPos != null
                && ai.targetPos.dst(targetX, targetY) < 48f){
                boolean bothCommanded = !groupMode ||
                    (second.controller() instanceof CommandAI other &&
                        other.targetPos != null && other.targetPos.dst(targetX, targetY) < 48f);
                if(bothCommanded){
                    done = true;
                    releaseShift();
                    markCommanded(probe.id, targetX, targetY, ai.targetPos.x, ai.targetPos.y,
                        control.input.selectedUnits.size);
                    if(groupMode) markGroupCommanded(probe.id, second.id, control.input.selectedUnits.size);
                    if(rectMode) markRectCommanded();
                    return;
                }
            }
            if(++frames > 180){
                releaseShift();
                CommandAI ai = probe.controller() instanceof CommandAI command ? command : null;
                throw new IllegalStateException("DOM right-click did not leave an observable stock CommandAI move target" +
                    ": commandMode=" + control.input.commandMode +
                    ", selected=" + control.input.selectedUnits.size +
                    ", probe=" + probe.x + "," + probe.y +
                    ", expected=" + targetX + "," + targetY +
                    ", actual=" + (ai == null ? "no-CommandAI" : ai.targetPos) +
                    ", mouse=" + Core.input.mouseX() + "," + Core.input.mouseY() +
                    ", hoveredUI=" + Core.scene.hasMouse());
            }
        }
    }

    private static void releaseShift(){
        if(!shiftHeld) return;
        dispatchShift(false);
        shiftHeld = false;
    }

    @JSBody(params = {"down"}, script = """
        window.dispatchEvent(new KeyboardEvent(down ? 'keydown' : 'keyup', {
            code:'ShiftLeft', key:'Shift', bubbles:true, cancelable:true, repeat:false
        }));
        """)
    private static native void dispatchShift(boolean down);

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryRtsRectSmoke') === '1';")
    private static native boolean queryRectRequested();

    @JSBody(params = {"type", "sx", "sy"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('RTS browser canvas missing in rectangle test');
        const rect = canvas.getBoundingClientRect();
        canvas.dispatchEvent(new PointerEvent(type, {
            pointerId:1, pointerType:'mouse', isPrimary:true,
            clientX:rect.left+sx, clientY:rect.top+rect.height-sy,
            button:type==='pointermove'?-1:0, buttons:type==='pointerup'?0:1,
            bubbles:true, cancelable:true
        }));
        """)
    private static native void dispatchDragPointer(String type, float sx, float sy);

    @JSBody(params = {"selected"}, script = """
        document.documentElement.setAttribute('data-mindustry-rts-rect-smoke','selected');
        document.documentElement.setAttribute('data-mindustry-rts-rect-source','dom-left-button-world-drag');
        document.documentElement.setAttribute('data-mindustry-rts-rect-selected-count',String(selected));
        """)
    private static native void markRectSelected(int selected);

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryRtsGroupSmoke') === '1';")
    private static native boolean queryGroupRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryRtsCommandSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params = {"down"}, script = """
        window.dispatchEvent(new KeyboardEvent(down ? 'keydown' : 'keyup', {
            code:'KeyG', key:'g', bubbles:true, cancelable:true, repeat:false
        }));
        """)
    private static native void dispatchKey(boolean down);

    @JSBody(params = {"type", "sx", "sy"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('RTS browser canvas is missing');
        const rect = canvas.getBoundingClientRect();
        canvas.dispatchEvent(new PointerEvent(type, {
            pointerId:1, pointerType:'mouse', isPrimary:true,
            clientX:rect.left + sx, clientY:rect.top + rect.height - sy,
            button:type === 'pointermove' ? -1 : 2,
            buttons:type === 'pointerup' ? 0 : 2,
            bubbles:true, cancelable:true
        }));
        """)
    private static native void dispatchRightPointer(String type, float sx, float sy);

    @JSBody(params = {"stage"}, script = "document.documentElement.setAttribute('data-mindustry-rts-command-smoke',stage);")
    private static native void markStage(String stage);

    @JSBody(params = {"id", "tx", "ty", "ax", "ay", "count"}, script = """
        const root = document.documentElement;
        root.setAttribute('data-mindustry-rts-command-smoke','commanded');
        root.setAttribute('data-mindustry-rts-command-source','dom-shift-g-and-right-click');
        root.setAttribute('data-mindustry-rts-command-unit-id',String(id));
        root.setAttribute('data-mindustry-rts-command-target',String(tx)+','+String(ty));
        root.setAttribute('data-mindustry-rts-command-ai-target',String(ax)+','+String(ay));
        root.setAttribute('data-mindustry-rts-selected-count',String(count));
        """)
    private static native void markCommanded(int id, float tx, float ty, float ax, float ay, int count);

    @JSBody(params = {"firstId", "secondId", "count"}, script = """
        const root = document.documentElement;
        root.setAttribute('data-mindustry-rts-group-smoke','commanded');
        root.setAttribute('data-mindustry-rts-group-source','dom-key-g-and-single-right-click');
        root.setAttribute('data-mindustry-rts-group-unit-ids',String(firstId)+','+String(secondId));
        root.setAttribute('data-mindustry-rts-group-selected-count',String(count));
        root.setAttribute('data-mindustry-rts-group-orders','2');
        """)
    private static native void markGroupCommanded(int firstId, int secondId, int count);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-rts-rect-smoke','commanded');")
    private static native void markRectCommanded();
}

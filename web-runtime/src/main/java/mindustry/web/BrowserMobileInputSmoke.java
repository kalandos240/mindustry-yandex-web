package mindustry.web;

import arc.*;
import mindustry.gen.*;
import mindustry.input.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only proof that real DOM touch input drives stock MobileInput panning and player movement.
 *
 * The smoke never mutates camera or unit coordinates directly. It emits an ordinary touch
 * PointerEvent drag on the game canvas, then observes BrowserInputBridge -> WebInput ->
 * GestureDetector -> MobileInput.pan() and the normal MobileInput.updateMovement() result.
 */
public final class BrowserMobileInputSmoke{
    private static final int maxSpawnFrames = 900;
    private static final int maxMoveFrames = 300;

    private static boolean queryChecked;
    private static boolean enabled;
    private static boolean completed;
    private static boolean pointerDown;
    private static int stage;
    private static int spawnFrames;
    private static int moveFrames;
    private static int unitId = -1;
    private static float startCameraX, startCameraY;
    private static float startUnitX, startUnitY;

    private BrowserMobileInputSmoke(){}

    /** Called after each normal application frame; inert unless explicitly requested. */
    public static void update(){
        if(!enabled() || completed) return;

        if(state == null || player == null || control == null || !BrowserLocalMapRuntime.active() || !state.isPlaying()){
            releasePointer();
            return;
        }
        if(!Core.app.isMobile() || !(control.input instanceof MobileInput)){
            releasePointer();
            throw new IllegalStateException("Mobile-input smoke requires the stock MobileInput runtime");
        }
        if(Core.settings.getBool("keyboard")){
            releasePointer();
            throw new IllegalStateException("Mobile-input smoke requires touch controls, not mobile keyboard mode");
        }

        Unit unit = player.unit();
        if(unit == null || !unit.isAdded() || !unit.isValid()){
            spawnFrames++;
            markWaiting(spawnFrames);
            if(spawnFrames >= maxSpawnFrames){
                throw new IllegalStateException("Mobile-input smoke never received a real local player unit");
            }
            return;
        }

        if(unitId != -1 && (unit.id != unitId || player.unit() != unit)){
            releasePointer();
            throw new IllegalStateException("Mobile-input smoke changed controlled unit during touch-pan test");
        }

        if(stage == 0){
            unitId = unit.id;
            startCameraX = Core.camera.position.x;
            startCameraY = Core.camera.position.y;
            startUnitX = unit.x;
            startUnitY = unit.y;

            dispatchTouch("pointerdown", 0.62f, 0.52f);
            pointerDown = true;
            stage = 1;
            markStarted(unitId, unit.type.name, startCameraX, startCameraY, startUnitX, startUnitY);
            return;
        }

        if(stage == 1){
            dispatchTouch("pointermove", 0.50f, 0.52f);
            stage = 2;
            markStage("drag-1");
            return;
        }

        if(stage == 2){
            dispatchTouch("pointermove", 0.36f, 0.52f);
            stage = 3;
            markStage("drag-2");
            return;
        }

        if(stage == 3){
            releasePointer();
            stage = 4;
            markStage("released");
            return;
        }

        moveFrames++;
        float cameraDx = Math.abs(Core.camera.position.x - startCameraX);
        float cameraDy = Math.abs(Core.camera.position.y - startCameraY);
        float unitDx = Math.abs(unit.x - startUnitX);
        float unitDy = Math.abs(unit.y - startUnitY);
        markProgress(moveFrames, Core.camera.position.x, Core.camera.position.y, unit.x, unit.y, cameraDx, cameraDy, unitDx, unitDy);

        if(cameraDx > 2f && (unitDx > 0.5f || unitDy > 0.5f)){
            completed = true;
            markMoved(
                unitId, unit.type.name, moveFrames,
                Core.camera.position.x, Core.camera.position.y,
                unit.x, unit.y, cameraDx, cameraDy, unitDx, unitDy
            );
            return;
        }

        if(moveFrames >= maxMoveFrames){
            throw new IllegalStateException(
                "DOM touch pan did not drive stock MobileInput movement: cameraDelta=" +
                cameraDx + "," + cameraDy + " unitDelta=" + unitDx + "," + unitDy
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
        dispatchTouch("pointerup", 0.36f, 0.52f);
        pointerDown = false;
        markPointerState("up");
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMobileInputSmoke') === '1';")
    private static native boolean requested();

    @JSBody(params = {"type", "nx", "ny"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        if(!canvas) throw new Error('Mindustry canvas missing for mobile input smoke');
        const rect = canvas.getBoundingClientRect();
        const clientX = rect.left + rect.width * nx;
        const clientY = rect.top + rect.height * ny;
        const active = type !== 'pointerup' && type !== 'pointercancel';
        canvas.dispatchEvent(new PointerEvent(type, {
            pointerId: 51,
            pointerType: 'touch',
            isPrimary: true,
            clientX: clientX,
            clientY: clientY,
            button: type === 'pointermove' ? -1 : 0,
            buttons: active ? 1 : 0,
            bubbles: true,
            cancelable: true
        }));
        """)
    private static native void dispatchTouch(String type, float nx, float ny);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-mobile-input-smoke', 'requested'); document.documentElement.setAttribute('data-mindustry-mobile-input-source', 'dom-touch-pan');")
    private static native void markRequested();

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-mobile-input-smoke', 'waiting-unit'); document.documentElement.setAttribute('data-mindustry-mobile-input-wait-frames', String(frames));")
    private static native void markWaiting(int frames);

    @JSBody(params = {"id", "type", "cx", "cy", "ux", "uy"}, script = "document.documentElement.setAttribute('data-mindustry-mobile-input-smoke', 'touch-down'); document.documentElement.setAttribute('data-mindustry-mobile-input-source', 'dom-touch-pan'); document.documentElement.setAttribute('data-mindustry-mobile-input-pointer-state', 'down'); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-id', String(id)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit', type); document.documentElement.setAttribute('data-mindustry-mobile-input-start-camera-x', String(cx)); document.documentElement.setAttribute('data-mindustry-mobile-input-start-camera-y', String(cy)); document.documentElement.setAttribute('data-mindustry-mobile-input-start-unit-x', String(ux)); document.documentElement.setAttribute('data-mindustry-mobile-input-start-unit-y', String(uy));")
    private static native void markStarted(int id, String type, float cx, float cy, float ux, float uy);

    @JSBody(params = {"stage"}, script = "document.documentElement.setAttribute('data-mindustry-mobile-input-smoke', stage);")
    private static native void markStage(String stage);

    @JSBody(params = {"state"}, script = "document.documentElement.setAttribute('data-mindustry-mobile-input-pointer-state', state);")
    private static native void markPointerState(String state);

    @JSBody(params = {"frames", "cx", "cy", "ux", "uy", "cdx", "cdy", "udx", "udy"}, script = "document.documentElement.setAttribute('data-mindustry-mobile-input-move-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-x', String(cx)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-y', String(cy)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-x', String(ux)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-y', String(uy)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-dx', String(cdx)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-dy', String(cdy)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-dx', String(udx)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-dy', String(udy));")
    private static native void markProgress(int frames, float cx, float cy, float ux, float uy, float cdx, float cdy, float udx, float udy);

    @JSBody(params = {"id", "type", "frames", "cx", "cy", "ux", "uy", "cdx", "cdy", "udx", "udy"}, script = "document.documentElement.setAttribute('data-mindustry-mobile-input-smoke', 'moved'); document.documentElement.setAttribute('data-mindustry-mobile-input-source', 'dom-touch-pan'); document.documentElement.setAttribute('data-mindustry-mobile-input-pointer-state', 'up'); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-id', String(id)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit', type); document.documentElement.setAttribute('data-mindustry-mobile-input-move-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-x', String(cx)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-y', String(cy)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-x', String(ux)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-y', String(uy)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-dx', String(cdx)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-dy', String(cdy)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-dx', String(udx)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-dy', String(udy));")
    private static native void markMoved(int id, String type, int frames, float cx, float cy, float ux, float uy, float cdx, float cdy, float udx, float udy);
}

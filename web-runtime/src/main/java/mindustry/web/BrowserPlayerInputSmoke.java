package mindustry.web;

import arc.*;
import arc.math.geom.*;
import mindustry.gen.*;
import mindustry.input.*;
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
    private static boolean possessionMode;
    private static boolean mobileMode;
    private static boolean controlDown;
    private static boolean pointerDown;
    private static boolean mobilePointerDown;
    private static int possessionStage;
    private static int possessionFrames;
    private static int mobileStage;
    private static int mobileFrames;
    private static int spawnFrames;
    private static int heldFrames;
    private static int unitId = -1;
    private static float startX;
    private static float startY;
    private static float controlScreenX;
    private static float controlScreenY;
    private static float mobileCameraX;

    private BrowserPlayerInputSmoke(){}

    /** Called once after each normal application frame by the staged Web build. */
    public static void update(){
        if(!enabled() || completed) return;

        if(state == null || player == null || !BrowserLocalMapRuntime.active() || !state.isPlaying()){
            releaseKey();
            releasePossessionInput();
            releaseMobileInput();
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

        if(mobileMode){
            updateMobile(unit);
            return;
        }

        if(possessionMode){
            updatePossession(unit);
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
            possessionMode = possessionRequested();
            mobileMode = mobileRequested();
            enabled = requested() || possessionMode || mobileMode;
            if(enabled) markRequested();
            if(possessionMode) markPossessionRequested();
        }
        return enabled;
    }

    private static void updateMobile(Unit unit){
        if(!Core.app.isMobile() || !(control.input instanceof MobileInput) || Core.settings.getBool("keyboard")){
            throw new IllegalStateException("Mobile-input smoke requires stock touch MobileInput");
        }

        if(mobileStage == 0){
            unitId = unit.id;
            startX = unit.x;
            startY = unit.y;
            mobileCameraX = Core.camera.position.x;
            // Queue a real fast swipe as one browser-input burst. Delaying the first
            // move by a whole heavy TeaVM frame can legitimately trigger long-press
            // manual shooting before GestureDetector sees a pan.
            dispatchTouch("pointerdown", 0.62f, 0.52f);
            mobilePointerDown = true;
            dispatchTouch("pointermove", 0.50f, 0.52f);
            dispatchTouch("pointermove", 0.36f, 0.52f);
            mobileStage = 3;
            return;
        }

        if(unit.id != unitId || player.unit() != unit){
            releaseMobileInput();
            throw new IllegalStateException("Mobile-input smoke changed controlled unit during touch drag");
        }

        if(mobileStage == 3){
            MobileInput mobile = (MobileInput)control.input;
            float immediateCameraDx = Math.abs(Core.camera.position.x - mobileCameraX);
            if(immediateCameraDx <= 2f){
                throw new IllegalStateException(
                    "DOM touch drag reached verification frame without MobileInput pan: down=" + mobile.down +
                    ", panning=" + mobile.detector.isPanning() +
                    ", manualShooting=" + mobile.manualShooting +
                    ", locked=" + mobile.locked() +
                    ", commandRect=" + mobile.commandRect +
                    ", lineMode=" + mobile.lineMode +
                    ", schematicMode=" + mobile.schematicMode +
                    ", selecting=" + mobile.selecting +
                    ", droppingItem=" + mobile.droppingItem +
                    ", dialog=" + (Core.scene != null && Core.scene.hasDialog()) +
                    ", touched=" + Core.input.isTouched(0) +
                    ", cameraWidth=" + Core.camera.width +
                    ", mouse=" + Core.input.mouseX() + "," + Core.input.mouseY()
                );
            }
            releaseMobileInput();
            mobileStage = 4;
            return;
        }

        mobileFrames++;
        float cameraDx = Math.abs(Core.camera.position.x - mobileCameraX);
        float unitDx = Math.abs(unit.x - startX);
        if(cameraDx > 2f && unitDx > 0.5f){
            completed = true;
            markMobileMoved(unitId, unit.type.name, mobileFrames, cameraDx, unitDx);
            return;
        }

        if(mobileFrames >= maxHeldFrames){
            throw new IllegalStateException(
                "DOM touch pan did not drive stock MobileInput movement: cameraDx=" +
                cameraDx + ", unitDx=" + unitDx
            );
        }
    }

    private static void updatePossession(Unit unit){
        if(!state.rules.possessionAllowed){
            throw new IllegalStateException("Player-possession smoke requires possessionAllowed rules");
        }

        if(possessionStage == 0){
            Building core = unit.closestCore();
            if(core == null || !core.canControlSelect(unit)){
                throw new IllegalStateException("Player-possession smoke requires a controllable local core");
            }
            unitId = unit.id;
            Vec2 point = Core.camera.project(new Vec2(core.x, core.y));
            controlScreenX = point.x;
            controlScreenY = point.y;
            dispatchPointer("pointermove", controlScreenX, controlScreenY, -1, false);
            possessionStage = 1;
            markPossessionStage("core-hover", unitId);
            return;
        }

        if(possessionStage == 1){
            if(Core.scene.hasMouse()){
                throw new IllegalStateException("Core possession target is covered by an Arc Scene actor");
            }
            if(Math.abs(Core.input.mouseX() - controlScreenX) > 4f || Math.abs(Core.input.mouseY() - controlScreenY) > 4f){
                throw new IllegalStateException("DOM pointermove did not reach the core possession target");
            }
            dispatchControlKey(true);
            controlDown = true;
            possessionStage = 2;
            markPossessionStage("control-down", unitId);
            return;
        }

        if(possessionStage == 2){
            dispatchPointer("pointerdown", controlScreenX, controlScreenY, 0, true);
            pointerDown = true;
            possessionStage = 3;
            markPossessionStage("select-down", unitId);
            return;
        }

        if(possessionStage == 3){
            dispatchPointer("pointerup", controlScreenX, controlScreenY, 0, false);
            pointerDown = false;
            possessionStage = 4;
            markPossessionStage("select-up", unitId);
            return;
        }

        if(possessionStage == 4){
            dispatchControlKey(false);
            controlDown = false;
            possessionStage = 5;
            markPossessionStage("control-up", unitId);
            return;
        }

        Unit current = player.unit();
        if(current != null && current.isAdded() && current.isValid() && current.id != unitId && current.spawnedByCore && current.isPlayer()){
            completed = true;
            markPossessed(unitId, current.id, current.type.name);
            return;
        }

        if(++possessionFrames >= maxHeldFrames){
            releasePossessionInput();
            throw new IllegalStateException(
                "DOM ControlLeft + core click did not complete stock local core possession: old=" +
                unitId + ", current=" + (current == null ? -1 : current.id)
            );
        }
        markPossessionWait(possessionFrames, current == null ? -1 : current.id);
    }

    private static void releasePossessionInput(){
        if(pointerDown){
            dispatchPointer("pointerup", controlScreenX, controlScreenY, 0, false);
            pointerDown = false;
        }
        if(controlDown){
            dispatchControlKey(false);
            controlDown = false;
        }
    }

    private static void releaseKey(){
        if(!keyDown) return;
        dispatchMovementKey(false);
        keyDown = false;
        markKeyState("up");
    }

    private static void releaseMobileInput(){
        if(!mobilePointerDown) return;
        dispatchTouch("pointerup", 0.36f, 0.52f);
        mobilePointerDown = false;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPlayerInputSmoke') === '1';")
    private static native boolean requested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPlayerPossessionSmoke') === '1';")
    private static native boolean possessionRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMobileInputSmoke') === '1';")
    private static native boolean mobileRequested();

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

    @JSBody(params = {"down"}, script = "window.dispatchEvent(new KeyboardEvent(down ? 'keydown' : 'keyup', {code:'ControlLeft', key:'Control', bubbles:true, cancelable:true, repeat:false}));")
    private static native void dispatchControlKey(boolean down);

    @JSBody(params = {"type", "sx", "sy", "button", "down"}, script = """
        const canvas = document.getElementById('mindustry-canvas');
        const rect = canvas.getBoundingClientRect();
        canvas.dispatchEvent(new PointerEvent(type, {
            pointerId: 1, pointerType: 'mouse', isPrimary: true,
            clientX: rect.left + sx, clientY: rect.top + rect.height - sy,
            button: button < 0 ? -1 : button, buttons: down ? 1 : 0,
            bubbles: true, cancelable: true
        }));
        """)
    private static native void dispatchPointer(String type, float sx, float sy, int button, boolean down);

    @JSBody(params = {"type", "nx", "ny"}, script = """
        const c = document.getElementById('mindustry-canvas'), r = c.getBoundingClientRect();
        const active = type !== 'pointerup';
        c.dispatchEvent(new PointerEvent(type, {pointerId:51, pointerType:'touch', isPrimary:true,
            clientX:r.left+r.width*nx, clientY:r.top+r.height*ny, button:type === 'pointermove' ? -1 : 0,
            buttons:active ? 1 : 0, bubbles:true, cancelable:true}));
        """)
    private static native void dispatchTouch(String type, float nx, float ny);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-player-possession-smoke', 'requested'); document.documentElement.setAttribute('data-mindustry-player-possession-source', 'dom-control-click');")
    private static native void markPossessionRequested();

    @JSBody(params = {"id", "type", "frames", "cdx", "udx"}, script = "document.documentElement.setAttribute('data-mindustry-mobile-input-smoke','moved'); document.documentElement.setAttribute('data-mindustry-mobile-input-source','dom-touch-pan'); document.documentElement.setAttribute('data-mindustry-mobile-input-pointer-state','up'); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-id',String(id)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit',type); document.documentElement.setAttribute('data-mindustry-mobile-input-move-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-mobile-input-camera-dx',String(cdx)); document.documentElement.setAttribute('data-mindustry-mobile-input-unit-dx',String(udx));")
    private static native void markMobileMoved(int id, String type, int frames, float cdx, float udx);

    @JSBody(params = {"stage", "oldId"}, script = "document.documentElement.setAttribute('data-mindustry-player-possession-smoke', stage); document.documentElement.setAttribute('data-mindustry-player-possession-old-id', String(oldId));")
    private static native void markPossessionStage(String stage, int oldId);

    @JSBody(params = {"frames", "currentId"}, script = "document.documentElement.setAttribute('data-mindustry-player-possession-wait-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-player-possession-current-id', String(currentId));")
    private static native void markPossessionWait(int frames, int currentId);

    @JSBody(params = {"oldId", "newId", "type"}, script = "document.documentElement.setAttribute('data-mindustry-player-possession-smoke', 'possessed'); document.documentElement.setAttribute('data-mindustry-player-possession-source', 'dom-control-click'); document.documentElement.setAttribute('data-mindustry-player-possession-old-id', String(oldId)); document.documentElement.setAttribute('data-mindustry-player-possession-new-id', String(newId)); document.documentElement.setAttribute('data-mindustry-player-possession-unit', type); document.documentElement.setAttribute('data-mindustry-player-possession-spawned-by-core', 'true');")
    private static native void markPossessed(int oldId, int newId, String type);

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

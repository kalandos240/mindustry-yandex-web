package mindustry.web;

import arc.backend.web.*;
import arc.input.*;
import org.teavm.jso.*;

/** Connects DOM keyboard/pointer/wheel events to {@link WebInput}. */
public final class BrowserInputBridge{
    private BrowserInputBridge(){}

    @JSFunctor
    private interface KeyDownCallback extends JSObject{
        void handle(String code, String key, boolean repeat);
    }

    @JSFunctor
    private interface KeyUpCallback extends JSObject{
        void handle(String code);
    }

    @JSFunctor
    private interface PointerCallback extends JSObject{
        void handle(int pointer, int x, int y, int button);
    }

    @JSFunctor
    private interface PointerMoveCallback extends JSObject{
        void handle(int pointer, int x, int y);
    }

    @JSFunctor
    private interface ScrollCallback extends JSObject{
        void handle(float amountX, float amountY);
    }

    @JSFunctor
    private interface VoidCallback extends JSObject{
        void handle();
    }

    public static void install(String canvasId, WebInput input){
        installNative(
            canvasId,
            (code, key, repeat) -> {
                input.keyDown(BrowserKeymap.fromCode(code), repeat);
                typeKey(input, key);
            },
            code -> input.keyUp(BrowserKeymap.fromCode(code)),
            (pointer, x, y, button) -> input.pointerDown(pointer, x, y, BrowserKeymap.mouseButton(button)),
            (pointer, x, y, button) -> input.pointerUp(pointer, x, y, BrowserKeymap.mouseButton(button)),
            input::pointerMove,
            input::scroll,
            input::releaseAllPointers
        );
    }

    /** Release every browser-held key/pointer at a platform lifecycle boundary. */
    public static void releaseAll(String canvasId, String reason){
        releaseAllNative(canvasId, reason);
    }

    private static void typeKey(WebInput input, String key){
        if(key == null || key.isEmpty()) return;
        if(key.length() == 1){
            input.keyTyped(key.charAt(0));
            return;
        }

        switch(key){
            case "Backspace" -> input.keyTyped((char)8);
            case "Tab" -> input.keyTyped('\t');
            case "Enter" -> input.keyTyped((char)13);
            case "Delete" -> input.keyTyped((char)127);
        }
    }

    @JSBody(params = {"canvasId", "keyDown", "keyUp", "pointerDown", "pointerUp", "pointerMove", "scroll", "releasePointers"}, script = """
        const canvas = document.getElementById(canvasId);
        if (!canvas) throw new Error('Canvas #' + canvasId + ' not found');
        canvas.tabIndex = 0;
        canvas.style.touchAction = 'none';

        const downCodes = new Set();
        const pointerSlots = new Map();
        const pendingMoves = new Map();
        const movePoints = Array.from({length: 10}, () => [0, 0]);
        const eventPoint = [0, 0];
        let moveFramePending = false;

        const flushMoves = () => {
            moveFramePending = false;
            pendingMoves.forEach((p, slot) => pointerMove(slot, p[0], p[1]));
            pendingMoves.clear();
        };

        const scheduleMoveFlush = () => {
            if(moveFramePending) return;
            moveFramePending = true;
            requestAnimationFrame(flushMoves);
        };

        const coordsInto = (event, out) => {
            // The game canvas is fixed to the viewport. PointerEvent offset coordinates are
            // already target-relative, so avoid layout reads and per-event coordinate arrays.
            const width = Math.max(1, canvas.__mindustryClientWidth | 0);
            const height = Math.max(1, canvas.__mindustryClientHeight | 0);
            const ox = Number.isFinite(event.offsetX) ? event.offsetX : event.clientX;
            const oy = Number.isFinite(event.offsetY) ? event.offsetY : event.clientY;
            out[0] = Math.max(0, Math.min(width, Math.floor(ox)));
            out[1] = Math.max(0, Math.min(height, Math.floor(height - oy)));
            return out;
        };

        const findSlot = (event, create) => {
            if (event.pointerType === 'mouse') return 0;
            if (pointerSlots.has(event.pointerId)) return pointerSlots.get(event.pointerId);
            if (!create) return -1;
            const used = new Set(pointerSlots.values());
            for (let i = 0; i < 10; i++) {
                if (!used.has(i)) {
                    pointerSlots.set(event.pointerId, i);
                    return i;
                }
            }
            return -1;
        };

        const shouldPreventKey = event => {
            if (event.ctrlKey || event.metaKey) return false;
            return event.code === 'Space' || event.code === 'Tab' || event.code === 'Backspace' ||
                event.code === 'ArrowUp' || event.code === 'ArrowDown' || event.code === 'ArrowLeft' || event.code === 'ArrowRight';
        };

        window.addEventListener('keydown', event => {
            downCodes.add(event.code);
            keyDown(event.code || '', event.key || '', !!event.repeat);
            if (shouldPreventKey(event)) event.preventDefault();
        }, {passive: false});

        window.addEventListener('keyup', event => {
            downCodes.delete(event.code);
            keyUp(event.code || '');
            if (shouldPreventKey(event)) event.preventDefault();
        }, {passive: false});

        const releaseAllInput = reason => {
            downCodes.forEach(function(code){ keyUp(code); });
            downCodes.clear();

            // Always clear Arc pointer state. Mouse pointer 0 is not stored in
            // pointerSlots, so a map-size guard would leave mouse/touch state stuck
            // after an ad, tab switch or focus loss.
            releasePointers();
            pointerSlots.clear();
            pendingMoves.clear();

            const root = document.documentElement;
            const count = Number(root.getAttribute('data-mindustry-input-reset-count') || '0') + 1;
            root.setAttribute('data-mindustry-input-reset-count', String(count));
            root.setAttribute('data-mindustry-input-reset', String(reason || 'unknown'));
            root.setAttribute('data-mindustry-pointer-reset', String(reason || 'unknown'));
        };

        canvas.__mindustryReleaseInput = releaseAllInput;
        window.addEventListener('blur', () => releaseAllInput('blur'));

        canvas.addEventListener('pointerdown', event => {
            const slot = findSlot(event, true);
            if (slot < 0) return;
            const p = coordsInto(event, eventPoint);
            pendingMoves.delete(slot);
            try { canvas.setPointerCapture(event.pointerId); } catch (_) {}
            canvas.focus({preventScroll: true});
            pointerDown(slot, p[0], p[1], event.button | 0);
            event.preventDefault();
        }, {passive: false});

        canvas.addEventListener('pointermove', event => {
            const slot = findSlot(event, false);
            if (slot < 0) return;
            const p = movePoints[slot];
            coordsInto(event, p);
            pendingMoves.set(slot, p);
            scheduleMoveFlush();
            event.preventDefault();
        }, {passive: false});

        const finishPointer = event => {
            const slot = findSlot(event, false);
            if (slot < 0) return;
            const p = coordsInto(event, eventPoint);
            // Preserve the final drag coordinate even when several DOM moves were
            // collapsed into this browser frame.
            pendingMoves.delete(slot);
            pointerMove(slot, p[0], p[1]);
            pointerUp(slot, p[0], p[1], event.button | 0);
            if (event.pointerType !== 'mouse') pointerSlots.delete(event.pointerId);
            event.preventDefault();
        };
        canvas.addEventListener('pointerup', finishPointer, {passive: false});
        canvas.addEventListener('pointercancel', finishPointer, {passive: false});

        canvas.addEventListener('wheel', event => {
            const scale = event.deltaMode === 1 ? 1 : event.deltaMode === 2 ? 3 : 0.01;
            scroll(event.deltaX * scale, event.deltaY * scale);
            event.preventDefault();
        }, {passive: false});

        canvas.addEventListener('contextmenu', event => event.preventDefault());
        document.documentElement.dataset.mindustryInput = 'ready';
        document.documentElement.setAttribute('data-mindustry-input-coordinates', 'offset-cached');
        document.documentElement.setAttribute('data-mindustry-input-move-policy', 'raf-coalesced');
        document.documentElement.setAttribute('data-mindustry-input-move-buffer', 'reused-slot');
        """)
    private static native void installNative(String canvasId, KeyDownCallback keyDown, KeyUpCallback keyUp,
                                              PointerCallback pointerDown, PointerCallback pointerUp,
                                              PointerMoveCallback pointerMove, ScrollCallback scroll,
                                              VoidCallback releasePointers);

    @JSBody(params = {"canvasId", "reason"}, script = """
        const canvas = document.getElementById(canvasId);
        if(canvas && typeof canvas.__mindustryReleaseInput === 'function'){
            canvas.__mindustryReleaseInput(reason);
        }
        """)
    private static native void releaseAllNative(String canvasId, String reason);
}

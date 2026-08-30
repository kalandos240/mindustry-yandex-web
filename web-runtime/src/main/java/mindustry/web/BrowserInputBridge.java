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
            input::scroll
        );
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

    @JSBody(params = {"canvasId", "keyDown", "keyUp", "pointerDown", "pointerUp", "pointerMove", "scroll"}, script = """
        const canvas = document.getElementById(canvasId);
        if (!canvas) throw new Error('Canvas #' + canvasId + ' not found');
        canvas.tabIndex = 0;
        canvas.style.touchAction = 'none';

        const downCodes = new Set();
        const pointerSlots = new Map();

        const coords = event => {
            const rect = canvas.getBoundingClientRect();
            const x = Math.max(0, Math.min(canvas.clientWidth, Math.floor(event.clientX - rect.left)));
            const y = Math.max(0, Math.min(canvas.clientHeight, Math.floor(canvas.clientHeight - (event.clientY - rect.top))));
            return [x, y];
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

        window.addEventListener('blur', () => {
            downCodes.forEach(function(code){ keyUp(code); });
            downCodes.clear();
        });

        canvas.addEventListener('pointerdown', event => {
            const slot = findSlot(event, true);
            if (slot < 0) return;
            const p = coords(event);
            try { canvas.setPointerCapture(event.pointerId); } catch (_) {}
            canvas.focus({preventScroll: true});
            pointerDown(slot, p[0], p[1], event.button | 0);
            event.preventDefault();
        }, {passive: false});

        canvas.addEventListener('pointermove', event => {
            const slot = findSlot(event, false);
            if (slot < 0) return;
            const p = coords(event);
            pointerMove(slot, p[0], p[1]);
            event.preventDefault();
        }, {passive: false});

        const finishPointer = event => {
            const slot = findSlot(event, false);
            if (slot < 0) return;
            const p = coords(event);
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
        """)
    private static native void installNative(String canvasId, KeyDownCallback keyDown, KeyUpCallback keyUp,
                                              PointerCallback pointerDown, PointerCallback pointerUp,
                                              PointerMoveCallback pointerMove, ScrollCallback scroll);
}

package arc.backend.web;

import arc.*;
import arc.input.*;
import arc.math.geom.*;
import arc.struct.*;

/** Browser-neutral Arc input state. DOM events are fed into this class by the TeaVM layer. */
public class WebInput extends Input{
    private static final int maxPointers = 10;

    private final InputEventQueue queue = new InputEventQueue();

    // First-run campaign UI diagnostic only. Preserve normal gameplay
    // dispatch unchanged. During the stock chooser, record the ordered
    // recipient that consumes each real queued pointerdown; this pinpoints
    // whether an event is lost before it reaches Arc Scene.
    private final InputMultiplexer firstRunRouteProbe = new InputMultiplexer(inputMultiplexer){
        @Override
        public boolean touchDown(int sx, int sy, int pointer, KeyCode button){
            Vec2 at = new Vec2(sx, sy);
            Core.scene.screenToStageCoordinates(at);
            arc.scene.Element hit = Core.scene.hit(at.x, at.y, true);
            String hitName = hit == null ? "null" :
                hit.name != null ? hit.name : hit.getClass().getSimpleName();

            SnapshotSeq<InputProcessor> processors = inputMultiplexer.getProcessors();
            Object[] snapshot = processors.begin();
            StringBuilder route = new StringBuilder();
            boolean consumed = false;
            try{
                for(int i = 0, n = processors.size; i < n; i++){
                    InputProcessor handler = (InputProcessor)snapshot[i];
                    boolean handled = handler.touchDown(sx, sy, pointer, button);
                    if(i > 0) route.append('>');
                    route.append(handler.getClass().getSimpleName());
                    if(handled){
                        route.append(":handled");
                        consumed = true;
                        break;
                    }
                }
            }finally{
                processors.end();
            }
            if(Core.settings != null){
                Core.settings.put("web-first-queue-route",
                    sx + "," + sy + " hit=" + hitName + " stage=" +
                    Math.round(at.x) + "," + Math.round(at.y) + " via=" + route);
            }
            return consumed;
        }
    };
    private final int[] x = new int[maxPointers], y = new int[maxPointers];
    private final int[] deltaX = new int[maxPointers], deltaY = new int[maxPointers];
    private final int[] pressed = new int[maxPointers];
    private boolean justTouched;
    private int firstRunEnqueued, firstRunDrained;

    /** Observe queued native events only after the first-run portrait was picked. */
    private boolean firstRunClickPending(){
        return Core.settings != null &&
            !"none".equals(Core.settings.getString("web-first-choice", "none")) &&
            "none".equals(Core.settings.getString("web-first-ok", "none"));
    }

    /** Drain queued DOM events before the application update. */
    public void update(){
        // The extra multiplexer is used only while the original first-run
        // chooser is displayed; outside that modal, the exact existing
        // input queue and dispatch path remain unchanged.
        boolean firstRunChooser = Core.scene != null && Core.scene.getDialog() != null
            && Core.scene.getDialog().find("web-campaign-first-confirm") != null;
        queue.setProcessor(firstRunChooser ? firstRunRouteProbe : inputMultiplexer);
        int pendingId = firstRunEnqueued;
        queue.drain();
        if(pendingId != firstRunDrained){
            firstRunDrained = pendingId;
            if(Core.settings != null){
                Core.settings.put("web-first-queue-drained",
                    pendingId + ":dialog=" + firstRunChooser + ":processor=" +
                    (firstRunChooser ? "chooser-probe" : "normal-scene"));
            }
        }
    }

    /** Clear one-frame input state after the application update. */
    public void postUpdate(){
        keyboard.postUpdate();
        justTouched = false;
        for(int i = 0; i < maxPointers; i++){
            deltaX[i] = 0;
            deltaY[i] = 0;
        }
    }

    public void keyDown(KeyCode key, boolean repeat){
        if(key == null || key == KeyCode.unknown || repeat) return;
        queue.keyDown(key);
    }

    public void keyUp(KeyCode key){
        if(key == null || key == KeyCode.unknown) return;
        queue.keyUp(key);
    }

    public void keyTyped(char character){
        if(character != 0) queue.keyTyped(character);
    }

    public void pointerDown(int pointer, int screenX, int screenY, KeyCode button){
        pointer = pointer(pointer);
        updatePointer(pointer, screenX, screenY);
        pressed[pointer]++;
        justTouched = true;
        queue.touchDown(x[pointer], y[pointer], pointer, button == null ? KeyCode.mouseLeft : button);
        if(firstRunClickPending()){
            firstRunEnqueued++;
            Core.settings.put("web-first-queue-enqueued",
                firstRunEnqueued + ":" + x[pointer] + "," + y[pointer] +
                ":pointer=" + pointer + ":button=" + button);
        }
    }

    public void pointerUp(int pointer, int screenX, int screenY, KeyCode button){
        pointer = pointer(pointer);
        updatePointer(pointer, screenX, screenY);
        pressed[pointer] = Math.max(0, pressed[pointer] - 1);
        queue.touchUp(x[pointer], y[pointer], pointer, button == null ? KeyCode.mouseLeft : button);
    }

    public void pointerMove(int pointer, int screenX, int screenY){
        pointer = pointer(pointer);
        updatePointer(pointer, screenX, screenY);
        if(pressed[pointer] > 0){
            queue.touchDragged(x[pointer], y[pointer], pointer);
        }else if(pointer == 0){
            queue.mouseMoved(x[pointer], y[pointer]);
        }
    }

    /**
     * Release every pointer still considered pressed by Arc. Browsers normally emit
     * pointercancel when a system gesture takes ownership, but focus loss/navigation can
     * happen without a matching pointerup. Leaving pressed[] set would make isTouched()
     * remain true forever after returning to the game.
     */
    public void releaseAllPointers(){
        for(int pointer = 0; pointer < maxPointers; pointer++){
            if(pressed[pointer] <= 0) continue;
            pressed[pointer] = 0;
            queue.touchUp(x[pointer], y[pointer], pointer, KeyCode.mouseLeft);
        }
    }

    public void scroll(float amountX, float amountY){
        queue.scrolled(amountX, amountY);
    }

    private void updatePointer(int pointer, int nextX, int nextY){
        deltaX[pointer] += nextX - x[pointer];
        deltaY[pointer] += nextY - y[pointer];
        x[pointer] = nextX;
        y[pointer] = nextY;
    }

    private int pointer(int pointer){
        return Math.max(0, Math.min(maxPointers - 1, pointer));
    }

    @Override
    public int mouseX(){
        return x[0];
    }

    @Override
    public int mouseX(int pointer){
        return x[pointer(pointer)];
    }

    @Override
    public int deltaX(){
        return deltaX[0];
    }

    @Override
    public int deltaX(int pointer){
        return deltaX[pointer(pointer)];
    }

    @Override
    public int mouseY(){
        return y[0];
    }

    @Override
    public int mouseY(int pointer){
        return y[pointer(pointer)];
    }

    @Override
    public int deltaY(){
        return deltaY[0];
    }

    @Override
    public int deltaY(int pointer){
        return deltaY[pointer(pointer)];
    }

    @Override
    public boolean isTouched(){
        for(int count : pressed){
            if(count > 0) return true;
        }
        return false;
    }

    @Override
    public boolean justTouched(){
        return justTouched;
    }

    @Override
    public boolean isTouched(int pointer){
        return pressed[pointer(pointer)] > 0;
    }

    @Override
    public long getCurrentEventTime(){
        return queue.getCurrentEventTime();
    }
}

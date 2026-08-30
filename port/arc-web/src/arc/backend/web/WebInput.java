package arc.backend.web;

import arc.*;
import arc.input.*;

/** Browser-neutral Arc input state. DOM events are fed into this class by the TeaVM layer. */
public class WebInput extends Input{
    private static final int maxPointers = 10;

    private final InputEventQueue queue = new InputEventQueue();
    private final int[] x = new int[maxPointers], y = new int[maxPointers];
    private final int[] deltaX = new int[maxPointers], deltaY = new int[maxPointers];
    private final int[] pressed = new int[maxPointers];
    private boolean justTouched;

    /** Drain queued DOM events before the application update. */
    public void update(){
        queue.setProcessor(inputMultiplexer);
        queue.drain();
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

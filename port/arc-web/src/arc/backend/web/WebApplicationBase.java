package arc.backend.web;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;

/**
 * Platform-neutral part of the browser application lifecycle.
 *
 * DOM, clipboard, graphics, input, audio and persistence are intentionally left
 * to a concrete browser implementation. Keeping the frame loop here prevents
 * those browser bindings from being mixed with game lifecycle semantics.
 */
public abstract class WebApplicationBase implements Application{
    private final Seq<ApplicationListener> listeners = new Seq<>();
    private final TaskQueue runnables = new TaskQueue();

    protected final WebConfig config;
    private boolean initialized;
    private boolean running = true;

    protected WebApplicationBase(ApplicationListener listener, WebConfig config){
        this.config = config;
        listeners.add(listener);
    }

    /** Called after browser platform services have been installed into {@link Core}. */
    protected final void initialize(){
        if(initialized) return;
        Core.app = this;
        initialized = true;
        listen(ApplicationListener::init);
    }

    /** Called by requestAnimationFrame (or an equivalent browser scheduler). */
    public final void frame(){
        if(!running) return;
        if(!initialized) initialize();

        if(Core.settings == null){
            Time.updateGlobal();
        }else{
            defaultUpdate();
        }

        listen(ApplicationListener::update);
        runnables.run();
    }

    public final void resize(int width, int height){
        if(!initialized) return;
        listen(listener -> listener.resize(width, height));
    }

    public final void pause(){
        if(!initialized) return;
        listen(ApplicationListener::pause);
    }

    public final void resume(){
        if(!initialized) return;
        listen(ApplicationListener::resume);
    }

    protected final boolean isRunning(){
        return running;
    }

    protected final void listen(Cons<ApplicationListener> action){
        synchronized(listeners){
            for(ApplicationListener listener : listeners){
                action.get(listener);
            }
        }
    }

    @Override
    public Seq<ApplicationListener> getListeners(){
        return listeners;
    }

    @Override
    public ApplicationType getType(){
        return ApplicationType.web;
    }

    @Override
    public void post(Runnable runnable){
        runnables.post(runnable);
    }

    @Override
    public void exit(){
        if(!running) return;
        running = false;
        if(initialized) listen(ApplicationListener::exit);
    }
}

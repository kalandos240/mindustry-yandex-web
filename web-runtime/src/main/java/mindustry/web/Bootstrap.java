package mindustry.web;

import arc.*;
import arc.backend.web.*;
import arc.graphics.*;
import mindustry.core.*;

/** First executable bridge between current Mindustry/Arc bytecode and a browser frame loop. */
public final class Bootstrap{
    private Bootstrap(){}

    public static void main(String[] args){
        WebConfig config = new WebConfig();

        new BrowserApplication(new ApplicationListener(){
            private int frames;

            @Override
            public void init(){
                BrowserCanvas.setStatus("initialized", "Mindustry core + Arc Web initialized; waiting for animation frames...");
            }

            @Override
            public void update(){
                double seconds = System.currentTimeMillis() / 1000.0;
                float pulse = 0.08f + 0.03f * (float)(Math.sin(seconds) * 0.5 + 0.5);
                Core.graphics.clear(pulse, pulse, pulse + 0.02f, 1f);

                if(++frames == 3){
                    String glVersion = Core.gl20.glGetString(GL20.GL_VERSION);
                    BrowserCanvas.setStatus("ready", "Mindustry core " + Version.buildString() + " + Arc GL20 ready: " + glVersion);
                }
            }
        }, config);
    }
}

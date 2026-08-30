package mindustry.web;

import arc.*;
import arc.backend.web.*;
import arc.graphics.*;

/** First executable bridge between the current Arc lifecycle and a real browser frame loop. */
public final class Bootstrap{
    private Bootstrap(){}

    public static void main(String[] args){
        WebConfig config = new WebConfig();

        new BrowserApplication(new ApplicationListener(){
            private int frames;

            @Override
            public void init(){
                BrowserCanvas.setStatus("initialized", "Arc Web initialized; waiting for animation frames...");
            }

            @Override
            public void update(){
                double seconds = System.currentTimeMillis() / 1000.0;
                float pulse = 0.08f + 0.03f * (float)(Math.sin(seconds) * 0.5 + 0.5);
                Core.graphics.clear(pulse, pulse, pulse + 0.02f, 1f);

                if(++frames == 3){
                    String version = Core.gl20.glGetString(GL20.GL_VERSION);
                    BrowserCanvas.setStatus("ready", "Arc GL20 + requestAnimationFrame ready: " + version);
                }
            }
        }, config);
    }
}

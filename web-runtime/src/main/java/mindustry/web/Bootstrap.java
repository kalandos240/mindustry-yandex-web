package mindustry.web;

import arc.*;
import arc.backend.web.*;

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
                BrowserCanvas.clearSmokeFrame(config.canvasId, System.currentTimeMillis() / 1000.0);
                if(++frames == 3){
                    BrowserCanvas.setStatus("ready", "Arc requestAnimationFrame + WebGL runtime ready");
                }
            }
        }, config);
    }
}

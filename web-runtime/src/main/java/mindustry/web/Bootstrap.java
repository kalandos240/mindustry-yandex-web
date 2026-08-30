package mindustry.web;

import arc.backend.web.*;
import org.teavm.jso.dom.html.*;

/** First executable bridge between current Arc code and the browser toolchain. */
public final class Bootstrap{
    private Bootstrap(){}

    public static void main(String[] args){
        WebConfig config = new WebConfig();
        HTMLDocument document = HTMLDocument.current();
        var status = document.createElement("div");
        status.appendChild(document.createTextNode(
            "Mindustry Web runtime ready: " + config.width + "x" + config.height
        ));
        document.getBody().appendChild(status);
    }
}

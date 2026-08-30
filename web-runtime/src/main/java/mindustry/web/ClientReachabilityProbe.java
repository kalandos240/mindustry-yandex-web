package mindustry.web;

import mindustry.ClientLauncher;
import org.teavm.jso.JSBody;

/**
 * Forces TeaVM dependency analysis through the real Mindustry client startup graph
 * without executing that incomplete path in the normal browser smoke runtime.
 */
public final class ClientReachabilityProbe{
    private ClientReachabilityProbe(){}

    public static void link(){
        ClientLauncher launcher = new ClientLauncher(){};
        if(fullClientProbeEnabled()){
            launcher.setup();
        }
    }

    @JSBody(script = "return window.__mindustryRunFullClientProbe === true;")
    private static native boolean fullClientProbeEnabled();
}

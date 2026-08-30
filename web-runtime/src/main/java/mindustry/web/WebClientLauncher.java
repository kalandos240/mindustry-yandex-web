package mindustry.web;

import mindustry.*;
import mindustry.net.Net.*;

/** Concrete Mindustry client platform boundary for the TeaVM browser target. */
public final class WebClientLauncher extends ClientLauncher{
    private final NetProvider netProvider = new WebNetProvider();

    @Override
    public NetProvider getNet(){
        return netProvider;
    }
}

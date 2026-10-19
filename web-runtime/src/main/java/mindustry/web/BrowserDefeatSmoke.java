package mindustry.web;

import org.teavm.jso.JSBody;
import static mindustry.Vars.*;

/** Opt-in real core destruction; the normal loss code and input handle the rest. */
final class BrowserDefeatSmoke{
    private static boolean checked, enabled, fired;
    private static int frames;
    static void update(){
        if(!checked){ enabled = requested(); checked = true; }
        if(!enabled || fired || !state.isPlaying() || state.gameOver) return;
        if(++frames < 30 || state.rules.defaultTeam.core() == null) return;
        fired = true;
        state.rules.defaultTeam.cores().copy().each(core -> core.kill());
    }
    @JSBody(script="return new URLSearchParams(location.search).get('mindustryDefeatSmoke') === '1';")
    private static native boolean requested();
}

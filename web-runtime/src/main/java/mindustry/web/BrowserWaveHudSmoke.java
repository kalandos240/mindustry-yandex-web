package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Opt-in Chrome verification of the ORIGINAL Mindustry HudFragment "skip"
 * wave button. Never invokes logic.skipWave() or spawns any enemies itself.
 * The real user click must advance GameState.wave through vanilla
 * HudFragment -> Logic.runWave -> WaveSpawner.spawnEnemies().
 */
public final class BrowserWaveHudSmoke{
    private static boolean checked, enabled, passed;
    private static int frames, startWave = -1, waitingForClickFrames;

    private BrowserWaveHudSmoke(){}

    public static void update(){
        if(!requested() || passed || state == null || !state.isPlaying()
        || state.gameOver || !BrowserCampaignRuntime.active()
        || Core.scene == null || state.rules == null) return;

        if(++frames > 1100){
            throw new IllegalStateException("Stock HUD wave button made no progress: wave="
                + state.wave + ", start=" + startWave + ", timer="
                + state.rules.waveTimer + ", sending=" + state.rules.waveSending
                + ", enemies=" + state.enemies);
        }
        if(!state.rules.waves){
            throw new IllegalStateException("Live sector disabled waves: " + state.rules.sector);
        }

        if(startWave >= 0){
            if(state.wave > startWave){
                passed = true;
                markPassed(state.wave, state.enemies, state.rules.waveTimer);
                return;
            }
            if(++waitingForClickFrames > 560){
                throw new IllegalStateException("Native wave click never advanced GameState.wave: "
                    + startWave + ", enemies=" + state.enemies);
            }
            return;
        }

        // If the selected map still has enemies from its prior wave, wait for
        // them to be defeated. This is the original waitEnemies rule, not a
        // browser-specific override that would break campaign objectives.
        if(state.enemies != 0 || spawner == null || spawner.isSpawning()) return;
        Element candidate = Core.scene.root.find("skip");
        if(!(candidate instanceof Button button) || button.isDisabled()
        || !button.visible || button.getWidth() < 10f || button.getHeight() < 10f)
            return;
        Vec2 center = button.localToStageCoordinates(new Vec2(
            button.getWidth() / 2f, button.getHeight() / 2f));
        Element hit = Core.scene.hit(center.x, center.y, true);
        if(hit != button && (hit == null || !hit.isDescendantOf(button))) return;
        Vec2 coords = Core.scene.getViewport().project(center);
        if(coords.x < 0 || coords.y < 0 ||
            coords.x >= Core.graphics.getWidth() ||
            coords.y >= Core.graphics.getHeight()) return;
        startWave = state.wave;
        markReady(startWave, coords.x, coords.y, state.rules.waveTimer,
            state.rules.waveSending);
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryWaveHudSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params={"wave","x","y","timer","sending"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-wave-hud-ready','yes');
        r.setAttribute('data-mindustry-wave-hud-start',String(wave));
        r.setAttribute('data-mindustry-wave-hud-x',String(x));
        r.setAttribute('data-mindustry-wave-hud-y',String(y));
        r.setAttribute('data-mindustry-wave-hud-timer',timer?'on':'off');
        r.setAttribute('data-mindustry-wave-hud-sending',sending?'on':'off');
        """)
    private static native void markReady(int wave,float x,float y,boolean timer,boolean sending);

    @JSBody(params={"wave","enemies","timer"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-wave-hud-result','passed');
        r.setAttribute('data-mindustry-wave-hud-end',String(wave));
        r.setAttribute('data-mindustry-wave-hud-enemies',String(enemies));
        r.setAttribute('data-mindustry-wave-hud-timer-after',timer?'on':'off');
        """)
    private static native void markPassed(int wave,int enemies,boolean timer);
}

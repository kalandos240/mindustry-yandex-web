package mindustry.web;

import arc.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import mindustry.game.EventType.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.fragments.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * First step in restoring the stock in-game HUD without invoking the desktop-only
 * UI.init() dialog graph. Reuses actual Mindustry Minimap, MinimapFragment and
 * CoreItemsDisplay; the wave/mission panel reads the original GameState and Rules.
 *
 * The lightweight Yandex navigation/pause controls remain until the full stock
 * HudFragment can be mounted with its dependent dialogs.
 */
public final class BrowserHudEssentials{
    private static boolean initialized;
    private static int frameCount;

    private BrowserHudEssentials(){}

    public static void install(Group parent){
        if(initialized) return;
        if(parent == null || ui == null || ui.hudfrag == null || renderer == null
            || renderer.minimap == null || player == null){
            throw new IllegalStateException("Vanilla Web HUD requires renderer, stock HUD and local player");
        }

        // Minimap's click handler opens the original full-screen map. The Web
        // patch keeps its focus handling independent of the absent chat dialog.
        if(ui.minimapfrag == null) ui.minimapfrag = new MinimapFragment();
        ui.minimapfrag.build(parent);

        Table minimap = new Table();
        minimap.name = "web-hud-minimap-root";
        minimap.setFillParent(true);
        minimap.top().right().marginTop(mobile ? 125f : 5f).marginRight(5f);
        minimap.touchable = Touchable.childrenOnly;
        minimap.visible(() -> state != null && state.isGame() && !state.gameOver
            && ui.hudfrag.shown && Core.settings.getBool("minimap", true));
        minimap.add(new Minimap()).name("web-hud-minimap");
        parent.addChild(minimap);

        // CoreItemsDisplay is an original Mindustry class: only items held by
        // the current team's core are shown, with vanilla item icons/counts.
        Table items = new Table();
        items.name = "web-hud-core-items-root";
        items.setFillParent(true);
        items.touchable = Touchable.childrenOnly;
        if(mobile){
            items.bottom().left().marginLeft(5f).marginBottom(5f);
        }else{
            items.top().marginTop(5f);
        }
        items.visible(() -> state != null && state.isGame() && !state.gameOver
            && ui.hudfrag.shown && Core.settings.getBool("coreitems", true));
        items.add(ui.hudfrag.coreItems).name("web-hud-core-items");
        parent.addChild(items);

        Table waves = new Table();
        waves.name = "web-hud-status-root";
        waves.setFillParent(true);
        waves.top().left().marginTop(mobile ? 195f : 145f).marginLeft(5f);
        waves.touchable = Touchable.childrenOnly;
        waves.visible(() -> state != null && state.isGame() && !state.gameOver && ui.hudfrag.shown);
        waves.table(Styles.black6, panel -> {
            panel.margin(5f);
            panel.labelWrap(BrowserHudEssentials::statusText).width(mobile ? 174f : 218f).left()
                .name("web-hud-wave-status");
            // Same eligibility predicate as vanilla HudFragment.canSkipWave(), but
            // no network admin branch is required in Yandex single-player mode.
            panel.button(Icon.play, () -> {
                if(canSkipWave()) logic.skipWave();
            }).size(40f).disabled(b -> !canSkipWave()).name("web-hud-skip-wave");
        }).left();
        waves.update(() -> {
            if(state != null && state.isGame() && (++frameCount & 31) == 0){
                boolean textured = renderer != null && renderer.minimap != null
                    && renderer.minimap.getRegion() != null;
                markLive(textured, state.wave, state.enemies);
            }
        });
        parent.addChild(waves);

        Events.on(ResetEvent.class, event -> ui.hudfrag.coreItems.resetUsed());

        initialized = true;
        markMounted();
    }

    private static boolean canSkipWave(){
        return state != null && state.isPlaying() && state.rules != null &&
            state.rules.waves && state.rules.waveSending && !net.active()
            && state.enemies == 0 && spawner != null && !spawner.isSpawning();
    }

    private static String statusText(){
        markStatusPhase("entry");
        if(state == null || !state.isGame() || state.rules == null) return "";
        if(state.rules.mission != null && !state.rules.mission.isEmpty()){
            return state.rules.mission;
        }
        markStatusPhase("objectives");
        for(var objective : state.rules.objectives){
            if(!objective.hidden && objective.qualified()){
                String text = objective.text();
                if(text != null && !text.isEmpty()) return UI.formatIcons(text);
            }
        }
        if(!state.rules.waves){
            if(state.rules.attackMode){
                int cores = state.teams.present.sum(t -> t.team != player.team() ? t.cores.size : 0);
                return localized(cores == 1 ? "wave.enemycore" : "wave.enemycores", cores);
            }
            return Core.bundle.get("sector.curcapture", "Sector");
        }

        markStatusPhase("wave");
        String wave = state.rules.winWave > 1
            ? localized("wave.cap", state.wave, state.rules.winWave)
            : localized("wave", state.wave);
        int seconds = (int)Math.max(0, Math.ceil(state.wavetime / 60f));
        String remaining = (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
        markStatusPhase("enemies");
        String enemies = state.enemies == 1
            ? localized("wave.enemy", state.enemies)
            : localized("wave.enemies", state.enemies);
        String status = wave + "\n" + enemies + "\n" + localized("wave.waiting", remaining);
        markStatusPhase("complete");
        return status;
    }

    // Avoid TeaVM's java.text.MessageFormat currency-data path on every HUD frame.
    // Stock .properties placeholders only need two numeric/text positional args.
    private static String localized(String key, Object value){
        return Core.bundle.get(key, key).replace("{0}", String.valueOf(value));
    }

    private static String localized(String key, Object first, Object second){
        return localized(key, first).replace("{1}", String.valueOf(second));
    }

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-hud-status-phase',phase);")
    private static native void markStatusPhase(String phase);

    @JSBody(script = "const r=document.documentElement;r.setAttribute('data-mindustry-hud-essentials','ready');r.setAttribute('data-mindustry-hud-minimap','stock-mindustry-ui-Minimap');r.setAttribute('data-mindustry-hud-minimap-overlay','stock-MiniMapFragment');r.setAttribute('data-mindustry-hud-coreitems','stock-CoreItemsDisplay');r.setAttribute('data-mindustry-hud-status','game-state');r.setAttribute('data-mindustry-hud-skip-wave','stock-rule-guarded');")
    private static native void markMounted();

    @JSBody(params = {"textured", "wave", "enemies"}, script = "const r=document.documentElement;r.setAttribute('data-mindustry-hud-minimap-texture',textured?'ready':'pending');r.setAttribute('data-mindustry-hud-live-wave',String(wave));r.setAttribute('data-mindustry-hud-live-enemies',String(enemies));")
    private static native void markLive(boolean textured, int wave, int enemies);
}

package mindustry.web;

import arc.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import mindustry.game.EventType.*;
import mindustry.core.*;
import mindustry.graphics.*;
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
        // Restore the position and health indicators adjacent to the stock map.
        // These values come from the actual player Unit, not browser estimates.
        minimap.row();
        minimap.label(() -> player.tileX() + "," + player.tileY())
            .style(Styles.outlineLabel)
            .visible(() -> Core.settings.getBool("position", true))
            .name("web-hud-position");
        minimap.row();
        minimap.add(new Bar(
            () -> player.dead() ? Core.bundle.get("respawning", "Respawning") : player.unit().type().localizedName,
            () -> Pal.health,
            () -> player.dead() ? 0f : player.unit().healthf()
        )).width(mobile ? 150f : 180f).height(27f).padTop(4f).name("web-hud-player-health");
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

        // Original guardian health feedback, independent of the core-items
        // visibility setting and using the actual boss unit health.
        Table bosses = new Table();
        bosses.name = "web-hud-guardian-root";
        bosses.setFillParent(true);
        bosses.top().marginTop(mobile ? 50f : 85f);
        bosses.touchable = Touchable.disabled;
        bosses.visible(() -> state != null && state.isGame() && state.rules != null
            && state.rules.waves && state.boss() != null
            && !(mobile && Core.graphics.isPortrait()) && ui.hudfrag.shown);
        bosses.add(new Bar(
            () -> Core.bundle.get("guardian", "Guardian"),
            () -> Pal.health,
            () -> {
                float health = 0f, maximum = 0f;
                for(var boss : state.teams.bosses){
                    maximum += boss.maxHealth;
                    health += boss.health;
                }
                return maximum <= 0f ? 0f : health / maximum;
            }
        )).width(mobile ? 220f : 320f).height(mobile ? 34f : 52f)
            .name("web-hud-guardian-health");
        parent.addChild(bosses);

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
        // Vanilla displays every qualified objective. Returning after the first
        // hides concurrent build/production/research missions on Erekir.
        StringBuilder objectives = new StringBuilder();
        for(var objective : state.rules.objectives){
            if(objective.hidden || !objective.qualified()) continue;
            String objectiveText;
            try{
                objectiveText = objective.text();
            }catch(IllegalArgumentException badFormat){
                // TeaVM MessageFormat currency data may fail for some locales.
                // Treat this as an individual label fallback, not a lost mission.
                markObjectiveFallback(objective.typeName());
                objectiveText = fallbackObjectiveText(objective);
            }
            if(objectiveText != null && !objectiveText.isEmpty()){
                if(objectives.length() > 0) objectives.append("\n[white]");
                objectives.append(UI.formatIcons(objectiveText));
            }
        }
        if(objectives.length() > 0) return objectives.toString();
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
        StringBuilder status = new StringBuilder(wave);
        if(state.enemies > 0){
            status.append("\n").append(state.enemies == 1
                ? localized("wave.enemy", state.enemies)
                : localized("wave.enemies", state.enemies));
        }
        if(state.rules.waveTimer){
            status.append("\n").append(logic.isWaitingWave()
                ? Core.bundle.get("wave.waveInProgress", "Wave in progress")
                : localized("wave.waiting", remaining));
        }else if(state.enemies == 0){
            status.append("\n").append(Core.bundle.get("waiting", "Waiting"));
        }
        markStatusPhase("complete");
        return status.toString();
    }

    private static String fallbackObjectiveText(mindustry.game.MapObjectives.MapObjective objective){
        // Keep the original translated vocabulary while avoiding MessageFormat.
        // These are only used if upstream objective.text() failed at runtime.
        if(objective instanceof mindustry.game.MapObjectives.ResearchObjective o){
            return localized("objective.research", o.content.emoji(), o.content.localizedName);
        }
        if(objective instanceof mindustry.game.MapObjectives.ProduceObjective o){
            return localized("objective.produce", o.content.emoji(), o.content.localizedName);
        }
        if(objective instanceof mindustry.game.MapObjectives.ItemObjective o){
            return localized("objective.item", state.rules.defaultTeam.items().get(o.item),
                o.amount, o.item.emoji(), o.item.localizedName);
        }
        if(objective instanceof mindustry.game.MapObjectives.CoreItemObjective o){
            return localized("objective.coreitem", state.stats.coreItemCount.get(o.item),
                o.amount, o.item.emoji(), o.item.localizedName);
        }
        if(objective instanceof mindustry.game.MapObjectives.BuildCountObjective o){
            return localized("objective.build", o.count - state.stats.placedBlockCount.get(o.block, 0),
                o.block.emoji(), o.block.localizedName);
        }
        if(objective instanceof mindustry.game.MapObjectives.UnitCountObjective o){
            return localized("objective.buildunit", o.count - state.rules.defaultTeam.data().countType(o.unit),
                o.unit.emoji(), o.unit.localizedName);
        }
        if(objective instanceof mindustry.game.MapObjectives.DestroyUnitsObjective o){
            return localized("objective.destroyunits", o.count - state.stats.enemyUnitsDestroyed);
        }
        if(objective instanceof mindustry.game.MapObjectives.DestroyBlockObjective o){
            return localized("objective.destroyblock", o.block.emoji(), o.block.localizedName);
        }
        if(objective instanceof mindustry.game.MapObjectives.DestroyBlocksObjective o){
            return localized("objective.destroyblocks", o.progress(), o.positions.length,
                o.block.emoji(), o.block.localizedName);
        }
        if(objective instanceof mindustry.game.MapObjectives.TimerObjective o && o.text != null){
            String key = o.text.startsWith("@") ? o.text.substring(1) : o.text;
            return o.text.startsWith("@")
                ? (state.mapLocales.containsProperty(key) ? state.mapLocales.getProperty(key) : Core.bundle.get(key, key)) : key;
        }
        return objective.typeName();
    }

    private static String localized(String key, Object first, Object second, Object third){
        return localized(key, first, second).replace("{2}", String.valueOf(third));
    }

    private static String localized(String key, Object first, Object second, Object third, Object fourth){
        return localized(key, first, second, third).replace("{3}", String.valueOf(fourth));
    }

    @JSBody(params = {"name"}, script = "document.documentElement.setAttribute('data-mindustry-hud-objective-fallback',name);")
    private static native void markObjectiveFallback(String name);

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

    @JSBody(script = "const r=document.documentElement;r.setAttribute('data-mindustry-hud-essentials','ready');r.setAttribute('data-mindustry-hud-minimap','stock-mindustry-ui-Minimap');r.setAttribute('data-mindustry-hud-minimap-overlay','stock-MiniMapFragment');r.setAttribute('data-mindustry-hud-coreitems','stock-CoreItemsDisplay');r.setAttribute('data-mindustry-hud-status','game-state');r.setAttribute('data-mindustry-hud-skip-wave','stock-rule-guarded');r.setAttribute('data-mindustry-hud-player-bar','stock-Bar');r.setAttribute('data-mindustry-hud-position','player-tile');r.setAttribute('data-mindustry-hud-guardian','stock-Bar');r.setAttribute('data-mindustry-hud-objectives','all-qualified');")
    private static native void markMounted();

    @JSBody(params = {"textured", "wave", "enemies"}, script = "const r=document.documentElement;r.setAttribute('data-mindustry-hud-minimap-texture',textured?'ready':'pending');r.setAttribute('data-mindustry-hud-live-wave',String(wave));r.setAttribute('data-mindustry-hud-live-enemies',String(enemies));")
    private static native void markLive(boolean textured, int wave, int enemies);
}

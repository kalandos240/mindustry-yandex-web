package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.ctype.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only live BlockConfigFragment acceptance, using original SorterBuild.
 * A disposable sorter is created on a valid visible Ground Zero tile. Chrome
 * physically clicks its world tile, the STOCK copper selection and, after
 * reopening the config, the selected copper button to clear it. This observer
 * only records the resulting original SorterBuild.sortItem; it never calls
 * configure(), showConfig(), or mutates the selection.
 */
public final class BrowserBuildingConfigSmoke{
    private static boolean checked, enabled, finished;
    private static int frames, scan, stage, stageFrames;
    private static Tile fixture;
    private static String familyName = "";
    private static Block familyBlock;

    private BrowserBuildingConfigSmoke(){}

    public static void update(){
        if(!requested() || finished) return;
        if(state == null || !state.isPlaying() || state.gameOver ||
            !Campaign.active() || state.rules == null ||
            state.rules.sector == null || familyName.isEmpty() && state.rules.sector.planet != Planets.serpulo ||
            state.rules.defaultTeam.core() == null ||
            control == null || control.input == null || Core.scene == null) return;

        if(++frames > 1800 && familyName.isEmpty()) throw new IllegalStateException("Sorter native config smoke exceeded 1800 Java frames");
        if(stage == 0){
            Block block = Blocks.sorter;
            if(!familyName.isEmpty()){
                if(familyBlock == null){
                    familyBlock = content.getByName(ContentType.block, familyName);
                    if(familyBlock == null || !familyBlock.configurable) throw new IllegalStateException("Unsupported native config fixture: " + familyName);
                    // Isolated QA profile setup only. No configure() callback is
                    // called here; actual browser input must select each value.
                    familyBlock.quietUnlock();
                    for(var unit : content.units()) unit.quietUnlock();
                    state.rules.infiniteResources = true;
                }
                block = familyBlock;
            }
            Building core = state.rules.defaultTeam.core();
            // Search in small batches instead of freezing the JavaScript main
            // thread; favor tiles close to the live player and visible camera.
            for(int i = 0; i < 64 && scan < 41 * 41; i++, scan++){
                int x = core.tile.x + scan % 41 - 20;
                int y = core.tile.y + scan / 41 - 20;
                Tile t = world.tile(x, y);
                if(t == null) continue;
                Vec2 p = Core.camera.project(new Vec2(t.worldx(), t.worldy()));
                if(p.x < 80f || p.y < 80f || p.x > Core.graphics.getWidth() - 80f ||
                    p.y > Core.graphics.getHeight() - 80f ||
                    Core.scene.hasMouse(p.x, p.y)) continue;
                if(familyName.equals("naval-factory")){
                    // Ground Zero need not have water next to its core. Only
                    // this disposable QA fixture gets an empty water footprint
                    // and output ring; never replace an existing building.
                    boolean empty = true;
                    for(int dx = -2; dx <= 2; dx++) for(int dy = -2; dy <= 2; dy++){
                        Tile part = world.tile(x + dx, y + dy);
                        if(part == null || part.block() != Blocks.air) empty = false;
                    }
                    if(!empty) continue;
                    for(int dx = -2; dx <= 2; dx++) for(int dy = -2; dy <= 2; dy++){
                        world.tile(x + dx, y + dy).setFloor(Blocks.water.asFloor());
                    }
                }
                if(!Build.validPlace(block, state.rules.defaultTeam, x, y, 0, false)) continue;
                t.setBlock(block, state.rules.defaultTeam, 0);
                if(familyName.isEmpty() && !(t.build instanceof Sorter.SorterBuild)){
                    throw new IllegalStateException("Original SorterBuild not constructed");
                }
                fixture = t;
                stage = 1;
                stageFrames = 0;
                if(familyName.isEmpty()) markFixture(p.x, p.y);
                return;
            }
            if(scan == 41 * 41) throw new IllegalStateException("No reachable visible ground for stock Sorter config");
            return;
        }

        if(!familyName.isEmpty()){
            if(fixture == null || fixture.build == null || fixture.build.block != familyBlock) throw new IllegalStateException("Native config fixture disappeared: " + familyName);
            Vec2 point = Core.camera.project(new Vec2(fixture.worldx(), fixture.worldy()));
            // A processor's config() compresses the full program. Its code and
            // link values are read by the test probe instead of recompressing
            // them on every render tick.
            Object value = familyName.equals("micro-processor") ? null : fixture.build.config();
            markFamilyValue(familyName, String.valueOf(value), value == null ? "null" : value.getClass().getName(),
                control.input.config.isShown() && control.input.config.getSelected() == fixture.build, point.x, point.y);
            return;
        }

        if(fixture == null || !(fixture.build instanceof Sorter.SorterBuild sorter)){
            throw new IllegalStateException("Sorter configuration fixture disappeared");
        }
        if(++stageFrames > 350){
            throw new IllegalStateException("Native Sorter config stalled at stage " + stage);
        }
        if(stage == 1 || stage == 3){
            // Camera-follow moves world-screen positions while the original
            // game runs. Refresh the real tile center for trusted CDP clicks.
            if((stageFrames & 7) == 0){
                Vec2 center = Core.camera.project(new Vec2(fixture.worldx(), fixture.worldy()));
                markFixture(center.x, center.y);
            }
            // Waiting for a real pointer world tap via DesktopInput/MobileInput.
            if(control.input.config.isShown() && control.input.config.getSelected() == sorter){
                stage = stage == 1 ? 2 : 4;
                stageFrames = 0;
                markOpened(stage == 2 ? "first" : "reopen");
            }
        }else if(stage == 2 || stage == 4){
            // ItemSelection closes its own popup immediately on a successful
            // click, so first inspect the build value even when hidden.
            if(stage == 2 && sorter.sortItem == Items.copper){
                stage = 3;
                stageFrames = 0;
                markSet();
                return;
            }
            if(stage == 4 && sorter.sortItem == null){
                finished = true;
                markPassed();
                return;
            }
            if(!control.input.config.isShown() ||
                control.input.config.getSelected() != sorter) return;
            // The stock ItemSelection.buildTable creates a native ImageButton
            // for copper. Report only the center that actually hits it in Arc.
            Core.scene.root.forEach(actor -> {
                if(!(actor instanceof ImageButton button) || !button.visible ||
                    button.getWidth() < 15f || button.getHeight() < 15f ||
                    !(button.getStyle().imageUp instanceof TextureRegionDrawable d) ||
                    d.getRegion() != Items.copper.uiIcon) return;
                Vec2 p = button.localToStageCoordinates(new Vec2(
                    button.getWidth() / 2f, button.getHeight() / 2f));
                Element hit = Core.scene.hit(p.x, p.y, true);
                if(hit != button && (hit == null || !hit.isDescendantOf(button))) return;
                Vec2 screen = Core.scene.getViewport().project(p);
                markCopper(screen.x, screen.y, stage == 2 ? "set" : "clear");
            });
        }
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            familyName = queryFamily();
            enabled |= !familyName.isEmpty();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryBuildingConfigSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryConfigFamily') || '';")
    private static native String queryFamily();

    @JSBody(params={"name","value","type","open","x","y"},script="""
        var r=document.documentElement;
        r.setAttribute('data-mindustry-native-config-name',name);
        r.setAttribute('data-mindustry-native-config-value',value);
        r.setAttribute('data-mindustry-native-config-type',type);
        r.setAttribute('data-mindustry-native-config-open',String(open));
        r.setAttribute('data-mindustry-native-config-x',String(x));
        r.setAttribute('data-mindustry-native-config-y',String(y));
        """)
    private static native void markFamilyValue(String name,String value,String type,boolean open,float x,float y);

    @JSBody(params={"x","y"},script="""
        const r = document.documentElement;
        r.setAttribute('data-mindustry-building-config-fixture','sorter');
        r.setAttribute('data-mindustry-building-config-tile-x',String(x));
        r.setAttribute('data-mindustry-building-config-tile-y',String(y));
        """)
    private static native void markFixture(float x,float y);

    @JSBody(params={"part"},script="document.documentElement.setAttribute('data-mindustry-building-config-opened',part);")
    private static native void markOpened(String part);

    @JSBody(params={"x","y","action"},script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-building-config-copper-x',String(x));
        r.setAttribute('data-mindustry-building-config-copper-y',String(y));
        r.setAttribute('data-mindustry-building-config-choice',action);
        """)
    private static native void markCopper(float x,float y,String action);

    @JSBody(script="document.documentElement.setAttribute('data-mindustry-building-config-selected','copper');")
    private static native void markSet();

    @JSBody(script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-building-config-cleared','yes');
        r.setAttribute('data-mindustry-building-config-smoke','passed');
        """)
    private static native void markPassed();
}

package mindustry.web;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.game.EventType.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Lean local build palette for the Yandex/Web client.
 *
 * Full HudFragment/PlacementFragment initialization retains a large desktop UI graph
 * that is unnecessary for local gameplay and does not fit the tight TeaVM budget.
 * This palette deliberately owns only block/category selection. Actual placement,
 * rotation, BuildPlan creation, validation, configuration and builder execution remain
 * in the stock InputHandler/DesktopInput/MobileInput graph already bound to hudGroup.
 */
public final class BrowserBuildPalette{
    private static final int columns = 4;
    private static boolean initialized;
    private static Category current = Category.distribution;
    private static Table root, categories, blocks;
    private static ScrollPane pane;

    private BrowserBuildPalette(){}

    public static void build(Group parent){
        if(initialized) return;
        if(parent == null || control == null || control.input == null || content == null){
            throw new IllegalStateException("Browser build palette requires HUD, Control input and loaded content");
        }

        root = new Table();
        root.setFillParent(true);
        root.bottom().right();
        root.touchable = Touchable.childrenOnly;
        root.visible(() -> state != null && state.isGame() && player != null && player.isBuilder());

        Table panel = new Table(Tex.pane2);
        panel.margin(4f);

        blocks = new Table();
        blocks.top().left();
        pane = new ScrollPane(blocks, Styles.smallPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        panel.add(pane).width(mobile ? 236f : 204f).height(mobile ? 222f : 194f).growY();

        categories = new Table();
        categories.bottom();
        panel.add(categories).width(mobile ? 116f : 104f).bottom();

        root.add(panel).bottom().right().pad(mobile ? 6f : 4f);
        parent.addChild(root);

        rebuildCategories();
        rebuildBlocks();

        Events.on(WorldLoadEvent.class, event -> Core.app.post(() -> {
            control.input.block = null;
            current = Category.distribution;
            rebuildCategories();
            if(!hasBlocks(current)){
                for(Category category : Category.all){
                    if(hasBlocks(category)){
                        current = category;
                        break;
                    }
                }
            }
            rebuildBlocks();
            markCounts(visibleCategoryCount(), visibleBlockCount(current));
        }));

        Events.on(ResetEvent.class, event -> {
            if(control != null && control.input != null) control.input.block = null;
        });

        initialized = true;
        markReady(visibleCategoryCount(), visibleBlockCount(current));
    }

    private static void rebuildCategories(){
        categories.clear();
        int index = 0;
        for(Category category : Category.all){
            if(!hasBlocks(category)) continue;

            ImageButton button = categories.button(ui.getIcon(category.name()), Styles.clearTogglei, () -> {
                current = category;
                if(control.input.block != null && control.input.block.category != current){
                    control.input.block = null;
                }
                rebuildBlocks();
                markSelection(current.name(), control.input.block == null ? "none" : control.input.block.name);
            }).size(mobile ? 54f : 48f).name("web-category-" + category.name()).get();
            button.update(() -> button.setChecked(current == category));
            if(++index % 2 == 0) categories.row();
        }
    }

    private static void rebuildBlocks(){
        blocks.clear();
        int index = 0;
        for(Block block : content.blocks()){
            if(block.category != current || !available(block)) continue;

            ImageButton button = blocks.button(new TextureRegionDrawable(block.uiIcon), Styles.selecti, () -> {
                control.input.block = control.input.block == block ? null : block;
                markSelection(current.name(), control.input.block == null ? "none" : control.input.block.name);
            }).size(mobile ? 54f : 46f).name("web-block-" + block.name).get();
            button.resizeImage(mobile ? 38f : 32f);
            button.update(() -> button.setChecked(control.input.block == block));
            if(++index % columns == 0) blocks.row();
        }
        blocks.invalidateHierarchy();
        pane.setScrollYForce(0f);
        markCounts(visibleCategoryCount(), index);
    }

    private static boolean available(Block block){
        return block != null && block.isVisible() && block.unlockedNowHost() && block.placeablePlayer
            && block.environmentBuildable() && state != null && block.supportsEnv(state.rules.env);
    }

    private static boolean hasBlocks(Category category){
        for(Block block : content.blocks()){
            if(block.category == category && available(block)) return true;
        }
        return false;
    }

    private static int visibleCategoryCount(){
        int count = 0;
        for(Category category : Category.all){
            if(hasBlocks(category)) count++;
        }
        return count;
    }

    private static int visibleBlockCount(Category category){
        int count = 0;
        for(Block block : content.blocks()){
            if(block.category == category && available(block)) count++;
        }
        return count;
    }

    public static boolean initialized(){
        return initialized;
    }

    @JSBody(params = {"categories", "blocks"}, script = "document.documentElement.setAttribute('data-mindustry-build-palette', 'ready'); document.documentElement.setAttribute('data-mindustry-build-categories', String(categories)); document.documentElement.setAttribute('data-mindustry-build-blocks', String(blocks));")
    private static native void markReady(int categories, int blocks);

    @JSBody(params = {"categories", "blocks"}, script = "document.documentElement.setAttribute('data-mindustry-build-categories', String(categories)); document.documentElement.setAttribute('data-mindustry-build-blocks', String(blocks));")
    private static native void markCounts(int categories, int blocks);

    @JSBody(params = {"category", "block"}, script = "document.documentElement.setAttribute('data-mindustry-build-category', category); document.documentElement.setAttribute('data-mindustry-build-selected', block);")
    private static native void markSelection(String category, String block);
}

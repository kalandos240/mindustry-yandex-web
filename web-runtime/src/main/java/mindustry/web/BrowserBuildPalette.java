package mindustry.web;

import arc.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
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
    // Allow room for readable labels on desktop and touch screens.
    private static final int columns = 2;
    private static boolean initialized;
    private static Category current = Category.distribution;
    private static Table root, categories, blocks;
    private static ScrollPane pane, categoryPane;
    private static int visibilityFrames;

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
        // A missing or non-builder unit must not make the entire construction HUD disappear.
        root.visible(() -> state != null && state.isGame() && !state.gameOver);

        Table panel = new Table(Tex.pane2);
        panel.margin(4f);
        panel.add(Core.bundle.get("category.blocks.name", "Blocks")).colspan(2).left().pad(3f);
        panel.row();

        blocks = new Table();
        blocks.top().left();
        pane = new ScrollPane(blocks, Styles.smallPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        panel.add(pane).width(mobile ? 236f : 204f).height(mobile ? 222f : 194f).growY();

        categories = new Table();
        categories.top().left();
        categoryPane = new ScrollPane(categories, Styles.smallPane);
        categoryPane.setFadeScrollBars(false);
        categoryPane.setScrollingDisabled(true, false);
        panel.add(categoryPane).width(mobile ? 116f : 104f).height(mobile ? 222f : 194f).top();

        root.add(panel).bottom().right().pad(mobile ? 6f : 4f);
        parent.addChild(root);
        root.update(() -> {
            // Read real in-game palette visibility, not just startup construction.
            if(state != null && state.isGame() && (++visibilityFrames & 31) == 0){
                markDisplay(root.isVisible(), categories.getChildren().size > 0,
                    blocks.getChildren().size > 0);
            }
        });

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
        for(Category category : Category.all){
            if(!hasBlocks(category)) continue;

            // Avoid icon-only navigation: on some Web clients the icons have
            // no visible pixels even though the controls are clickable.
            TextButton button = categories.button(
                Core.bundle.get("category." + category.name() + ".name", category.name()), () -> {
                    current = category;
                    if(control.input.block != null && control.input.block.category != current){
                        control.input.block = null;
                    }
                    rebuildBlocks();
                    markSelection(current.name(), control.input.block == null ? "none" : control.input.block.name);
                }).size(mobile ? 112f : 100f, mobile ? 36f : 32f)
                .name("web-category-" + category.name()).get();
            button.getLabel().setFontScale(0.72f);
            button.getLabel().setEllipsis(true);
            button.update(() -> button.setChecked(current == category));
            categories.row();
        }
        categories.invalidateHierarchy();
    }

    private static void rebuildBlocks(){
        blocks.clear();
        int index = 0;
        for(Block block : content.blocks()){
            if(block.category != current || !available(block)) continue;

            // Render a real localized label even if block.uiIcon is blank.
            // Keep the original actor names and stock InputHandler selection.
            TextButton button = blocks.button(block.localizedName, () -> {
                control.input.block = control.input.block == block ? null : block;
                markSelection(current.name(), control.input.block == null ? "none" : control.input.block.name);
            }).size(mobile ? 112f : 98f, mobile ? 46f : 42f)
                .name("web-block-" + block.name).get();
            button.getLabel().setFontScale(0.72f);
            button.getLabel().setEllipsis(true);
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

    @JSBody(params = {"visible", "categories", "blocks"}, script = "document.documentElement.setAttribute('data-mindustry-build-palette-visible', visible ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-build-palette-actions', categories && blocks ? 'present' : 'missing');")
    private static native void markDisplay(boolean visible, boolean categories, boolean blocks);

    @JSBody(params = {"categories", "blocks"}, script = "document.documentElement.setAttribute('data-mindustry-build-palette', 'ready'); document.documentElement.setAttribute('data-mindustry-build-categories', String(categories)); document.documentElement.setAttribute('data-mindustry-build-blocks', String(blocks));")
    private static native void markReady(int categories, int blocks);

    @JSBody(params = {"categories", "blocks"}, script = "document.documentElement.setAttribute('data-mindustry-build-categories', String(categories)); document.documentElement.setAttribute('data-mindustry-build-blocks', String(blocks));")
    private static native void markCounts(int categories, int blocks);

    @JSBody(params = {"category", "block"}, script = "document.documentElement.setAttribute('data-mindustry-build-category', category); document.documentElement.setAttribute('data-mindustry-build-selected', block);")
    private static native void markSelection(String category, String block);
}

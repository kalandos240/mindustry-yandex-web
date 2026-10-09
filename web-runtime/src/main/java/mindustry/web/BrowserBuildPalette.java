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
        // Keep the compact palette only as a fallback. The original PlacementFragment
        // becomes the visible build HUD once its actor graph is successfully mounted.
        root.visible(() -> state != null && state.isGame() && !state.gameOver
            && !BrowserStockPlacement.active());

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
        // A hidden fallback actor does not receive Scene.act() updates. Report the
        // visible native PlacementFragment through updateVisibility() instead.

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

    /**
     * BrowserApplication calls this from its regular gameplay frame even when the
     * compact fallback palette is hidden. A hidden Scene actor never receives
     * Element.update(), so tying the portal visibility status to root.update()
     * silently stopped reporting after the native PlacementFragment was mounted.
     */
    public static void updateVisibility(){
        if(!initialized || state == null || !state.isGame()) return;
        if((++visibilityFrames & 31) != 0) return;

        boolean nativeVisible = BrowserStockPlacement.active() && ui != null
            && ui.hudfrag != null && ui.hudfrag.shown;
        boolean fallbackVisible = root != null && root.visible;
        markDisplay(!state.gameOver && (nativeVisible || fallbackVisible),
            visibleCategoryCount() > 0, visibleBlockCount(current) > 0,
            waitingForFirstResearch());
    }

    /** Refresh available actions after research unlocks a new construction block. */
    public static void refresh(){
        if(!initialized) return;
        if(!hasBlocks(current)){
            for(Category category : Category.all){
                if(hasBlocks(category)){
                    current = category;
                    break;
                }
            }
        }
        rebuildCategories();
        rebuildBlocks();
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
        if(index == 0){
            // Ground Zero starts before any buildable technology is unlocked.
            // Never present an unexplained black/empty pane: guide the player
            // to the actual research action exposed by BrowserUiRuntime.
            String label = waitingForFirstResearch()
                ? Core.bundle.get("research", "Research") + ": " + mindustry.content.Blocks.mechanicalDrill.localizedName
                : Core.bundle.get("none", "No available blocks");
            Label hint = new Label(label);
            hint.setWrap(true);
            blocks.add(hint).width(mobile ? 208f : 188f).pad(6f).left();
        }
        blocks.invalidateHierarchy();
        pane.setScrollYForce(0f);
        markCounts(visibleCategoryCount(), index);
    }

    private static boolean waitingForFirstResearch(){
        return state != null && state.isCampaign() && state.rules != null
            && state.rules.sector == mindustry.content.SectorPresets.groundZero.sector
            && !mindustry.content.Blocks.mechanicalDrill.unlocked();
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

    @JSBody(params = {"visible", "categories", "blocks", "research"}, script = "const r=document.documentElement; r.setAttribute('data-mindustry-build-palette-visible', visible ? 'yes' : 'no'); r.setAttribute('data-mindustry-build-palette-actions', categories && blocks ? 'present' : research ? 'research-needed' : 'none');")
    private static native void markDisplay(boolean visible, boolean categories, boolean blocks, boolean research);

    @JSBody(params = {"categories", "blocks"}, script = "document.documentElement.setAttribute('data-mindustry-build-palette', 'ready'); document.documentElement.setAttribute('data-mindustry-build-categories', String(categories)); document.documentElement.setAttribute('data-mindustry-build-blocks', String(blocks));")
    private static native void markReady(int categories, int blocks);

    @JSBody(params = {"categories", "blocks"}, script = "document.documentElement.setAttribute('data-mindustry-build-categories', String(categories)); document.documentElement.setAttribute('data-mindustry-build-blocks', String(blocks));")
    private static native void markCounts(int categories, int blocks);

    @JSBody(params = {"category", "block"}, script = "document.documentElement.setAttribute('data-mindustry-build-category', category); document.documentElement.setAttribute('data-mindustry-build-selected', block);")
    private static native void markSelection(String category, String block);
}

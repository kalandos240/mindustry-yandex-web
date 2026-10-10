package mindustry.web;

import arc.*;
import arc.graphics.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.game.Objectives.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * A compact, interactive view of the actual pinned vanilla TechTree.
 *
 * The former 32-items-per-page catalog flattened unrelated branches and mixed
 * Serpulo/Erekir technologies. This keeps native parent/child relationships,
 * root selection, tech icons, sector objectives and partial item progress while
 * using lightweight Arc Scene widgets instead of the large desktop ResearchDialog.
 * Unlocks still go exclusively through BrowserCampaignResearch.spend().
 */
public final class BrowserResearchUi{
    private static boolean initialized, open;
    private static TechNode activeRoot, selected;
    private static final ObjectSet<TechNode> expanded = new ObjectSet<>();
    private static BrowserTechTreeGraph menuTree, hudTree;
    private static ScrollPane menuTreePane, hudTreePane;
    private static Table menuDetails, hudDetails;
    private static float graphZoom = 1f;
    private static float menuTreeWidth, hudTreeWidth, menuDetailsWidth, hudDetailsWidth;
    private static int visibleCount;

    private BrowserResearchUi(){}

    public static void init(){
        if(initialized) return;
        if(ui == null || ui.menuGroup == null || ui.hudGroup == null){
            throw new IllegalStateException("Research tree requires initialized menu and HUD groups");
        }
        if(TechTree.all.size < 30 || TechTree.roots.size < 2 ||
            Planets.serpulo.techTree == null || Planets.erekir.techTree == null ||
            Blocks.conveyor.techNode == null || Blocks.junction.techNode == null ||
            Blocks.router.techNode == null || Blocks.mechanicalDrill.techNode == null){
            throw new IllegalStateException("Browser research tree lost vanilla technology roots");
        }

        activeRoot = Planets.serpulo.techTree;
        selected = activeRoot;
        expanded.add(activeRoot);
        buildPanel(ui.menuGroup, true);
        buildPanel(ui.hudGroup, false);
        initialized = true;
        markResearchReady();
        markResearchCatalog(totalNodes());
        refresh();
    }

    private static void buildPanel(Group parent, boolean menu){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> open && (menu ? state.isMenu() :
            state.isCampaign() && !state.gameOver));
        overlay.setBackground(Tex.pane2);

        float totalWidth = Math.max(270f, Math.min(mobile ? 370f : 1000f, Core.graphics.getWidth() - 24f));
        float treeWidth = mobile ? totalWidth - 16f : totalWidth * 0.58f;
        float detailWidth = mobile ? totalWidth - 16f : totalWidth - treeWidth - 16f;
        float treeHeight = mobile ?
            Math.max(185f, Math.min(340f, Core.graphics.getHeight() - 405f)) :
            Math.max(250f, Math.min(590f, Core.graphics.getHeight() - 165f));
        float detailHeight = mobile ?
            Math.max(145f, Math.min(235f, Core.graphics.getHeight() - treeHeight - 165f)) :
            treeHeight;

        Table panel = new Table(Tex.pane2);
        panel.defaults().pad(3f);

        Table heading = new Table();
        heading.add(Core.bundle.get("research", "Research")).left().growX();
        heading.button(Core.bundle.get("back", "Back"), BrowserResearchUi::close)
            .size(mobile ? 100f : 105f, mobile ? 49f : 43f);
        panel.add(heading).colspan(mobile ? 1 : 2).growX().row();

        Table roots = new Table();
        roots.left();
        for(TechNode root : TechTree.roots){
            roots.button(root.localizedName(), () -> switchRoot(root))
                .checked(button -> activeRoot == root)
                .height(mobile ? 44f : 40f).minWidth(mobile ? 124f : 145f).padRight(5f)
                .name("web-research-root-" + root.content.name);
        }
        panel.add(roots).colspan(mobile ? 1 : 2).left().row();

        BrowserTechTreeGraph tree = new BrowserTechTreeGraph();
        ScrollPane treePane = new ScrollPane(tree, Styles.smallPane);
        treePane.setFadeScrollBars(false);
        treePane.setScrollingDisabled(false, false);
        treePane.setOverscroll(false, false);
        // Vanilla ResearchDialog zooms with the wheel rather than moving the
        // viewport. Restrict wheel capture to this research pane so normal
        // menu/gameplay scrolling and nearby item details are unchanged.
        treePane.addCaptureListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                if(!open || amountY == 0f) return false;
                changeZoom(-0.1f * Math.signum(amountY));
                event.stop();
                return true;
            }
        });
        // Two-finger pinch zoom. A quantized zoom step prevents rebuilding
        // hundreds of buttons on every high-frequency browser touch event.
        treePane.addCaptureListener(new ElementGestureListener(){
            private float pinchInitialZoom = -1f;

            @Override
            public void zoom(InputEvent event, float initialDistance, float distance){
                if(!open || initialDistance <= 0.1f) return;
                if(pinchInitialZoom < 0f) pinchInitialZoom = graphZoom;
                setZoom(pinchInitialZoom * distance / initialDistance);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button){
                pinchInitialZoom = -1f;
            }
        });
        // Arc sends wheel events to the current scroll-focus element, not
        // necessarily the actor under the cursor. Match stock ResearchDialog:
        // moving over the graph transfers scroll focus to this pane.
        treePane.addListener(new InputListener(){
            @Override
            public boolean mouseMoved(InputEvent event, float x, float y){
                treePane.requestScroll();
                return false;
            }
        });

        Table details = new Table();
        details.top().left();
        ScrollPane infoPane = new ScrollPane(details, Styles.smallPane);
        infoPane.setFadeScrollBars(false);
        infoPane.setScrollingDisabled(true, false);

        if(mobile){
            panel.add(treePane).width(treeWidth).height(treeHeight).left().row();
            panel.add(infoPane).width(detailWidth).height(detailHeight).left().row();
        }else{
            panel.add(treePane).width(treeWidth).height(treeHeight);
            panel.add(infoPane).width(detailWidth).height(detailHeight);
            panel.row();
        }

        Table actions = new Table();
        actions.button("-", () -> changeZoom(-0.2f))
            .size(mobile ? 52f : 60f, mobile ? 44f : 42f);
        actions.button("+", () -> changeZoom(0.2f))
            .size(mobile ? 52f : 60f, mobile ? 44f : 42f);
        actions.button(Core.bundle.get("refresh", "Refresh"), BrowserResearchUi::refresh)
            .size(mobile ? 105f : 120f, mobile ? 44f : 42f);
        panel.add(actions).colspan(mobile ? 1 : 2).left().row();

        overlay.add(panel).center();
        parent.addChild(overlay);

        if(menu){
            menuTree = tree;
            menuTreePane = treePane;
            menuDetails = details;
            menuTreeWidth = treeWidth;
            menuDetailsWidth = detailWidth;
        }else{
            hudTree = tree;
            hudTreePane = treePane;
            hudDetails = details;
            hudTreeWidth = treeWidth;
            hudDetailsWidth = detailWidth;
        }
    }

    public static void show(){
        if(!initialized || state == null || !(state.isMenu() || state.isCampaign())) return;
        // Opening research during Erekir gameplay selects its real technology root.
        if(state.isCampaign() && state.rules != null && state.rules.sector != null &&
            state.rules.sector.planet.techTree != null){
            TechNode campaignRoot = state.rules.sector.planet.techTree;
            if(activeRoot != campaignRoot) switchRoot(campaignRoot);
        }
        open = true;
        refresh();
        markResearchOpen(true);
        focusSelected();
    }

    public static void close(){
        open = false;
        markResearchOpen(false);
    }

    // Read-only state for the opt-in real-pointer Chrome navigation gate.
    public static boolean isOpen(){ return open; }
    public static String activeRootContent(){ return activeRoot == null ? "" : activeRoot.content.name; }
    public static String selectedContent(){ return selected == null ? "" : selected.content.name; }

    private static void switchRoot(TechNode root){
        if(root == null) return;
        activeRoot = root;
        selected = root;
        expanded.clear();
        expanded.add(root);
        refresh();
        focusSelected();
    }

    private static int totalNodes(){
        int result = 0;
        for(TechNode node : TechTree.all){
            if(node.content != null && !(node.content instanceof SectorPreset)) result++;
        }
        return result;
    }

    public static void refresh(){
        if(!initialized || activeRoot == null) return;
        visibleCount = 0;
        rebuildTree(menuTree, menuTreeWidth);
        int count = visibleCount;
        rebuildTree(hudTree, hudTreeWidth);
        rebuildDetails(menuDetails, menuDetailsWidth);
        rebuildDetails(hudDetails, hudDetailsWidth);
        markResearchCatalog(totalNodes());
        markTree(activeRoot.name == null ? activeRoot.content.name : activeRoot.name,
            selected == null ? "" : selected.content.name, count, TechTree.roots.size);
    }

    /**
     * In a spatial tech graph the top-left initial scroll position is often
     * far from the root (its Y is centered among many child branches).
     * Reveal selected nodes after every root switch or branch expansion so
     * desktop and portrait users never open an apparently empty graph.
     * The native ScrollPane still owns subsequent touch drag / wheel panning.
     */
    private static void focusSelected(){
        Core.app.post(() -> {
            if(!open || selected == null) return;
            focusNode(menuTreePane, menuTree, selected);
            focusNode(hudTreePane, hudTree, selected);
        });
    }

    private static void focusNode(ScrollPane pane, BrowserTechTreeGraph graph, TechNode node){
        if(pane == null || graph == null || node == null) return;
        pane.validate();
        graph.validate();
        Element element = graph.find("web-research-node-" + node.content.name);
        if(element == null) return;
        pane.scrollTo(element.x, element.y, element.getWidth(), element.getHeight(), true, true);
        pane.updateVisualScroll();
    }

    private static void changeZoom(float delta){
        setZoom(graphZoom + delta);
    }

    private static void setZoom(float requested){
        // Quantize to 0.1x steps: legible text and bounded JS/GPU allocations
        // even during rapid trackpad scrolling or two-finger pinch.
        float next = Math.round(Math.max(0.6f, Math.min(1.6f, requested)) * 10f) / 10f;
        if(Math.abs(graphZoom - next) < 0.049f) return;
        graphZoom = next;
        refresh();
        focusSelected();
    }

    private static void selectGraphNode(TechNode node){
        selected = node;
        if(node.children.size > 0){
            // Root always stays open. Other branches toggle between expanded
            // and collapsed; descendants keep their expansion state for later.
            if(node != activeRoot && expanded.contains(node)){
                expanded.remove(node);
            }else{
                expanded.add(node);
            }
        }
        refresh();
        focusSelected();
    }

    private static void rebuildTree(BrowserTechTreeGraph graph, float width){
        if(graph == null) return;
        graph.rebuild(activeRoot, expanded, graphZoom, BrowserResearchUi::selectGraphNode);
        visibleCount = graph.nodeCount();
    }

    private static void rebuildDetails(Table details, float width){
        if(details == null || selected == null) return;
        details.clear();
        details.top().left();
        float textWidth = Math.max(170f, width - 24f);
        TechNode node = selected;
        UnlockableContent content = node.content;

        Table title = new Table();
        if(content.uiIcon != null) title.add(new Image(content.uiIcon)).size(38f).padRight(8f);
        Label name = new Label(content.localizedName);
        name.setFontScale(1f);
        title.add(name).left().growX();
        details.add(title).width(textWidth).left().row();

        details.add(content.unlocked() ?
                Core.bundle.get("unlocked", "Unlocked") :
                Core.bundle.get("locked", "Locked"))
            .left().padTop(6f).row();

        if(content.description != null && !content.description.isEmpty()){
            Label description = new Label(content.description);
            description.setFontScale(0.83f);
            description.setWrap(true);
            details.add(description).width(textWidth).left().padTop(7f).row();
        }

        if(node.parent != null){
            details.add(Core.bundle.get("requirement.research", "Research") +
                ": " + node.parent.content.localizedName).left().padTop(6f).row();
        }

        for(Objective objective : node.objectives){
            if(objective == null) continue;
            boolean complete = objectiveComplete(objective);
            Label label = new Label((complete ? "+ " : "- ") + objective.display());
            label.setWrap(true);
            label.setFontScale(0.8f);
            details.add(label).width(textWidth).left().padTop(3f).row();
        }

        if(!(content instanceof SectorPreset)){
            for(int i = 0; i < node.requirements.length; i++){
                ItemStack req = node.requirements[i];
                int completed = node.finishedRequirements[i].amount;
                details.add(req.item.localizedName + ": " +
                    Math.min(completed, req.amount) + " / " + req.amount)
                    .left().padTop(4f).row();
            }
            if(!content.unlocked()){
                TextButton invest = details.button(Core.bundle.get("research", "Research"), () -> {
                    if(BrowserCampaignResearch.canSpend(content)){
                        BrowserCampaignResearch.spend(content);
                        BrowserBuildPalette.refresh();
                        Core.app.post(BrowserResearchUi::refresh);
                    }
                }).width(mobile ? Math.min(textWidth, 200f) : Math.min(textWidth, 240f))
                    .height(mobile ? 50f : 46f).padTop(9f).left().get();
                // Resource counts can change while this window is open. Check
                // spendability in the callback; disable only immutable gates.
                boolean parentLocked = node.parent != null && !node.parent.content.unlocked();
                boolean objectivesLocked = false;
                for(Objective objective : node.objectives){
                    if(!objectiveComplete(objective)) objectivesLocked = true;
                }
                invest.setDisabled(parentLocked || objectivesLocked);
            }
        }else{
            details.add(Core.bundle.get("sectors", "Sectors")).left().padTop(7f).row();
        }
        details.invalidateHierarchy();
    }

    private static boolean objectiveComplete(Objective objective){
        // The Web port uses local unlock flags, not the multiplayer-host path
        // exposed by Objective.complete() for research and produced resources.
        if(objective instanceof mindustry.game.Objectives.Research research){
            return research.content != null && research.content.unlocked();
        }
        if(objective instanceof mindustry.game.Objectives.Produce produce){
            return produce.content != null && produce.content.unlocked();
        }
        return objective.complete();
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-research-catalog', 'techtree-all'); document.documentElement.setAttribute('data-mindustry-research-ui', 'ready');")
    private static native void markResearchReady();

    @JSBody(params = {"count"}, script = "document.documentElement.setAttribute('data-mindustry-research-tech-nodes', String(count));")
    private static native void markResearchCatalog(int count);

    @JSBody(params = {"shown"}, script = "document.documentElement.setAttribute('data-mindustry-research-open', shown ? 'yes' : 'no');")
    private static native void markResearchOpen(boolean shown);

    @JSBody(params = {"root","node","count","roots"}, script = """
        const r=document.documentElement;
        r.setAttribute('data-mindustry-research-layout','hierarchical-techtree');
        r.setAttribute('data-mindustry-research-tree-root',root);
        r.setAttribute('data-mindustry-research-tree-selected',node);
        r.setAttribute('data-mindustry-research-tree-visible-nodes',String(count));
        r.setAttribute('data-mindustry-research-tree-roots',String(roots));
        """)
    private static native void markTree(String root, String node, int count, int roots);
}

package mindustry.web;

import arc.*;
import arc.graphics.*;
import arc.math.geom.*;
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
import mindustry.graphics.*;
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
    private static boolean initialized, open, rootChooserOpen;
    private static TechNode activeRoot, selected;
    private static final ObjectSet<TechNode> expanded = new ObjectSet<>();
    private static BrowserTechTreeGraph menuTree, hudTree;
    private static ScrollPane menuTreePane, hudTreePane;
    private static Table menuDetails, hudDetails;
    private static Table menuResources, hudResources;
    private static ItemSeq storedResources;
    private static Planet storedPlanet;
    private static boolean resourceCacheDirty = true;
    private static float graphZoom = 0.9f;
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
        expandUnlocked(activeRoot);
        buildPanel(ui.menuGroup, true);
        buildPanel(ui.hudGroup, false);
        initialized = true;
        markResearchReady();
        markResearchCatalog(totalNodes());
        refresh();
    }

    // Fullscreen ResearchDialog-style canvas. The original desktop dialog
    // eagerly pulls in online/editor UI; preserve its pinned visuals with
    // Arc Scene and the actual TechTree without instantiating that graph.
    private static void buildPanel(Group parent, boolean menu){
        Table overlay = new Table();
        overlay.name = menu ? "web-stock-research-menu" : "web-stock-research-hud";
        overlay.setFillParent(true);
        overlay.setBackground(Styles.black6);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> open && (menu ? state.isMenu() :
            state.isCampaign() && !state.gameOver));

        Table shell = new Table();
        shell.setFillParent(true);
        shell.top();
        // Pinned ResearchDialog shows one current-tree title; tapping it
        // opens a separate TechTree selector. Persistent side-by-side planet
        // tabs were a Web-specific deviation from the original interface.
        Table titleBar = new Table();
        TextButton currentTree = titleBar.button(activeRoot.localizedName(), Icon.tree, () -> {
            rootChooserOpen = true;
        }).height(mobile ? 50f : 44f).minWidth(mobile ? 205f : 275f)
            .name("web-research-choose-tree").get();
        currentTree.update(() -> {
            if(activeRoot != null) currentTree.setText(activeRoot.localizedName());
        });
        shell.add(titleBar).height(mobile ? 54f : 48f).padTop(4f).row();

        // Lightweight Scene selector: keep the real pinned roots and icons,
        // but avoid eagerly constructing desktop BaseDialog/online UI.
        Table chooser = new Table();
        chooser.name = "web-stock-research-planet";
        chooser.setFillParent(true);
        chooser.setBackground(Styles.black6);
        chooser.touchable = Touchable.enabled;
        chooser.visible(() -> open && rootChooserOpen && (menu ? state.isMenu() :
            state.isCampaign() && !state.gameOver));
        Table options = new Table(Tex.pane2);
        options.defaults().pad(5f);
        options.add(Core.bundle.get("techtree.select", "Select Tech Tree"))
            .color(Pal.accent).pad(9f).row();
        for(TechNode root : TechTree.roots){
            options.button(root.localizedName(), root.icon(), Styles.flatTogglet,
                mobile ? 30f : 32f, () -> {
                    switchRoot(root);
                    rootChooserOpen = false;
                }).checked(button -> activeRoot == root)
                .height(mobile ? 52f : 56f).width(mobile ? 240f : 310f)
                .name("web-research-root-" + root.content.name).row();
        }
        options.button(Core.bundle.get("back", "Back"), Icon.left, () -> {
            rootChooserOpen = false;
        }).height(mobile ? 48f : 50f).width(mobile ? 180f : 240f)
            .name("web-research-root-back").padTop(8f).row();
        chooser.add(options).center();

        BrowserTechTreeGraph tree = new BrowserTechTreeGraph();
        ScrollPane treePane = new ScrollPane(tree, Styles.smallPane);
        treePane.name = "web-stock-research-map";
        treePane.setFadeScrollBars(false);
        treePane.setScrollingDisabled(false, false);
        treePane.setOverscroll(false, false);
        treePane.addCaptureListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                if(!open || amountY == 0f) return false;
                changeZoom(-0.1f * Math.signum(amountY));
                event.stop();
                return true;
            }
        });
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
        treePane.addListener(new InputListener(){
            @Override
            public boolean mouseMoved(InputEvent event, float x, float y){
                treePane.requestScroll();
                return false;
            }
        });
        shell.add(treePane).grow().pad(3f).row();
        Table footer = new Table();
        footer.name = "web-stock-research-footer";
        footer.button(Core.bundle.get("back", "Back"), Icon.left,
            BrowserResearchUi::close).size(mobile ? 128f : 155f, mobile ? 54f : 51f)
            .name("web-research-back").padRight(5f);
        footer.button(Core.bundle.get("database.button", "Core Database"), Icon.book, () -> {
            close();
            BrowserStockDatabaseUi.show();
        }).size(mobile ? 157f : 205f, mobile ? 54f : 51f)
            .name("web-research-database")
            .visible(() -> menu && state.isMenu());
        shell.add(footer).height(mobile ? 62f : 58f).padBottom(3f);
        overlay.addChild(shell);

        Table resources = new Table(Tex.pane2);
        resources.name = "web-stock-research-items";
        resources.top().left();
        Table resourceHost = new Table();
        resourceHost.setFillParent(true);
        resourceHost.top().left();
        resourceHost.touchable = Touchable.childrenOnly;
        resourceHost.add(resources).left().top().pad(6f)
            .maxWidth(mobile ? 155f : 190f);
        overlay.addChild(resourceHost);

        Table details = new Table();
        details.top().left();
        ScrollPane infoPane = new ScrollPane(details, Styles.smallPane);
        infoPane.setFadeScrollBars(false);
        infoPane.setScrollingDisabled(true, false);
        // The original ResearchDialog opens the information card beside
        // the currently highlighted technology, not in a permanent sidebar.
        // Track its actual Scene position through ScrollPane pan/zoom.
        Table popover = new Table(Tex.pane2);
        popover.name = "web-stock-research-node-info";
        popover.touchable = Touchable.childrenOnly;
        popover.add(infoPane).grow().pad(6f);
        final float infoWidth = mobile ? 217f : 292f;
        final float infoHeight = mobile ? 155f : 220f;
        popover.setSize(infoWidth, infoHeight);
        popover.visible(() -> selected != null && selected != activeRoot);
        overlay.addChild(popover);
        overlay.update(() -> {
            if(!open || selected == null || selected == activeRoot) return;
            Element node = tree.buttonFor(selected);
            if(node == null) return;
            Vec2 topRight = node.localToStageCoordinates(
                new Vec2(node.getWidth(), node.getHeight()));
            Vec2 anchor = overlay.stageToLocalCoordinates(topRight);
            float x = anchor.x + 8f;
            if(x + infoWidth > overlay.getWidth() - 8f){
                x = anchor.x - node.getWidth() - infoWidth - 8f;
            }
            x = Math.max(6f, Math.min(x, overlay.getWidth() - infoWidth - 6f));
            float y = Math.max(70f, Math.min(anchor.y - infoHeight * 0.5f,
                overlay.getHeight() - infoHeight - 65f));
            popover.setPosition(x, y);
        });
        // Draw selector last, above research details and resource panels.
        // Hidden chooser does not capture any canvas touch or scroll events.
        overlay.addChild(chooser);
        parent.addChild(overlay);

        if(menu){
            menuTree = tree;
            menuTreePane = treePane;
            menuDetails = details;
            menuResources = resources;
            menuTreeWidth = Core.graphics.getWidth() - 12f;
            menuDetailsWidth = mobile ? 217f : 292f;
        }else{
            hudTree = tree;
            hudTreePane = treePane;
            hudDetails = details;
            hudResources = resources;
            hudTreeWidth = Core.graphics.getWidth() - 12f;
            hudDetailsWidth = mobile ? 217f : 292f;
        }
    }

    // As in stock ResearchDialog, previously unlocked branches are revealed
    // when a planet tree is first opened. Other branches still require a tap.
    private static void expandUnlocked(TechNode node){
        if(node == null || node.children.size == 0 || !node.content.unlocked()) return;
        expanded.add(node);
        for(TechNode child : node.children){
            if(child.content.unlocked()) expandUnlocked(child);
        }
    }

    // Stock ResearchDialog aggregates sector item stocks only when the
    // inventory changes, NOT on every graph zoom or click. Reading
    // sector.items() separately for each Item caused O(items * sectors)
    // save-metadata loads and visible stalls on mobile Chrome.
    private static void rebuildStoredResources(){
        if(!open || activeRoot == null) return;
        Planet current = activeRoot == Planets.erekir.techTree ?
            Planets.erekir : Planets.serpulo;
        if(!resourceCacheDirty && storedResources != null && storedPlanet == current) return;
        if(storedResources == null) storedResources = new ItemSeq();
        storedResources.clear();
        for(Sector sector : current.sectors){
            if(!sector.hasBase() || sector.isFrozen()) continue;
            // Cache this read once per sector, like vanilla ItemSeq cache.
            ItemSeq sectorStock = sector.items();
            sectorStock.each((item, amount) -> {
                if(amount > 0) storedResources.add(item, amount);
            });
        }
        storedPlanet = current;
        resourceCacheDirty = false;
    }

    private static void refreshResources(Table target){
        if(target == null || activeRoot == null) return;
        target.clear();
        target.top().left();
        target.add(Core.bundle.get("items", "Items")).color(Pal.accent).left().row();
        if(!open) return;
        rebuildStoredResources();
        int shown = 0;
        for(mindustry.type.Item item : content.items()){
            int total = storedResources.get(item);
            if(total <= 0) continue;
            Table line = new Table();
            line.add(String.valueOf(total)).width(40f).right();
            if(item.uiIcon != null) line.add(new Image(item.uiIcon)).size(15f).padLeft(3f);
            Label name = new Label(item.localizedName);
            name.setFontScale(0.7f);
            line.add(name).left().padLeft(3f);
            target.add(line).left().row();
            if(++shown >= (mobile ? 8 : 14)) break;
        }
    }

    public static void show(){
        rootChooserOpen = false;
        if(!initialized || state == null || !(state.isMenu() || state.isCampaign())) return;
        // Opening research during Erekir gameplay selects its real technology root.
        if(state.isCampaign() && state.rules != null && state.rules.sector != null &&
            state.rules.sector.planet.techTree != null){
            TechNode campaignRoot = state.rules.sector.planet.techTree;
            if(activeRoot != campaignRoot) switchRoot(campaignRoot);
        }
        // Ground Zero's original tutorial asks for Mechanical Drill. Do not
        // create a second one-click research purchase in the game HUD: guide
        // the player to the real TechTree node when the existing Research
        // button opens. Purchases still use the standard research dialog,
        // with its true requirements and partial research progress.
        if(state.isCampaign() && state.rules != null
            && state.rules.sector == SectorPresets.groundZero.sector
            && !Blocks.mechanicalDrill.unlocked() && Blocks.mechanicalDrill.techNode != null){
            TechNode drill = Blocks.mechanicalDrill.techNode;
            for(TechNode ancestor = drill.parent; ancestor != null; ancestor = ancestor.parent){
                expanded.add(ancestor);
            }
            selected = drill;
        }
        if(!open) BrowserPlaygamaAds.placement("research");
        open = true;
        resourceCacheDirty = true;
        refresh();
        markResearchOpen(true);
        focusSelected();
    }

    // Match the original stacked BaseDialogs: Escape first dismisses the
    // planet-selection popup, and only a second Escape closes Research itself.
    // The footer's Back action still closes the research view directly.
    public static void escape(){
        if(rootChooserOpen){
            rootChooserOpen = false;
            return;
        }
        close();
    }

    public static void close(){
        rootChooserOpen = false;
        open = false;
        markResearchOpen(false);
    }

    // Read-only state for the opt-in real-pointer Chrome navigation gate.
    public static boolean isOpen(){ return open; }
    public static String activeRootContent(){ return activeRoot == null ? "" : activeRoot.content.name; }
    public static String selectedContent(){ return selected == null ? "" : selected.content.name; }

    private static void switchRoot(TechNode root){
        if(root == null) return;
        rootChooserOpen = false;
        activeRoot = root;
        selected = root;
        resourceCacheDirty = true;
        expanded.clear();
        expanded.add(root);
        expandUnlocked(root);
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
        // The original dialog displays inventory available NOW / remaining
        // research cost. Load one cached planet stock snapshot before building
        // either desktop or mobile requirement card.
        rebuildStoredResources();
        rebuildDetails(menuDetails, menuDetailsWidth);
        rebuildDetails(hudDetails, hudDetailsWidth);
        markResearchCatalog(totalNodes());
        markTree(activeRoot.name == null ? activeRoot.content.name : activeRoot.name,
            selected == null ? "" : selected.content.name, count, TechTree.roots.size);
        markGraphZoom(graphZoom);
        refreshResources(menuResources);
        refreshResources(hudResources);
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
        float next = Math.round(Math.max(0.3f, Math.min(1.4f, requested)) * 10f) / 10f;
        if(Math.abs(graphZoom - next) < 0.049f) return;
        graphZoom = next;
        refresh();
        focusSelected();
    }

    private static void selectGraphNode(TechNode node){
        boolean repeated = selected == node;
        selected = node;
        if(node.children.size > 0){
            // A first tap shows requirements; only a repeated tap folds an
            // already visible branch. Keeps unlocked sibling branches intact.
            if(repeated && node != activeRoot && expanded.contains(node)){
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

        // Stock ResearchDialog shows campaign objectives with real completion
        // icons, rather than prefixing plain text with "+" and "-".
        for(Objective objective : node.objectives){
            if(objective == null) continue;
            boolean complete = objectiveComplete(objective);
            Table objectiveRow = new Table();
            objectiveRow.left();
            Label label = new Label(objective.display());
            label.setWrap(true);
            label.setFontScale(0.8f);
            label.setColor(complete ? Color.lightGray : Color.scarlet);
            objectiveRow.add(label).width(textWidth - 23f).left();
            objectiveRow.add(new Image(complete ? Icon.ok : Icon.cancel))
                .size(18f).padLeft(5f);
            details.add(objectiveRow).width(textWidth).left().padTop(3f).row();
        }

        if(!(content instanceof SectorPreset)){
            // Vanilla ResearchDialog shows a cost-weighted research percentage
            // once any resources were invested in a technology. The pinned
            // Item.cost weights matter: not all ingredients count equally.
            float weightedRequired = 0f, weightedFinished = 0f;
            for(int i = 0; i < node.requirements.length; i++){
                ItemStack req = node.requirements[i];
                weightedRequired += req.item.cost * req.amount;
                weightedFinished += req.item.cost *
                    Math.min(node.finishedRequirements[i].amount, req.amount);
            }
            if(!content.unlocked() && weightedRequired > 0f && weightedFinished > 0f){
                int percent = Math.min(99, (int)(100f * weightedFinished / weightedRequired));
                Label progress = new Label(Core.bundle.format("research.progress", percent));
                progress.setColor(Pal.accent);
                progress.setFontScale(0.85f);
                details.add(progress).left().padTop(7f).row();
            }
            for(int i = 0; i < node.requirements.length; i++){
                ItemStack req = node.requirements[i];
                int completed = Math.min(node.finishedRequirements[i].amount, req.amount);
                int missing = Math.max(0, req.amount - completed);
                // Original ResearchDialog hides already-completed ingredient rows.
                if(missing == 0) continue;
                // Original card uses each item's actual atlas icon beside its
                // resource requirement, not a plain unstyled text sentence.
                Table line = new Table();
                line.left();
                if(req.item.uiIcon != null){
                    line.add(new Image(req.item.uiIcon)).size(20f).padRight(6f);
                }
                Label ingredient = new Label(req.item.localizedName);
                ingredient.setFontScale(0.8f);
                line.add(ingredient).left().growX();
                int available = storedResources == null ? 0 :
                    Math.max(0, storedResources.get(req.item));
                // Pinned ResearchDialog compares available items with the
                // remaining (not original) cost after partial investments.
                Label count = new Label(Math.min(available, missing) + " / " + missing);
                count.setFontScale(0.8f);
                count.setColor(missing == 0 ? Pal.accent :
                    available > 0 ? Color.lightGray : Color.scarlet);
                line.add(count).right().padLeft(5f);
                details.add(line).width(textWidth).left().padTop(4f).row();
            }
            if(!content.unlocked()){
                TextButton invest = details.button(Core.bundle.get("research", "Research"), () -> {
                    if(BrowserCampaignResearch.canSpend(content)){
                        BrowserCampaignResearch.spend(content);
                        resourceCacheDirty = true;
                        BrowserBuildPalette.refresh();
                        Core.app.post(BrowserResearchUi::refresh);
                    }
                }).width(mobile ? Math.min(textWidth, 200f) : Math.min(textWidth, 240f))
                    .height(mobile ? 50f : 46f).padTop(9f).left().get();
                // Mirror the original disabled research button state without
                // querying every Sector.items() on every UI frame. The same
                // cached stock snapshot drives the resource count labels.
                // The callback still checks actual resources before spending.
                boolean parentLocked = node.parent != null && !node.parent.content.unlocked();
                boolean objectivesLocked = false;
                for(Objective objective : node.objectives){
                    if(!objectiveComplete(objective)) objectivesLocked = true;
                }
                boolean spendable = node.requirements.length == 0;
                for(int i = 0; i < node.requirements.length; i++){
                    ItemStack req = node.requirements[i];
                    if(node.finishedRequirements[i].amount < req.amount &&
                        storedResources != null && storedResources.get(req.item) > 0){
                        spendable = true;
                    }
                }
                invest.setDisabled(parentLocked || objectivesLocked || !spendable);
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

    @JSBody(params = {"zoom"}, script =
        "document.documentElement.setAttribute('data-mindustry-research-tree-zoom', String(zoom));")
    private static native void markGraphZoom(float zoom);

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

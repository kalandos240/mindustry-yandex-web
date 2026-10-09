package mindustry.web;

import arc.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.type.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Accessible browser research catalog backed by the complete stock TechTree.
 * The compact Yandex campaign menu only lists a handful of storyline actions;
 * this panel exposes the actual resources, parent requirements and purchases
 * for all non-sector tech nodes, both from the menu and during active campaigns.
 */
public final class BrowserResearchUi{
    private static final int pageSize = 32;
    private static boolean initialized, open;
    private static int page;
    private static Table menuItems, hudItems;
    private static Label menuPage, hudPage;

    private BrowserResearchUi(){}

    public static void init(){
        if(initialized) return;
        if(ui == null || ui.menuGroup == null || ui.hudGroup == null){
            throw new IllegalStateException("Research UI requires initialized menu and HUD groups");
        }

        buildPanel(ui.menuGroup, true);
        buildPanel(ui.hudGroup, false);
        initialized = true;
        markResearchReady();
    }

    private static void buildPanel(Group parent, boolean menu){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> open && (menu ? state.isMenu() : state.isCampaign() && !state.gameOver));
        overlay.setBackground(Tex.black8);

        Table panel = new Table(Tex.pane2);
        panel.defaults().pad(3f);
        panel.add(Core.bundle.get("research", "Research")).left();
        panel.button(Core.bundle.get("back", "Back"), BrowserResearchUi::close)
            .size(mobile ? 98f : 90f, mobile ? 52f : 42f);
        panel.row();

        Table items = new Table();
        items.top().left();
        ScrollPane pane = new ScrollPane(items, Styles.smallPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        panel.add(pane).colspan(2)
            .width(mobile ? 330f : 500f)
            .height(Math.max(140f, Math.min(mobile ? 300f : 460f, Core.graphics.getHeight() - 125f)));
        panel.row();

        Table pages = new Table();
        pages.button("<", () -> changePage(-1)).size(64f, mobile ? 48f : 40f);
        Label counter = new Label("");
        pages.add(counter).minWidth(135f).center();
        pages.button(">", () -> changePage(1)).size(64f, mobile ? 48f : 40f);
        panel.add(pages).colspan(2);
        overlay.add(panel).center().width(mobile ? 350f : 522f);
        parent.addChild(overlay);

        if(menu){
            menuItems = items;
            menuPage = counter;
        }else{
            hudItems = items;
            hudPage = counter;
        }
    }

    public static void show(){
        if(!initialized || state == null || !(state.isMenu() || state.isCampaign())) return;
        page = 0;
        open = true;
        refresh();
        markResearchOpen(true);
    }

    public static void close(){
        open = false;
        markResearchOpen(false);
    }

    public static void refresh(){
        if(!initialized) return;
        rebuild(menuItems, menuPage);
        rebuild(hudItems, hudPage);
        markResearchCatalog(TechTree.all.size);
    }

    private static boolean eligible(TechNode node){
        return node != null && node.content != null && !(node.content instanceof SectorPreset);
    }

    private static int totalNodes(){
        int count = 0;
        for(TechNode node : TechTree.all) if(eligible(node)) count++;
        return count;
    }

    private static void changePage(int direction){
        int totalPages = Math.max(1, (totalNodes() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(totalPages - 1, page + direction));
        refresh();
    }

    private static String description(TechNode node){
        StringBuilder value = new StringBuilder(node.content.localizedName);
        if(node.content.unlocked()){
            return value.append(" - ").append(Core.bundle.get("unlocked", "Unlocked")).toString();
        }
        if(node.parent != null && !node.parent.content.unlocked()){
            value.append(" - ").append(Core.bundle.get("locked", "Locked")).append(": ")
                .append(node.parent.content.localizedName);
        }
        for(int i = 0; i < node.requirements.length; i++){
            ItemStack requirement = node.requirements[i];
            ItemStack progress = node.finishedRequirements[i];
            int remaining = Math.max(0, requirement.amount - progress.amount);
            if(remaining > 0){
                value.append("  ").append(requirement.item.localizedName).append(" ")
                    .append(remaining);
            }
        }
        return value.toString();
    }

    private static void rebuild(Table list, Label counter){
        if(list == null || counter == null) return;
        list.clear();
        int total = totalNodes();
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        if(page >= pages) page = pages - 1;
        counter.setText((page + 1) + " / " + pages);

        int index = 0;
        for(TechNode node : TechTree.all){
            if(!eligible(node)) continue;
            if(index >= (page + 1) * pageSize) break;
            if(index++ < page * pageSize) continue;

            TextButton button = list.button(description(node), () -> {
                if(!node.content.unlocked() && BrowserCampaignResearch.canSpend(node.content)){
                    BrowserCampaignResearch.spend(node.content);
                    BrowserBuildPalette.refresh();
                    Core.app.post(BrowserResearchUi::refresh);
                }
            }).width(mobile ? 308f : 474f).height(mobile ? 56f : 44f).get();
            button.getLabel().setFontScale(mobile ? 0.76f : 0.83f);
            button.getLabel().setWrap(true);
            button.update(() -> button.setDisabled(node.content.unlocked()
                || !BrowserCampaignResearch.canSpend(node.content)));
            list.row();
        }
        list.invalidateHierarchy();
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-research-catalog', 'techtree-all'); document.documentElement.setAttribute('data-mindustry-research-ui', 'ready');")
    private static native void markResearchReady();

    @JSBody(params = {"count"}, script = "document.documentElement.setAttribute('data-mindustry-research-tech-nodes', String(count));")
    private static native void markResearchCatalog(int count);

    @JSBody(params = {"shown"}, script = "document.documentElement.setAttribute('data-mindustry-research-open', shown ? 'yes' : 'no');")
    private static native void markResearchOpen(boolean shown);
}

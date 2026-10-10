package mindustry.web;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.style.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

import java.util.Locale;

import static mindustry.Vars.*;

/**
 * Native Arc Scene subset of the original Mindustry DatabaseDialog.
 *
 * Uses actual ContentLoader, UnlockableContent icons and pinned localization.
 * Items are presented in a paginated grid so opening the database does not
 * allocate hundreds of Scene buttons on constrained mobile/WebGL runtimes.
 */
public final class BrowserStockDatabaseUi{
    private static final ContentType[] tabs = {
        ContentType.item, ContentType.liquid, ContentType.block,
        ContentType.unit, ContentType.status
    };
    private static final int pageSize = 60;
    private static boolean initialized, open;
    private static ContentType category = ContentType.item;
    private static String search = "";
    private static int page, matches;
    private static UnlockableContent selected;
    private static Table tiles;
    private static Label detailTitle, detailText, pageLabel;
    private static Element closeButton;
    private static int pointerFrames;

    private BrowserStockDatabaseUi(){}

    public static void init(){
        if(initialized) return;
        if(ui == null || ui.menuGroup == null || content == null)
            throw new IllegalStateException("Native database needs initialized Mindustry content and Scene");

        Table overlay = new Table();
        overlay.name = "web-database-overlay";
        overlay.setFillParent(true);
        overlay.setBackground(Styles.black6);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> open && state.isMenu());

        Table dialog = new Table(Tex.pane2);
        dialog.name = "web-database-dialog";
        dialog.defaults().pad(4f);
        float width = Math.max(200f, Math.min(mobile ? 530f : 780f, Core.graphics.getWidth() - 20f));
        float height = Math.max(180f, Math.min(mobile ? 490f : 650f, Core.graphics.getHeight() - 18f));

        dialog.table(header -> {
            header.add(Core.bundle.get("database", "Database"))
                .style(Styles.outlineLabel).growX().left();
            closeButton = header.button(Icon.cancel, BrowserStockDatabaseUi::hide)
                .size(mobile ? 52f : 43f).name("web-database-back").get();
        }).growX().row();

        dialog.table(tabBar -> {
            tabBar.defaults().pad(2f);
            for(ContentType type : tabs){
                String label = Core.bundle.get("database-category." + type.name(),
                    type.name());
                tabBar.button(label, () -> {
                    category = type;
                    page = 0;
                    selected = null;
                    rebuild();
                }).width(Math.max(42f, (width - 26f) / tabs.length))
                    .height(mobile ? 48f : 40f)
                    .name("web-database-tab-" + type.name());
            }
        }).growX().row();

        dialog.table(searchRow -> {
            searchRow.image(Icon.zoom).padRight(6f);
            searchRow.field("", text -> {
                search = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
                page = 0;
                selected = null;
                rebuild();
            }).growX().name("web-database-search");
        }).growX().row();

        tiles = new Table();
        tiles.name = "web-database-items";
        tiles.top().left();
        ScrollPane pane = new ScrollPane(tiles, Styles.smallPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        dialog.add(pane).width(width - 24f)
            .height(Math.max(75f, height - (mobile ? 242f : 220f))).grow().row();

        dialog.table(paging -> {
            paging.defaults().pad(5f);
            paging.button(Icon.left, () -> {
                if(page > 0){page--;rebuild();}
            }).size(44f).name("web-database-previous");
            pageLabel = paging.add("").width(130f).get();
            paging.button(Icon.right, () -> {
                if((page + 1) * pageSize < matches){page++;rebuild();}
            }).size(44f).name("web-database-next");
        }).growX().row();

        dialog.table(details -> {
            detailTitle = details.add("").growX().left().get();
            details.row();
            detailText = details.add("").width(width - 30f).left().wrap().get();
        }).growX().row();
        overlay.add(dialog).width(width).height(height);

        overlay.update(() -> {
            if(open && state.isMenu() && (++pointerFrames & 15) == 0){
                Vec2 stage = closeButton.localToStageCoordinates(
                    new Vec2(closeButton.getWidth() * 0.5f, closeButton.getHeight() * 0.5f));
                Vec2 screen = Core.scene.getViewport().project(stage);
                markClosePosition(screen.x, screen.y);
            }
        });
        ui.menuGroup.addChild(overlay);
        initialized = true;
        rebuild();
        markReady();
    }

    public static void show(){
        if(!initialized) init();
        if(!state.isMenu()) return;
        open = true;
        markOpen(true);
    }

    public static void hide(){
        open = false;
        markOpen(false);
    }

    private static void rebuild(){
        if(tiles == null) return;
        tiles.clear();
        tiles.defaults().pad(4f);
        int cols = mobile ? 5 : 10;
        int visible = 0;
        matches = 0;
        for(Content c : content.getBy(category)){
            if(!(c instanceof UnlockableContent u) || u.hideDatabase
                || u.isHidden() || u.uiIcon == null
                || !search.isEmpty() && !u.localizedName.toLowerCase(Locale.ROOT).contains(search)){
                continue;
            }
            if(matches >= page * pageSize && matches < (page + 1) * pageSize){
                ImageButton button = new ImageButton(
                    new TextureRegionDrawable(u.uiIcon), Styles.clearNoneTogglei);
                button.name = "web-database-content-" + u.name;
                button.clicked(() -> select(u));
                tiles.add(button).size(mobile ? 49f : 54f)
                    .tooltip(u.localizedName);
                if(++visible % cols == 0) tiles.row();
            }
            matches++;
        }
        if(matches == 0){
            tiles.add(Core.bundle.get("none.found", "No results")).pad(12f);
        }
        pageLabel.setText((page + 1) + " / "
            + Math.max(1, (matches + pageSize - 1) / pageSize));
        if(selected == null || selected.getContentType() != category){
            detailTitle.setText("");
            detailText.setText("");
        }
        markResults(category.name(), matches);
    }

    private static void select(UnlockableContent u){
        selected = u;
        detailTitle.setText(u.localizedName);
        detailText.setText(u.description == null ? "" : u.description);
        markSelected(u.name);
    }

    @JSBody(script="""
        document.documentElement.setAttribute('data-mindustry-stock-database','ready');
        """)
    private static native void markReady();

    @JSBody(params={"value"}, script="""
        document.documentElement.setAttribute('data-mindustry-database-open',value?'yes':'no');
        """)
    private static native void markOpen(boolean value);

    @JSBody(params={"type","count"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-database-category',type);
        root.setAttribute('data-mindustry-database-count',String(count));
        """)
    private static native void markResults(String type, int count);

    @JSBody(params={"name"}, script="""
        document.documentElement.setAttribute('data-mindustry-database-selection',name);
        """)
    private static native void markSelected(String name);

    @JSBody(params={"x","y"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-database-close-x',String(x));
        root.setAttribute('data-mindustry-database-close-y',String(y));
        """)
    private static native void markClosePosition(float x, float y);
}

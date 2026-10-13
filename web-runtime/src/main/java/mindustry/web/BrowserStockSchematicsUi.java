package mindustry.web;

import arc.*;
import arc.input.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.style.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

import java.util.Locale;

import static mindustry.Vars.*;

/**
 * Browser-compatible stock Schematics library.
 *
 * Reuses the real Schematics .msch persistence and InputHandler.useSchematic()
 * path. The pinned SchematicsDialog eagerly reaches Steam, OS file choosers
 * and thumbnail framebuffer rendering, so this mounts a compact Arc Scene
 * surface rather than pulling those unsupported desktop dependencies into TeaVM.
 */
public final class BrowserStockSchematicsUi{
    private static final int pageSize = 30;
    private static Table overlay, results;
    private static Label pageLabel, status;
    private static boolean open;
    private static String search = "";
    private static int page, matches, pointerFrames;
    private static Schematic pendingDelete;
    private static Element closeButton;

    private BrowserStockSchematicsUi(){}

    public static void init(){
        if(overlay != null) return;
        if(Core.scene == null || ui == null){
            throw new IllegalStateException("Native schematic library requires the Mindustry Scene");
        }

        // The lean browser launcher skips ClientLauncher.loadAsync(), which
        // normally initializes this global. Establish the original registry
        // once so DesktopInput schematic selection and persistence both work.
        if(schematics == null){
            schematics = new Schematics();
            schematics.loadSync();
        }

        overlay = new Table();
        overlay.name = "web-stock-schematics-overlay";
        overlay.setFillParent(true);
        overlay.setBackground(Styles.black6);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> open && (state.isMenu() || state.isGame() && !state.gameOver));

        float width = Math.max(210f, Math.min(mobile ? 510f : 790f, Core.graphics.getWidth() - 16f));
        float height = Math.max(190f, Math.min(mobile ? 620f : 740f, Core.graphics.getHeight() - 16f));
        Table panel = new Table(Tex.pane2);
        panel.defaults().pad(4f);

        panel.table(header -> {
            header.add("@schematics").style(Styles.outlineLabel).growX().left();
            closeButton = header.button(Icon.cancel, BrowserStockSchematicsUi::hide)
                .size(mobile ? 51f : 45f).name("web-schematics-back").get();
        }).growX().padTop(7f).row();

        panel.table(searchBar -> {
            searchBar.image(Icon.zoom).padRight(5f);
            searchBar.field("", value -> {
                search = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                page = 0;
                pendingDelete = null;
                rebuild();
            }).growX().name("web-schematics-search");
        }).growX().row();

        results = new Table();
        results.name = "web-schematics-items";
        results.top().left();
        ScrollPane scroll = new ScrollPane(results, Styles.smallPane);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).width(width - 24f).height(Math.max(85f, height - 212f)).grow().row();

        panel.table(footer -> {
            footer.defaults().pad(3f);
            footer.button(Icon.left, () -> {
                if(page > 0){page--;rebuild();}
            }).size(42f).name("web-schematics-prev");
            pageLabel = footer.add("").width(82f).get();
            footer.button(Icon.right, () -> {
                if((page + 1) * pageSize < matches){page++;rebuild();}
            }).size(42f).name("web-schematics-next");
            footer.button("@schematic.import", Icon.download, BrowserStockSchematicsUi::showImport)
                .height(45f).minWidth(mobile ? 98f : 158f).name("web-schematics-import");
        }).growX().row();

        status = panel.add("").left().width(width - 30f).get();
        panel.row();
        overlay.add(panel).width(width).height(height);
        overlay.addListener(new InputListener(){
            @Override
            public boolean keyDown(InputEvent event, KeyCode key){
                if(key == KeyCode.escape || key == KeyCode.back){
                    hide();
                    return true;
                }
                return false;
            }
        });
        overlay.update(() -> {
            if(!open) return;
            if(!(state.isMenu() || state.isGame() && !state.gameOver)){
                hide();
                return;
            }
            if((++pointerFrames & 15) == 0 && closeButton != null){
                Vec2 stage = closeButton.localToStageCoordinates(
                    new Vec2(closeButton.getWidth() / 2f, closeButton.getHeight() / 2f));
                Core.scene.getViewport().project(stage);
                markClosePoint(stage.x, stage.y);
            }
        });
        Core.scene.add(overlay);
        rebuild();
        markReady();
    }

    public static void show(){
        if(overlay == null) init();
        if(!(state.isMenu() || state.isGame() && !state.gameOver)) return;
        pendingDelete = null;
        open = true;
        rebuild();
        Core.scene.setKeyboardFocus(overlay);
        markOpen(true);
    }

    public static boolean isOpen(){
        return open;
    }

    public static void hide(){
        open = false;
        pendingDelete = null;
        if(Core.scene != null && Core.scene.getKeyboardFocus() == overlay){
            Core.scene.setKeyboardFocus(null);
        }
        markOpen(false);
    }

    private static void rebuild(){
        if(results == null || schematics == null) return;
        results.clearChildren();
        results.defaults().pad(4f);
        int visible = 0;
        int columns = mobile || Core.graphics.getWidth() < 560f ? 1 : 2;
        float tileWidth = columns == 1
            ? Math.max(190f, Math.min(390f, Core.graphics.getWidth() - 50f)) : 340f;
        matches = 0;

        for(Schematic schematic : schematics.all()){
            if(!search.isEmpty() && !schematic.name().toLowerCase(Locale.ROOT).contains(search)) continue;
            int matchingIndex = matches++;
            if(matchingIndex < page * pageSize || matchingIndex >= (page + 1) * pageSize) continue;

            Table tile = new Table(Tex.button);
            tile.defaults().pad(3f);
            tile.table(line -> {
                if(schematic.tiles.size > 0 && schematic.tiles.first().block.uiIcon != null){
                    line.image(new TextureRegionDrawable(schematic.tiles.first().block.uiIcon))
                        .size(32f).padRight(7f);
                }else{
                    line.image(Icon.paste).size(32f).padRight(7f);
                }
                line.button(schematic.name(), () -> use(schematic)).width(mobile ? 135f : 255f)
                    .height(43f).name("web-schematics-item-" + matchingIndex);
            }).growX().row();

            tile.add(schematic.width + " x " + schematic.height + "  (" + schematic.tiles.size + ")")
                .left().row();
            tile.table(actions -> {
                actions.defaults().pad(2f);
                actions.button(Icon.info, () -> {
                    status.setText(schematic.name() + ": " + schematic.description());
                }).size(38f);
                actions.button(Icon.upload, () -> {
                    Core.app.setClipboardText(schematics.writeBase64(schematic));
                    status.setText(Core.bundle.get("copied", "Copied"));
                }).size(38f);
                if(schematic.mod == null){
                    actions.button(Icon.trash, () -> {
                        if(pendingDelete == schematic){
                            schematics.remove(schematic);
                            pendingDelete = null;
                            rebuild();
                        }else{
                            pendingDelete = schematic;
                            status.setText(Core.bundle.get("schematic.delete.confirm", "Click delete again to confirm"));
                        }
                    }).size(38f);
                }
            }).growX();
            results.add(tile).width(tileWidth).top();
            if(++visible % columns == 0) results.row();
        }
        if(matches == 0){
            results.add(search.isEmpty() ? Core.bundle.get("none", "None")
                : Core.bundle.get("none.found", "No results")).pad(20f);
        }
        if(pageLabel != null) pageLabel.setText((page + 1) + " / " + Math.max(1, (matches + pageSize - 1) / pageSize));
        markEntries(matches);
    }

    private static void use(Schematic schematic){
        if(state.isMenu()){
            status.setText(schematic.name() + ": " + schematic.description());
            return;
        }
        if(!state.rules.schematicsAllowed){
            status.setText(Core.bundle.get("schematic.disabled", "Schematics are disabled here."));
            return;
        }
        control.input.useSchematic(schematic);
        hide();
        markUsed(schematic.name());
    }

    private static void showImport(){
        // Native Arc text field accepts pasted base64 .msch payloads. This
        // also works without Navigator clipboard permission in iframes.
        results.clearChildren();
        Table form = new Table(Tex.button);
        form.defaults().pad(6f);
        form.add("@schematic.import").row();
        TextField text = new TextField("");
        text.setMessageText("bXNja...");
        form.add(text).width(mobile ? 290f : 530f).height(56f).row();
        form.button("@ok", Icon.ok, () -> {
            try{
                String raw = text.getText().trim();
                if(raw.isEmpty()) throw new IllegalArgumentException("Empty schematic");
                Schematic schematic = Schematics.readBase64(raw);
                schematic.removeSteamID();
                schematics.add(schematic);
                page = 0;
                search = "";
                status.setText(Core.bundle.get("schematic.saved", "Schematic saved"));
                rebuild();
                markImported(schematic.name());
            }catch(Exception failure){
                status.setText(Core.bundle.get("data.invalid", "Invalid data"));
            }
        }).size(210f, 53f);
        form.button("@back", Icon.left, BrowserStockSchematicsUi::rebuild).size(210f, 53f).row();
        results.add(form).pad(12f);
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-stock-schematics','ready');")
    private static native void markReady();

    @JSBody(params={"open"}, script = "document.documentElement.setAttribute('data-mindustry-schematics-open',open?'yes':'no');")
    private static native void markOpen(boolean value);

    @JSBody(params={"count"}, script = "document.documentElement.setAttribute('data-mindustry-schematics-count',String(count));")
    private static native void markEntries(int count);

    @JSBody(params={"x","y"}, script = "document.documentElement.setAttribute('data-mindustry-schematics-close-x',String(x));document.documentElement.setAttribute('data-mindustry-schematics-close-y',String(y));")
    private static native void markClosePoint(float x, float y);

    @JSBody(params={"name"}, script = "document.documentElement.setAttribute('data-mindustry-schematics-used',name);")
    private static native void markUsed(String name);

    @JSBody(params={"name"}, script = "document.documentElement.setAttribute('data-mindustry-schematics-imported',name);")
    private static native void markImported(String name);
}

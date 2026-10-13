package mindustry.logic;

import arc.*;
import arc.func.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.logic.LStatements.*;
import mindustry.ui.*;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;
import org.teavm.jso.JSBody;

import java.util.Locale;

import static mindustry.Vars.*;

/**
 * Browser-hosted *original* Mindustry logic instruction canvas.
 *
 * UI.loadSync() intentionally omits heavyweight LogicDialog and its entire
 * desktop modal graph. Keep the original LCanvas, LAssembler, LStatements
 * instruction widgets, and LogicBuild's compressed configure() path intact.
 */
public final class BrowserStockLogicUi{
    private static Table overlay, pane, chooser;
    private static Stack editorArea;
    private static ScrollPane chooserScroll;
    private static TextField chooserSearch;
    private static LCanvas canvas;
    private static TextArea codeArea;
    private static Label status;
    private static LogicBuild editing;
    private static boolean open, selecting, textMode, forceEditor;
    private static String originalCode;

    private BrowserStockLogicUi(){}

    public static void init(){
        if(overlay != null) return;
        if(Core.scene == null) throw new IllegalStateException("Original logic canvas needs Arc Scene");

        overlay = new Table();
        overlay.name = "web-native-logic-overlay";
        overlay.setFillParent(true);
        overlay.setBackground(Styles.black6);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> open && state.isGame() && !state.gameOver);

        float width = Math.max(230f, Math.min(mobile ? 540f : 890f, Core.graphics.getWidth() - 14f));
        float height = Math.max(200f, Math.min(mobile ? 680f : 730f, Core.graphics.getHeight() - 14f));

        pane = new Table(Tex.pane2);
        pane.defaults().pad(3f);
        pane.table(title -> {
            title.add("@logic").style(Styles.outlineLabel).growX().left();
            title.button(Icon.cancel, BrowserStockLogicUi::cancel)
                .size(43f).name("web-logic-close");
        }).growX().row();

        canvas = new LCanvas();
        canvas.privileged = false;
        canvas.visible(() -> !selecting && !textMode);
        chooser = new Table();
        chooser.top().left();
        chooserScroll = new ScrollPane(chooser, Styles.smallPane);
        chooserScroll.setScrollingDisabled(true, false);
        chooserScroll.setFadeScrollBars(false);
        Table chooserPage = new Table();
        chooserPage.top();
        chooserSearch = new TextField("");
        chooserSearch.setMessageText("@players.search");
        chooserSearch.changed(() -> rebuildChooser(chooserSearch.getText()));
        chooserPage.add(chooserSearch).growX().height(49f).pad(4f).row();
        chooserPage.add(chooserScroll).grow();
        chooserPage.visible(() -> selecting);
        codeArea = new TextArea("");
        codeArea.setPrefRows(12);
        codeArea.visible(() -> textMode);

        editorArea = new Stack();
        editorArea.add(canvas);
        editorArea.add(chooserPage);
        editorArea.add(codeArea);
        pane.add(editorArea).width(width - 20f)
            .height(Math.max(85f, height - (mobile ? 225f : 145f))).grow().row();

        pane.table(actions -> {
            actions.defaults().pad(3f);
            actions.button("@add", Icon.add, () -> {
                if(textMode) return;
                selecting = !selecting;
                if(selecting){
                    chooserSearch.setText("");
                    rebuildChooser("");
                    Core.scene.setKeyboardFocus(chooserSearch);
                }
            }).size(mobile ? 104f : 108f, 47f).name("web-logic-add");
            actions.button("@edit", Icon.pencil, () -> {
                if(selecting) selecting = false;
                if(textMode){
                    try{
                        canvas.load(codeArea.getText().replace("\r\n", "\n"));
                        textMode = false;
                        status.setText("");
                    }catch(Throwable error){
                        status.setText(Core.bundle.get("data.invalid", "Invalid logic code"));
                    }
                }else{
                    codeArea.setText(canvas.save());
                    textMode = true;
                    Core.scene.setKeyboardFocus(codeArea);
                }
            }).size(mobile ? 104f : 108f, 47f).name("web-logic-source");
            actions.button("@copy.clipboard", Icon.copy,
                () -> Core.app.setClipboardText(textMode ? codeArea.getText() : canvas.save()))
                .size(mobile ? 104f : 126f, 47f).name("web-logic-copy");
            if(mobile) actions.row();
            actions.button("@clear", Icon.trash, () -> {
                if(selecting) selecting = false;
                if(textMode) codeArea.setText("");
                else canvas.clearStatements();
            }).size(mobile ? 104f : 108f, 47f).name("web-logic-clear");
            actions.button("@ok", Icon.ok, BrowserStockLogicUi::save)
                .size(mobile ? 104f : 108f, 47f).name("web-logic-save");
            actions.button("@cancel", Icon.left, BrowserStockLogicUi::cancel)
                .size(mobile ? 104f : 108f, 47f).name("web-logic-cancel");
        }).growX().row();

        status = pane.add("").left().width(width - 20f).get();
        pane.row();
        overlay.add(pane).width(width).height(height);
        overlay.addListener(new InputListener(){
            @Override
            public boolean keyDown(InputEvent event, KeyCode key){
                if(key == KeyCode.escape || key == KeyCode.back){
                    if(selecting){selecting = false;}
                    else if(textMode){textMode = false;}
                    else cancel();
                    return true;
                }
                return false;
            }
        });
        overlay.update(() -> {
            if(open && (!state.isGame() || state.gameOver)) cancel();
        });
        Core.scene.add(overlay);
        Events.on(LogicBlock.WebLogicEditorRequest.class, event ->
            show(event.build, event.forceEditor));
        markReady();
    }

    private static void show(LogicBuild building, boolean asEditor){
        if(building == null || !building.isValid() || !state.isGame()) return;
        init();
        editing = building;
        forceEditor = asEditor;
        originalCode = building.code == null ? "" : building.code;
        canvas.privileged = building.executor.privileged;
        canvas.clearStatements();
        canvas.rebuild();
        canvas.pane.setScrollYForce(0f);
        try{
            canvas.load(originalCode);
        }catch(Throwable invalid){
            canvas.load("");
        }
        selecting = false;
        textMode = false;
        status.setText("");
        open = true;
        Core.scene.setKeyboardFocus(overlay);
        markOpen(true, canvas.statements.getChildren().size);
    }

    private static void rebuildChooser(String search){
        chooser.clearChildren();
        chooser.top().left();
        chooser.defaults().pad(3f);
        String filter = search == null ? "" : search.toLowerCase(Locale.ROOT);
        int found = 0;
        for(Prov<LStatement> factory : LogicIO.allStatements){
            LStatement statement = factory.get();
            if(statement instanceof InvalidStatement || statement.hidden()
                || statement.privileged() && !canvas.privileged
                || statement.nonPrivileged() && canvas.privileged
                || !canvas.privileged && !state.rules.logicUnitControl
                    && statement.category() == LCategory.unit
                || !filter.isEmpty() && !statement.name().toLowerCase(Locale.ROOT).contains(filter)
                    && !statement.typeName().toLowerCase(Locale.ROOT).contains(filter)) continue;

            chooser.button(statement.name(), () -> {
                canvas.add(factory.get());
                selecting = false;
                status.setText("");
            }).width(mobile ? 210f : 325f).height(47f)
                .name("web-logic-add-" + found);
            if(++found % (mobile ? 1 : 2) == 0) chooser.row();
        }
        if(found == 0) chooser.add("@none.found");
        markChoices(found);
    }

    private static void save(){
        if(!open || editing == null) return;
        String code = textMode ? codeArea.getText().replace("\r\n", "\n") : canvas.save();
        if(code == null) code = "";
        if(code.equals(originalCode)){
            cancel();
            return;
        }
        if(!editing.isValid()){
            status.setText(Core.bundle.get("invalid", "Processor no longer exists"));
            return;
        }
        try{
            // Use exactly the vanilla relative link capture and compressed
            // LogicBuild configuration. This is *not* a direct field assignment.
            byte[] compressed = LogicBlock.compress(code, editing.relativeConnections());
            if(compressed.length > 16000){
                status.setText(Core.bundle.format("logic.error.toolong", 16000, compressed.length));
                return;
            }
            boolean wasEditor = state.rules.editor;
            try{
                if(forceEditor) state.rules.editor = true;
                editing.configure(compressed);
            }finally{
                state.rules.editor = wasEditor;
            }
            markSaved(code.length());
            cancel();
        }catch(Throwable error){
            status.setText(Core.bundle.get("data.invalid", "Invalid logic program"));
        }
    }

    private static void cancel(){
        open = false;
        editing = null;
        selecting = false;
        textMode = false;
        if(Core.scene != null && Core.scene.getKeyboardFocus() == overlay){
            Core.scene.setKeyboardFocus(null);
        }
        markOpen(false, 0);
    }

    public static boolean isOpen(){
        return open;
    }

    @JSBody(script="document.documentElement.setAttribute('data-mindustry-native-logic','ready');document.documentElement.setAttribute('data-mindustry-native-logic-canvas','mindustry.logic.LCanvas');")
    private static native void markReady();

    @JSBody(params={"state","count"},script="const r=document.documentElement;r.setAttribute('data-mindustry-logic-open',state?'yes':'no');r.setAttribute('data-mindustry-logic-instructions',String(count));")
    private static native void markOpen(boolean value, int count);

    @JSBody(params={"count"},script="document.documentElement.setAttribute('data-mindustry-logic-choices',String(count));")
    private static native void markChoices(int count);

    @JSBody(params={"chars"},script="document.documentElement.setAttribute('data-mindustry-logic-saved-chars',String(chars));")
    private static native void markSaved(int chars);
}

package mindustry.logic;

import arc.*;
import arc.func.*;
import arc.input.*;
import arc.math.geom.*;
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
    private static TextField chooserSearch, variableSearch;
    private static Table variableRows;
    private static LCanvas canvas;
    private static TextArea codeArea;
    private static Label status;
    private static LogicBuild editing;
    private static boolean open, selecting, textMode, variablesMode, forceEditor, pausedHere;
    private static String originalCode;
    private static int pointerFrames;

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
        canvas.visible(() -> !selecting && !textMode && !variablesMode);
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

        // The original LogicDialog "Variables" action reads the active
        // LExecutor.vars array. Keep the values live while the processor runs;
        // do not replace the processor with a Web-only simulation.
        variableRows = new Table();
        variableRows.top().left();
        ScrollPane variableScroll = new ScrollPane(variableRows, Styles.smallPane);
        variableScroll.setScrollingDisabled(true, false);
        variableScroll.setFadeScrollBars(false);
        Table variablePage = new Table();
        variablePage.top();
        variableSearch = new TextField("");
        variableSearch.setMessageText("@players.search");
        variableSearch.changed(BrowserStockLogicUi::rebuildVariables);
        variablePage.add(variableSearch).growX().height(49f).pad(4f).row();
        variablePage.add(variableScroll).grow();
        variablePage.visible(() -> variablesMode);

        editorArea = new Stack();
        editorArea.add(canvas);
        editorArea.add(chooserPage);
        editorArea.add(codeArea);
        editorArea.add(variablePage);
        pane.add(editorArea).width(width - 20f)
            .height(Math.max(85f, height - (mobile ? 225f : 145f))).grow().row();

        float actionWidth = Math.max(62f, Math.min(106f, (width - 40f) / (mobile ? 3f : 5f)));
        pane.table(actions -> {
            actions.defaults().pad(3f);
            actions.button("@add", Icon.add, () -> {
                if(textMode) return;
                variablesMode = false;
                selecting = !selecting;
                if(selecting){
                    chooserSearch.setText("");
                    rebuildChooser("");
                    Core.scene.setKeyboardFocus(chooserSearch);
                }
            }).size(actionWidth, 47f).name("web-logic-add");
            actions.button("@edit", Icon.pencil, () -> {
                if(selecting) selecting = false;
                if(variablesMode) variablesMode = false;
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
            }).size(actionWidth, 47f).name("web-logic-source");
            actions.button("@variables", Icon.menu, () -> {
                if(editing == null || !open) return;
                if(textMode){
                    try{
                        canvas.load(codeArea.getText().replace("\r\n", "\n"));
                        textMode = false;
                    }catch(Throwable problem){
                        status.setText(Core.bundle.get("data.invalid", "Invalid logic code"));
                        return;
                    }
                }
                selecting = false;
                variablesMode = !variablesMode;
                if(variablesMode){
                    variableSearch.setText("");
                    rebuildVariables();
                }
                markVariablesOpen(variablesMode);
            }).size(actionWidth, 47f).name("web-logic-variables");
            if(mobile) actions.row();
            actions.button("@copy.clipboard", Icon.copy,
                () -> Core.app.setClipboardText(textMode ? codeArea.getText() : canvas.save()))
                .size(actionWidth, 47f).name("web-logic-copy");
            actions.button("@load.clipboard", Icon.download, () -> {
                String code = Core.app.getClipboardText();
                if(code == null || code.isEmpty()){
                    status.setText(Core.bundle.get("empty", "Clipboard is empty"));
                    return;
                }
                code = code.replace("\r\n", "\n");
                if(textMode){
                    codeArea.setText(code);
                }else{
                    try{
                        canvas.load(code);
                        selecting = false;
                        variablesMode = false;
                        status.setText("");
                    }catch(Throwable invalid){
                        status.setText(Core.bundle.get("data.invalid", "Invalid logic code"));
                    }
                }
            }).size(actionWidth, 47f).name("web-logic-paste");
            if(!mobile) actions.row();
            actions.button("@clear", Icon.trash, () -> {
                if(selecting) selecting = false;
                if(variablesMode) variablesMode = false;
                if(textMode) codeArea.setText("");
                else canvas.clearStatements();
            }).size(actionWidth, 47f).name("web-logic-clear");
            if(mobile) actions.row();
            actions.button("@ok", Icon.ok, BrowserStockLogicUi::save)
                .size(actionWidth, 47f).name("web-logic-save");
            actions.button("@logic.restart", Icon.refresh, () -> save(true))
                .size(actionWidth, 47f).name("web-logic-restart");
            actions.button("@cancel", Icon.left, BrowserStockLogicUi::cancel)
                .size(actionWidth, 47f).name("web-logic-cancel");
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
                    else if(variablesMode){variablesMode = false;markVariablesOpen(false);}
                    else cancel();
                    return true;
                }
                return false;
            }
        });
        overlay.update(() -> {
            if(open && (!state.isGame() || state.gameOver)){
                cancel();
            }else if(open && (++pointerFrames & 15) == 0){
                markPointer("variables", "web-logic-variables");
                markPointer("close", "web-logic-close");
            }
        });
        Core.scene.add(overlay);
        Events.on(LogicBlock.WebLogicEditorRequest.class, event ->
            show(event.build, event.forceEditor));
        markReady();
    }

    private static void markPointer(String key, String actorName){
        Element actor = Core.scene.root.find(actorName);
        if(actor == null || !actor.isVisible()) return;
        Vec2 center = actor.localToStageCoordinates(new Vec2(actor.getWidth() / 2f, actor.getHeight() / 2f));
        Core.scene.getViewport().project(center);
        markPointerDom(key, center.x, center.y);
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
        variablesMode = false;
        markVariablesOpen(false);
        status.setText("");
        open = true;
        // Vanilla LogicDialog.shouldPause freezes the underlying local world.
        // Use our validated browser campaign/local-map pause state machines,
        // so a processor cannot execute against half-edited instructions.
        pausedHere = false;
        if(state.isPlaying() && !state.rules.pauseDisabled){
            if(mindustry.web.BrowserCampaignRuntime.active()){
                mindustry.web.BrowserCampaignRuntime.pause();
            }else if(mindustry.web.BrowserLocalMapRuntime.active()){
                mindustry.web.BrowserLocalMapRuntime.pause();
            }
            pausedHere = state.isPaused();
        }
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

    private static void rebuildVariables(){
        if(variableRows == null) return;
        variableRows.clearChildren();
        variableRows.defaults().pad(3f);
        if(editing == null || editing.executor == null) return;
        String filter = variableSearch == null ? "" : variableSearch.getText().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for(LVar variable : editing.executor.vars){
            if(variable == null || variable.constant ||
                !variable.name.toLowerCase(Locale.ROOT).contains(filter)) continue;
            variableRows.add(variable.name).left().width(mobile ? 95f : 195f);
            Label value = variableRows.add("").left().growX().get();
            value.update(() -> {
                if(open && variablesMode && editing != null){
                    String shown = variable.isobj
                        ? (variable.objval == null ? "null" : String.valueOf(variable.objval))
                        : (Math.abs(variable.numval - Math.rint(variable.numval)) < 0.00001d
                            ? Long.toString(Math.round(variable.numval))
                            : Double.toString(variable.numval));
                    value.setText(shown.length() > 120 ? shown.substring(0, 117) + "..." : shown);
                }
            });
            variableRows.row();
            count++;
        }
        if(count == 0){
            variableRows.add(Core.bundle.get("none.found", "No results"));
        }
        markVariableCount(count);
    }

    private static void save(){
        save(false);
    }

    private static void save(boolean restart){
        if(!open || editing == null) return;
        String code = textMode ? codeArea.getText().replace("\r\n", "\n") : canvas.save();
        if(code == null) code = "";
        if(!restart && code.equals(originalCode)){
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
        if(pausedHere && state.isPaused() && !state.gameOver){
            if(mindustry.web.BrowserCampaignRuntime.active()){
                mindustry.web.BrowserCampaignRuntime.resume();
            }else if(mindustry.web.BrowserLocalMapRuntime.active()){
                mindustry.web.BrowserLocalMapRuntime.resume();
            }
        }
        pausedHere = false;
        selecting = false;
        textMode = false;
        variablesMode = false;
        markVariablesOpen(false);
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

    @JSBody(params={"key","x","y"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-logic-'+key+'-x',String(x));
        root.setAttribute('data-mindustry-logic-'+key+'-y',String(y));
        """)
    private static native void markPointerDom(String key,float x,float y);

    @JSBody(params={"chars"},script="document.documentElement.setAttribute('data-mindustry-logic-saved-chars',String(chars));")
    private static native void markSaved(int chars);

    @JSBody(params={"open"},script="document.documentElement.setAttribute('data-mindustry-logic-variables-open',open?'yes':'no');")
    private static native void markVariablesOpen(boolean open);

    @JSBody(params={"count"},script="document.documentElement.setAttribute('data-mindustry-logic-variables-count',String(count));")
    private static native void markVariableCount(int count);
}

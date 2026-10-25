#!/usr/bin/env python3
"""Keep stock custom-game widgets; redirect only their launch boundary."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "work/Mindustry/core/src/mindustry/ui/dialogs/MapPlayDialog.java"
text = path.read_text()
replacements = [
    ("    public @Nullable Runnable playListener;", "    public @Nullable Runnable playListener;\n    public static arc.func.Cons2<Map, Rules> webPlay;"),
    ("            control.playMap(map, rules, playtesting);", "            if(webPlay == null) throw new IllegalStateException(\"Browser map launcher not installed\");\n            webPlay.get(map, rules);"),
    ("if(mode.hidden) continue;", "if(mode.hidden || mode == Gamemode.pvp) continue;"),
]
for old, new in replacements:
    if old not in text:
        raise SystemExit("Pinned MapPlayDialog anchor changed: " + old)
    text = text.replace(old, new)
# Original desktop dimensions exceed short portal frames. Keep the stock
# widgets, with a scrolling body and fixed Back/Play buttons on small screens.
start = text.index("    public void show(Map map, boolean playtesting){")
end = text.index("    private void displayGameModeHelp(){", start)
body = text[start:end].replace("cont.", "webBody.")
body = body.replace("        webBody.clearChildren();", """        cont.clearChildren();
        Table webBody = new Table();
        ScrollPane webPane = new ScrollPane(webBody);
        webPane.setFadeScrollBars(false);
        webPane.setScrollingDisabled(true, false);
        cont.add(webPane).grow().minWidth(0f).minHeight(0f);""")
body = body.replace("        addCloseButton();", """        float webButtonWidth = Math.max(1f, Math.min(210f, (Core.scene.getWidth() - 40f) / 2f));
        addCloseButton(webButtonWidth);""")
body = body.replace("}).size(210f, 64f);", "}).size(webButtonWidth, 64f);")
body = body.replace("        show();", "        show();\n        webFit(this);", 1)
text = text[:start] + body + text[end:]
# A resize can arrive while CustomRulesDialog covers its parent; stock onResize
# only runs for the top modal. Refit existing actors without rebuilding rules.
text = text.replace('        setFillParent(false);', '        setFillParent(false);\n        webTrack(this);', 1)
text = text.replace("        d.show();", "        webTrack(d);\n        d.show();\n        webFit(d);", 1)
helper = """
    private static void webTrack(BaseDialog dialog){
        final float[] viewport = {-1f, -1f};
        dialog.update(() -> {
            float width = Core.scene.getWidth(), height = Core.scene.getHeight();
            if(width == viewport[0] && height == viewport[1]) return;
            viewport[0] = width;
            viewport[1] = height;
            if(dialog instanceof MapPlayDialog){
                float buttonWidth = Math.max(1f, Math.min(210f, (width - 40f) / 2f));
                dialog.buttons.getCells().each(cell -> cell.width(buttonWidth));
            }
            webFit(dialog);
        });
    }

    private static void webFit(BaseDialog dialog){
        float width = Math.max(1f, Core.scene.getWidth() - Core.scene.marginLeft - Core.scene.marginRight);
        float height = Math.max(1f, Core.scene.getHeight() - Core.scene.marginTop - Core.scene.marginBottom);
        dialog.setSize(Math.min(dialog.getPrefWidth(), width), Math.min(dialog.getPrefHeight(), height));
        dialog.validate();
        dialog.centerWindow();
    }
"""
text = text[:text.rfind("}")] + helper + "}\n"
path.write_text(text)
print("Kept stock CustomGameDialog/MapPlayDialog with local browser launch callback")

# Keep stock rule/color dialogs' bottom actions reachable in narrow portal fields.
for filename in ("CustomRulesDialog.java", "ColorPicker.java"):
    target = path.with_name(filename)
    source = target.read_text()
    constructor = "        super(\"@pickcolor\");" if filename == "ColorPicker.java" else "        this.showRuleEditRule = showRuleEditRule;"
    if source.count(constructor) != 1:
        raise SystemExit("Pinned responsive-dialog constructor changed: " + filename)
    source = source.replace(constructor, constructor + "\n        onResize(this::webFitButtons);", 1)
    method = """
    private void webFitButtons(){
        int count = buttons.getCells().size;
        if(count == 0) return;
        float width = Math.max(1f, Math.min(210f, (arc.Core.scene.getWidth() - 40f) / count));
        buttons.getCells().each(cell -> cell.width(width));
    }
"""
    if filename == "CustomRulesDialog.java":
        search_clear = '''                ruleSearch = "";
                setupMain();'''
        if source.count(search_clear) != 1:
            raise SystemExit("Pinned custom rules search-clear callback changed")
        source = source.replace(search_clear, '''                ruleSearch = "";
                field.setText("");
                setupMain();''', 1)
        checks = """        // A category's wide numeric rows must not stretch checkbox hitboxes
        // beyond the portal. Keep natural desktop widths; wrap only as needed.
        float maxCheckWidth = Math.max(1f, arc.Core.scene.getWidth() - 80f);
        cont.forEach(actor -> {
            if(actor instanceof CheckBox box && box.parent instanceof Table table){
                box.getLabel().setWrap(false);
                float natural = box.getPrefWidth();
                box.getLabel().setWrap(true);
                Cell<?> cell = table.getCell(box);
                if(cell != null) cell.minWidth(Math.min(natural, maxCheckWidth)).maxWidth(maxCheckWidth);
            }
        });
"""
        method = method.replace("        int count = buttons.getCells().size;", checks + "        int count = buttons.getCells().size;")
        source = source.replace("        shown(this::setup);", "        shown(this::setup);\n        shown(this::webFitButtons);", 1)
    else:
        source = source.replace("        addCloseButton();", "        addCloseButton(Math.max(1f, Math.min(210f, (arc.Core.scene.getWidth() - 40f) / 2f)));", 1)
    source = source[:source.rfind("}")] + method + "}\n"
    target.write_text(source)

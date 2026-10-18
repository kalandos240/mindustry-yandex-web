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
text = text[:start] + body + text[end:]
path.write_text(text)
print("Kept stock CustomGameDialog/MapPlayDialog with local browser launch callback")

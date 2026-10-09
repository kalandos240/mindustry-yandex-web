#!/usr/bin/env python3
"""Keep vanilla PlacementFragment usable with browser's lazy/lean dialog graph.

The stock Mindustry fragment is used without recreating desktop networking,
chat or encyclopedia dialogs that aren't mounted by BrowserUiRuntime.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch-mindustry-placement-web.py PlacementFragment.java")
path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
replacements = {
    'if(ui.chatfrag.shown() || ui.consolefrag.shown() || Core.scene.hasKeyboard()) return false;':
    'if((ui.chatfrag != null && ui.chatfrag.shown()) || (ui.consolefrag != null && ui.consolefrag.shown()) || Core.scene.hasKeyboard()) return false;',
    'ui.content.show(unit.type());': 'if(ui.content != null) ui.content.show(unit.type());',
    'ui.content.show(displayBlock);': 'if(ui.content != null) ui.content.show(displayBlock);',
}
# Vanilla schedules a second ScrollPane force-layout while constructing the HUD.
# That runs in BrowserApplication.runPostedTasks() before the first rendered
# frame. On TeaVM this deferred path fails at frame-post #1; the pane already
# received the scroll position and act(0f) synchronously, and Arc Scene runs
# layout on the next draw. Keep normal block/category selection unchanged.
scroll_layout = """                    Core.app.post(() -> {
                        blockPane.setScrollYForce(scrollPositions.get(currentCategory, 0));
                        blockPane.act(0f);
                        blockPane.layout();
                    });"""
if source.count(scroll_layout) != 1:
    raise SystemExit("Pinned PlacementFragment scroll-layout callback anchor changed")
source = source.replace(scroll_layout, "", 1)

for old, new in replacements.items():
    occurrences = source.count(old)
    expected = 2 if old == 'ui.content.show(displayBlock);' else 1
    if occurrences != expected:
        raise SystemExit(f"Pinned vanilla PlacementFragment anchor count changed: {old}: {occurrences} != {expected}")
    source = source.replace(old, new)
path.write_text(source, encoding="utf-8")
print("Preserved stock PlacementFragment with null-safe unmounted desktop dialogs")

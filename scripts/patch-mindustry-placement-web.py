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
# Keep the vanilla actor and UI behavior, but label failures in the four
# eager placement bootstrap points. TeaVM does not always emit Java stacks
# in Chrome, so preserving a meaningful cause label is critical for CI.
probe_replacements = {
    '                    blockTable.act(0f);':
    '                    try{ blockTable.act(0f); }catch(Throwable error){ throw new IllegalStateException("stock-placement: category-act", error); }',
    '                        rebuildCommand.run();\n                    }).grow();':
    '                        try{ rebuildCommand.run(); }catch(Throwable error){ throw new IllegalStateException("stock-placement: command-ui", error); }\n                    }).grow();',
    '                            control.input.buildPlacementUI(t);':
    '                            try{ control.input.buildPlacementUI(t); }catch(Throwable error){ throw new IllegalStateException("stock-placement: placement-buttons", error); }',
    '                rebuildCategory.run();\n                frame.update(() -> {':
    '                try{ rebuildCategory.run(); }catch(Throwable error){ throw new IllegalStateException("stock-placement: initial-category", error); }\n                frame.update(() -> {',
}
for old, new in probe_replacements.items():
    if source.count(old) != 1:
        raise SystemExit(f"Pinned vanilla placement probe anchor changed: {old!r}")
    source = source.replace(old, new, 1)

for old, new in replacements.items():
    occurrences = source.count(old)
    expected = 2 if old == 'ui.content.show(displayBlock);' else 1
    if occurrences != expected:
        raise SystemExit(f"Pinned vanilla PlacementFragment anchor count changed: {old}: {occurrences} != {expected}")
    source = source.replace(old, new)
path.write_text(source, encoding="utf-8")
print("Preserved stock PlacementFragment with null-safe unmounted desktop dialogs")

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

# Record the last *eager* scene-building milestone before an exception.
# TeaVM may omit getStackTrace(), making this cheap phase label essential.
method_begin = '    public void build(Group parent){'
method_end = '        });' + chr(10) + '    }' + chr(10) + chr(10) + '    @Nullable String getUnplaceableReason(Block block){'
if source.count(method_begin) != 1 or source.count(method_end) != 1:
    raise SystemExit("Pinned vanilla native placement build method boundary changed")
source = source.replace(method_begin,
    method_begin + chr(10) + '        String[] initPhase = {"parent-fill"};' + chr(10) + '        try{', 1)
# Insertion takes place after the new method entry so the phase array is scoped.
for anchor, phase in (
    ('            full.table(frame -> {', 'frame-layout'),
    ('                frame.table(Tex.buttonEdge2, top -> {', 'info-panel'),
    ('                frame.image().color(Pal.gray)', 'main-stack'),
    ('                    commandTable.table(u -> {', 'command-controls'),
    ('                    blockCatTable.table(Tex.pane2, blocksSelect -> {', 'block-controls'),
    ('                    blockCatTable.table(categories -> {', 'category-controls'),
    ('                mainStack.add(blockCatTable);', 'initial-rebuild'),
):
    if source.count(anchor) != 1:
        raise SystemExit(f"Pinned native placement phase anchor changed: {anchor!r}")
    indent = anchor[:len(anchor) - len(anchor.lstrip())]
    source = source.replace(anchor, indent + 'initPhase[0] = "' + phase + '";' + chr(10) + anchor, 1)
source = source.replace(method_end,
    '        });' + chr(10) + '        }catch(Throwable error){' + chr(10)
    + '            throw new IllegalStateException("stock-placement: build-graph/" + initPhase[0], error);' + chr(10)
    + '        }' + chr(10) + '    }' + chr(10) + chr(10)
    + '    @Nullable String getUnplaceableReason(Block block){', 1)

# Narrow the category-builder failure to exact vanilla operations. This is
# diagnostic-only and intentionally leaves UI/gameplay behavior unchanged.
category_phase_markers = (
    ('                        categories.bottom();', 'category/start'),
    ('                        categories.defaults().size(50f);', 'category/defaults'),
    ('                        ButtonGroup<ImageButton> group = new ButtonGroup<>();', 'category/button-group'),
    ('                        for(Category cat : Category.all){', 'category/unlocked-scan'),
    ('                        boolean needsAssign = categoryEmpty[currentCategory.ordinal()];', 'category/assign-check'),
    ('                        for(Category cat : getCategories()){', 'category/category-iteration'),
    ('                            if(categoryEmpty[cat.ordinal()]){', 'category/check-visible'),
    ('                            categories.button(ui.getIcon(cat.name()), Styles.clearTogglei, () -> {', 'category/build-button'),
    ('                    }).fillY().bottom().touchable(Touchable.enabled);', 'category/end'),
)
for anchor, phase in category_phase_markers:
    expected = 2 if anchor == '                    }).fillY().bottom().touchable(Touchable.enabled);' else 1
    if source.count(anchor) != expected:
        raise SystemExit(f"Pinned vanilla category phase anchor changed: {anchor!r}, {source.count(anchor)} != {expected}")
    if expected == 2:
        # Instrument after the first block controls table, before category table ends.
        # The second matching table close belongs to the category builder itself.
        pos = source.rfind(anchor)
        source = source[:pos] + '                        initPhase[0] = "' + phase + '";' + chr(10) + source[pos:]
    else:
        indent = anchor[:len(anchor) - len(anchor.lstrip())]
        source = source.replace(anchor, indent + 'initPhase[0] = "' + phase + '";' + chr(10) + anchor, 1)

# Preserve the stock category filtering/sorting semantics, while exposing the
# precise predicate/entry that TeaVM reports as a plain NullPointerException.
# The normal lambda/selectFrom implementation hides its originating block in
# browser logs, so use an equivalent explicit iteration with localized errors.
category_filter_old = (
    '    Seq<Block> getUnlockedByCategory(Category cat){' + chr(10)
    + '        return returnArray2.selectFrom(content.blocks(), block -> block.category == cat && block.isVisible() && unlocked(block)).sort((b1, b2) -> Boolean.compare(!b1.isPlaceable(), !b2.isPlaceable()));' + chr(10)
    + '    }'
)
category_filter_new = """    Seq<Block> getUnlockedByCategory(Category cat){
        String category = cat == null ? "null" : cat.name();
        if(returnArray2 == null) throw new IllegalStateException("stock-placement: category-list missing " + category);
        if(content == null || content.blocks() == null) throw new IllegalStateException("stock-placement: block content missing " + category);
        Seq<Block> matches = returnArray2;
        matches.clear();
        for(Block block : content.blocks()){
            String blockName = block == null ? "null" : block.name;
            String step = "category";
            try{
                if(block == null || block.category != cat) continue;
                step = "visible";
                if(!block.isVisible()) continue;
                step = "unlocked";
                if(!unlocked(block)) continue;
                step = "add";
                matches.add(block);
            }catch(Throwable error){
                throw new IllegalStateException("stock-placement: filter/" + category + "/" + blockName + "/" + step, error);
            }
        }
        if(state != null && state.isMenu()) return matches;
        try{
            return matches.sort((b1, b2) -> Boolean.compare(!b1.isPlaceable(), !b2.isPlaceable()));
        }catch(Throwable error){
            throw new IllegalStateException("stock-placement: category-sort/" + category, error);
        }
    }"""
if source.count(category_filter_old) != 1:
    raise SystemExit("Pinned native placement category filter anchor changed")
source = source.replace(category_filter_old, category_filter_new, 1)

for old, new in replacements.items():
    occurrences = source.count(old)
    expected = 2 if old == 'ui.content.show(displayBlock);' else 1
    if occurrences != expected:
        raise SystemExit(f"Pinned vanilla PlacementFragment anchor count changed: {old}: {occurrences} != {expected}")
    source = source.replace(old, new)
path.write_text(source, encoding="utf-8")
print("Preserved stock PlacementFragment with null-safe unmounted desktop dialogs")

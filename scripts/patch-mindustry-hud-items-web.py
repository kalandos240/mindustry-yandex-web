#!/usr/bin/env python3
"""Keep stock core item rows inside the available Scene width, including resize."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / 'work/Mindustry/core/src/mindustry/ui/CoreItemsDisplay.java'
source = path.read_text()
replacements = {
    '    private CoreBuild core;': '''    private CoreBuild core;
    private int webColumns;

    private int webColumnCount(){
        if(arc.Core.scene == null) return 4;
        float available = arc.Core.scene.getWidth() - Scl.scl(20f);
        // The compact HUD shares its row with the minimap. Use the actual
        // ScrollPane-assigned widget width, which can be less than the Scene.
        boolean compact = arc.Core.scene.getWidth() < 600f || arc.Core.scene.getHeight() < 480f;
        if(compact && getWidth() > 1f) available = Math.min(available, getWidth() - Scl.scl(8f));
        return Math.max(1, Math.min(4, (int)(available / Scl.scl(84f))));
    }''',
    '    void rebuild(){': '    void rebuild(){\n        webColumns = webColumnCount();',
    '            core = Vars.player.team().core();': '''            core = Vars.player.team().core();
            if(webColumns != webColumnCount()){
                rebuild();
                return;
            }''',
    '                image(item.uiIcon).size(iconSmall)': '                image(item.uiIcon).name("web-core-item-" + item.name).size(iconSmall)',
    '                label(() -> core == null ? "0" : UI.formatAmount(core.items.get(item))).padRight(3)': '                label(() -> core == null ? "0" : UI.formatAmount(core.items.get(item))).name("web-core-count-" + item.name).padRight(3)',
    '                if(++i % 4 == 0){': '                if(++i % webColumns == 0){',
}
for old, new in replacements.items():
    if source.count(old) != 1:
        raise SystemExit('Pinned CoreItemsDisplay anchor changed: ' + old)
    source = source.replace(old, new, 1)
path.write_text(source)
print('Stock CoreItemsDisplay rows adapt to actual Scene width and resize')

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
        return Math.max(1, Math.min(4, (int)((arc.Core.scene.getWidth() - Scl.scl(20f)) / Scl.scl(84f))));
    }''',
    '    void rebuild(){': '    void rebuild(){\n        webColumns = webColumnCount();',
    '            core = Vars.player.team().core();': '''            core = Vars.player.team().core();
            if(webColumns != webColumnCount()){
                rebuild();
                return;
            }''',
    '                if(++i % 4 == 0){': '                if(++i % webColumns == 0){',
}
for old, new in replacements.items():
    if source.count(old) != 1:
        raise SystemExit('Pinned CoreItemsDisplay anchor changed: ' + old)
    source = source.replace(old, new, 1)
path.write_text(source)
print('Stock CoreItemsDisplay rows adapt to actual Scene width and resize')

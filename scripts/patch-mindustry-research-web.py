#!/usr/bin/env python3
"""Name original research actors for native pointer acceptance; keep stock behavior."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
path = root / 'work/Mindustry/core/src/mindustry/ui/dialogs/ResearchDialog.java'
source = path.read_text()
changes = {
    '}).visible(() -> showTechSelect =': '}).name("web-research-choose-tree").visible(() -> showTechSelect =',
    ').marginLeft(12f).checked(node == lastNode).row();': ').marginLeft(12f).name("web-research-root-" + node.content.name).checked(node == lastNode).row();',
    '                button.resizeImage(32f);': '                button.name = "web-research-node-" + node.node.content.name;\n                button.resizeImage(32f);',
    ').disabled(i -> !canSpend(node)).growX().height(44f).colspan(3);': ').disabled(i -> !canSpend(node)).growX().height(44f).colspan(3).name("web-research-invest-" + node.content.name);',
}
for old, new in changes.items():
    if source.count(old) != 1:
        raise SystemExit('Pinned ResearchDialog anchor changed: ' + old)
    source = source.replace(old, new, 1)
path.write_text(source)
print('Original ResearchDialog actors named for genuine native input checks')

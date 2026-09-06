#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "maps" / "planet" / "SerpuloPlanetGenerator.java"

if not path.is_file():
    raise SystemExit(f"Missing pinned Serpulo planet generator: {path}")

text = path.read_text(encoding="utf-8")
replacements = [
    (
        '''    @Override\n    public void onSectorCaptured(Sector sector){\n        sector.planet.reloadMeshAsync();\n    }''',
        '''    @Override\n    public void onSectorCaptured(Sector sector){\n        // Web: sector state is already updated; the desktop-only async planet mesh\n        // refresh requires ExecutorService and is deferred to normal rendering.\n    }''',
        "onSectorCaptured",
    ),
    (
        '''    @Override\n    public void onSectorLost(Sector sector){\n        sector.planet.reloadMeshAsync();\n    }''',
        '''    @Override\n    public void onSectorLost(Sector sector){\n        // Web: sector state is already updated; avoid the desktop executor-only mesh refresh.\n    }''',
        "onSectorLost",
    ),
]

for old, new, label in replacements:
    if text.count(old) != 1:
        raise SystemExit(f"Serpulo Web patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Applied Web-safe synchronous Serpulo sector event path")

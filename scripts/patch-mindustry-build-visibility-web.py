#!/usr/bin/env python3
"""Fix vanilla BuildVisibility's core-zone guard during browser menu boot.

Mindustry creates its TechTree/blocks before the browser's playing-world
indexer. PlacementFragment asks whether core-shard is visible during UI
construction in the main menu. The pinned upstream coreZoneOnly predicate
dereferences Vars.indexer before checking !state.isGame(), which crashes
in TeaVM. Check menu state first, then evaluate the index only in-world.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch-mindustry-build-visibility-web.py BuildVisibility.java")
path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
original = "    coreZoneOnly = new BuildVisibility(() -> Vars.indexer.isBlockPresent(Blocks.coreZone) || !Vars.state.isGame()),"
fixed = "    coreZoneOnly = new BuildVisibility(() -> Vars.state == null || !Vars.state.isGame() || (Vars.indexer != null && Vars.indexer.isBlockPresent(Blocks.coreZone))),"
if source.count(original) != 1:
    raise SystemExit("Pinned BuildVisibility.coreZoneOnly guard changed")
source = source.replace(original, fixed, 1)
path.write_text(source, encoding="utf-8")
print("Guarded core-shard visibility until world/indexer initialization")

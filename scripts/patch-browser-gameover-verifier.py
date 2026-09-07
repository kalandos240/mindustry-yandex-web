#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing browser verification script: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

old_url = '''    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze" \\
'''
new_url = '''    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryGameOverSmoke=1" \\
'''
if text.count(old_url) != 1:
    raise SystemExit("Packaged-map verifier URL anchor no longer matches")
text = text.replace(old_url, new_url, 1)

old_require = '''    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-local-map-module-order="logic-pathfinding-control-renderer-ui"' \\
'''
new_require = '''    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-gameover-ui="ready"' \\
    --require 'data-mindustry-local-map-wave-smoke="armed"' \\
    --require 'data-mindustry-local-map-wave-fired="yes"' \\
    --require 'data-mindustry-local-map-gameover-smoke="armed"' \\
    --require 'data-mindustry-local-map-gameover="ready"' \\
    --require 'data-mindustry-local-map-loop="game-over"' \\
    --require 'data-mindustry-local-map-module-order="logic-pathfinding-control-renderer-ui"' \\
'''
if text.count(old_require) != 1:
    raise SystemExit("Packaged-map verifier marker anchor no longer matches")
text = text.replace(old_require, new_require, 1)

old_greps = '''  grep -Eq 'data-mindustry-local-map-world="[1-9][0-9]*x[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$dom"
'''
new_greps = '''  grep -Eq 'data-mindustry-local-map-world="[1-9][0-9]*x[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-map-wave-fired-index="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-map-wave-enemies="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-map-gameover-wave="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-map-gameover-winner="[A-Za-z0-9_-]+"' "$dom"
'''
if text.count(old_greps) != 1:
    raise SystemExit("Packaged-map verifier numeric checks anchor no longer matches")
text = text.replace(old_greps, new_greps, 1)

old_echo = '''  echo 'Browser packaged map: maze.msav entered continuous local production play for 3+ real frames'
'''
new_echo = '''  echo 'Browser packaged map: maze.msav ran 3+ real frames, spawned a real survival wave, then entered lean local core-loss Game Over'
'''
if text.count(old_echo) != 1:
    raise SystemExit("Packaged-map verifier result message anchor no longer matches")
text = text.replace(old_echo, new_echo, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended packaged-map Chrome gate through real wave spawn and lean local Game Over")

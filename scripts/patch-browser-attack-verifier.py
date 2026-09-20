#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing browser verifier: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

function_anchor = '''run_locale(){
'''
function = '''run_attack_map(){
  local profile="/tmp/mindustry-web-profile-attack-map"
  local dom="/tmp/mindustry-web-attack-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryAttackSmoke=1" \
    --profile "$profile" \
    --port 9254 \
    --timeout 45 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-local-map-test="maze"' \
    --require 'data-mindustry-attack-smoke="armed"' \
    --require 'data-mindustry-attack-core-destroyed="yes"' \
    --require 'data-mindustry-local-map-gameover="ready"' \
    --require 'data-mindustry-local-map-gameover-winner="sharded"' \
    --require 'data-mindustry-local-map-loop="game-over"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' \
    --require 'data-mindustry-links="none"' > "$dom"

  grep -Eq 'data-mindustry-attack-core-id="[0-9]+"' "$dom"
  grep -q 'data-mindustry-attack-enemy-team="crux"' "$dom"
  echo 'Browser attack mode: real Crux core created, destroyed through local Building lifecycle, stock core-victory GameOverEvent awarded Sharded'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Attack verifier function anchor no longer matches final verifier")
text = text.replace(function_anchor, function, 1)

call_anchor = '''run_locale en
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Attack verifier call anchor no longer matches final verifier")
text = text.replace(call_anchor, '''run_attack_map
run_locale en
''', 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended Chrome gate with local attack-mode enemy-core victory")

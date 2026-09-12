#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing browser locale verifier: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

function_anchor = '''run_locale(){
'''
fog_function = '''run_fog_map(){
  local profile="/tmp/mindustry-web-profile-fog-map"
  local dom="/tmp/mindustry-web-fog-map.html"
  rm -rf "$profile"

  # Force both dynamic and static exploration fog on the large stock maze map. This
  # proves the Web single-thread FogControl rasterizers and FogRenderer survive real
  # production play, rather than only proving that FogControl can be constructed.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \\
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryFogSmoke=1" \\
    --profile "$profile" \\
    --port 9239 \\
    --timeout 35 \\
    --require 'data-mindustry-web="ready"' \\
    --require 'data-mindustry-smoke-mode="production"' \\
    --require 'data-mindustry-map-catalog="ready"' \\
    --require 'data-mindustry-local-map-test="maze"' \\
    --require 'data-mindustry-local-map-state="playing"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-local-map-module-order="logic-pathfinding-control-renderer-ui"' \\
    --require 'data-mindustry-local-fog-smoke="armed"' \\
    --require 'data-mindustry-local-fog="ready"' \\
    --require 'data-mindustry-local-fog-rules="dynamic-static"' \\
    --require 'data-mindustry-local-fog-core-visible="yes"' \\
    --require 'data-mindustry-local-fog-core-discovered="yes"' \\
    --require 'data-mindustry-local-fog-far-hidden="yes"' \\
    --require 'data-mindustry-local-fog-far-undiscovered="yes"' \\
    --require 'data-mindustry-network="local-only"' \\
    --require 'data-mindustry-network-mode="singleplayer-only"' \\
    --require 'data-mindustry-links="none"' > "$dom"

  grep -Eq 'data-mindustry-local-fog-world="[1-9][0-9]*x[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-fog-core-tile="[0-9]+,[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-local-fog-far-tile="[0-9]+,[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$dom"

  if grep -Eq 'data-mindustry-world-load-smoke=|data-mindustry-playing-frame=|data-mindustry-playing-state=' "$dom"; then
    echo 'Fog production gate unexpectedly executed deterministic CI gameplay smoke.' >&2
    grep -o '<html[^>]*>' "$dom" >&2 || true
    exit 1
  fi

  echo 'Browser fog: maze dynamic + static exploration fog updated synchronously; core visible/discovered, far corner hidden/undiscovered, renderer + 3+ production frames PASS'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Fog verifier function anchor no longer matches post-legacy locale gate")
text = text.replace(function_anchor, fog_function, 1)

call_anchor = '''run_production_menu
run_production_map
run_legacy_domain_map
run_locale en
'''
call_replacement = '''run_production_menu
run_production_map
run_legacy_domain_map
run_fog_map
run_locale en
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Fog verifier call anchor no longer matches post-legacy production ordering")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended browser gate with real dynamic/static fog visibility and renderer verification")

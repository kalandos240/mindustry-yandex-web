#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing browser locale verifier: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

function_anchor = '''run_locale(){
'''
legacy_function = '''run_legacy_domain_map(){
  local profile="/tmp/mindustry-web-profile-legacy-domain"
  local dom="/tmp/mindustry-web-legacy-domain.html"
  rm -rf "$profile"

  # domain.msav in the pinned v159.7 asset set is legacy Save7. Unlike catalog
  # metadata parsing, this gate forces the complete map through World.loadMap and
  # then executes the normal browser-local production loop for several frames.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \\
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=domain" \\
    --profile "$profile" \\
    --port 9238 \\
    --timeout 35 \\
    --require 'data-mindustry-web="ready"' \\
    --require 'data-mindustry-smoke-mode="production"' \\
    --require 'data-mindustry-map-catalog="ready"' \\
    --require 'data-mindustry-map-count="18"' \\
    --require 'data-mindustry-map-source="pinned-builtin-local-only"' \\
    --require 'data-mindustry-local-map-ui="ready"' \\
    --require 'data-mindustry-local-map-test="domain"' \\
    --require 'data-mindustry-local-map-state="playing"' \\
    --require 'data-mindustry-local-map-slug="domain"' \\
    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-local-map-module-order="logic-pathfinding-control-renderer-ui"' \\
    --require 'data-mindustry-network="local-only"' \\
    --require 'data-mindustry-network-mode="singleplayer-only"' \\
    --require 'data-mindustry-storage="ready"' \\
    --require 'data-mindustry-links="none"' > "$dom"

  grep -Eq 'data-mindustry-local-map-world="[1-9][0-9]*x[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$dom"

  if grep -Eq 'data-mindustry-world-load-smoke=|data-mindustry-playing-frame=|data-mindustry-playing-state=' "$dom"; then
    echo 'Legacy domain gate unexpectedly executed deterministic CI gameplay smoke.' >&2
    grep -o '<html[^>]*>' "$dom" >&2 || true
    exit 1
  fi

  echo 'Browser legacy map: domain.msav Save7 completed full World.loadMap and 3+ continuous production frames'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Legacy domain verifier function anchor no longer matches locale gate")
text = text.replace(function_anchor, legacy_function, 1)

call_anchor = '''run_production_menu
run_production_map
run_locale en
'''
call_replacement = '''run_production_menu
run_production_map
run_legacy_domain_map
run_locale en
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Legacy domain verifier call anchor no longer matches production-map ordering")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended browser gate with full legacy Save7 domain.msav production play")

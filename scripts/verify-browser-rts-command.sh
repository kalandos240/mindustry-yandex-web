#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8098
profile="/tmp/mindustry-rts-command-profile"
dom="/tmp/mindustry-rts-command.html"
server_pid=""
cleanup(){ if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/default/maze.msav"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-rts-command-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

rm -rf "$profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryRtsCommandSmoke=1" \
  --profile "$profile" \
  --port 9318 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-stock-placement-source="mindustry.ui.fragments.PlacementFragment"' \
  --require 'data-mindustry-hud-commands="stock-PlacementFragment"' \
  --require 'data-mindustry-rts-command-smoke="commanded"' \
  --require 'data-mindustry-rts-command-source="dom-shift-g-and-right-click"' \
  --require 'data-mindustry-network="local-only"' > "$dom"

grep -Eq 'data-mindustry-rts-command-unit-id="[0-9]+"' "$dom"
grep -Eq 'data-mindustry-rts-selected-count="[1-9][0-9]*"' "$dom"
grep -Eq 'data-mindustry-rts-command-ai-target="-?[0-9]+(\.[0-9]+)?,-?[0-9]+(\.[0-9]+)?"' "$dom"
echo "RTS: spawned original Dagger, activated via DOM ShiftLeft; selected via DOM KeyG -> WebInput/DesktopInput, right-click -> stock Call.commandUnits -> CommandAI target PASS"

# A second separate map session proves that G selects two real allied Daggers
# and a single stock desktop mouse command orders both. No selectedUnits,
# CommandAI or move order is injected directly from the smoke Java observer.
group_profile="/tmp/mindustry-rts-group-profile"
group_dom="/tmp/mindustry-rts-group.html"
rm -rf "$group_profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryRtsGroupSmoke=1" \
  --profile "$group_profile" \
  --port 9319 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-hud-commands="stock-PlacementFragment"' \
  --require 'data-mindustry-rts-command-smoke="commanded"' \
  --require 'data-mindustry-rts-group-smoke="commanded"' \
  --require 'data-mindustry-rts-group-source="dom-key-g-and-single-right-click"' \
  --require 'data-mindustry-rts-group-orders="2"' \
  --require 'data-mindustry-network="local-only"' > "$group_dom"

grep -Eq 'data-mindustry-rts-group-unit-ids="[0-9]+,[0-9]+"' "$group_dom"
grep -Eq 'data-mindustry-rts-group-selected-count="[2-9][0-9]*"' "$group_dom"
echo "RTS group: two original allied Daggers -> DOM Shift+G -> both selected -> one right-click -> both stock CommandAI targets PASS"


# Third independent browser game: select two genuine units with an actual
# left-button drag rectangle, then issue their shared order with right-click.
# This catches regressions missed by the keyboard's select-all shortcut.
rect_profile="/tmp/mindustry-rts-rect-profile"
rect_dom="/tmp/mindustry-rts-rect.html"
rm -rf "$rect_profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryRtsRectSmoke=1" \
  --profile "$rect_profile" \
  --port 9320 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-hud-commands="stock-PlacementFragment"' \
  --require 'data-mindustry-rts-command-smoke="commanded"' \
  --require 'data-mindustry-rts-rect-smoke="commanded"' \
  --require 'data-mindustry-rts-rect-source="dom-left-button-world-drag"' \
  --require 'data-mindustry-rts-group-orders="2"' \
  --require 'data-mindustry-network="local-only"' > "$rect_dom"

grep -Eq 'data-mindustry-rts-rect-selected-count="[2-9][0-9]*"' "$rect_dom"
grep -Eq 'data-mindustry-rts-group-unit-ids="[0-9]+,[0-9]+"' "$rect_dom"
echo "RTS rectangle: DOM Shift + real left-button drag -> stock DesktopInput selects two Daggers -> shared right-click order reaches both AIs PASS"


# Fourth independent Maze session validates original UnitStance callbacks
# in PlacementFragment. Hold Fire is unbound by default; the CI-only smoke
# temporarily sets its stock keybind to F, then uses actual DOM keydown/up.
# Both Daggers must independently show on and off in stock CommandAI.stances.
stance_profile="/tmp/mindustry-rts-stance-profile"
stance_dom="/tmp/mindustry-rts-stance.html"
rm -rf "$stance_profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryRtsStanceSmoke=1" \
  --profile "$stance_profile" \
  --port 9321 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-hud-commands="stock-PlacementFragment"' \
  --require 'data-mindustry-rts-stance-smoke="on-then-off"' \
  --require 'data-mindustry-rts-stance-source="dom-key-f-native-placementfragment-keybind"' \
  --require 'data-mindustry-rts-stance-count="2"' \
  --require 'data-mindustry-network="local-only"' > "$stance_dom"

grep -Eq 'data-mindustry-rts-stance-unit-ids="[0-9]+,[0-9]+"' "$stance_dom"
echo "RTS stance: DOM Shift+G -> stock PlacementFragment KeyF -> two stock CommandAI Hold Fire on -> KeyF again -> both off PASS"

#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing browser locale verifier: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

function_anchor = '''run_locale(){
'''
weather_function = '''run_weather_map(){
  local profile="/tmp/mindustry-web-profile-weather-map"
  local dom="/tmp/mindustry-web-weather-map.html"
  rm -rf "$profile"

  # Force one stock always-on rain WeatherEntry on the real packaged maze. PASS requires
  # the local weather scheduler to create exactly one WeatherState, its fade to affect
  # environment water/light attributes, and renderer/UI to survive 3+ production frames.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \\
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryWeatherSmoke=1" \\
    --profile "$profile" \\
    --port 9249 \\
    --timeout 40 \\
    --require 'data-mindustry-web="ready"' \\
    --require 'data-mindustry-smoke-mode="production"' \\
    --require 'data-mindustry-map-catalog="ready"' \\
    --require 'data-mindustry-local-map-test="maze"' \\
    --require 'data-mindustry-local-map-state="playing"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-local-map-module-order="logic-pathfinding-control-renderer-ui"' \\
    --require 'data-mindustry-local-weather-smoke="armed"' \\
    --require 'data-mindustry-local-weather="ready"' \\
    --require 'data-mindustry-local-weather-type="rain"' \\
    --require 'data-mindustry-local-weather-rule-count="1"' \\
    --require 'data-mindustry-local-weather-group-count="1"' \\
    --require 'data-mindustry-local-weather-active="yes"' \\
    --require 'data-mindustry-local-weather-attributes="applied"' \\
    --require 'data-mindustry-network="local-only"' \\
    --require 'data-mindustry-network-mode="singleplayer-only"' \\
    --require 'data-mindustry-links="none"' > "$dom"

  grep -Eq 'data-mindustry-local-weather-world="[1-9][0-9]*x[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-weather-frame="[2-9]|[1-9][0-9]+"' "$dom"
  grep -Eq 'data-mindustry-local-weather-water="[-+0-9.eE]+"' "$dom"
  grep -Eq 'data-mindustry-local-weather-light="[-+0-9.eE]+"' "$dom"
  grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$dom"

  if grep -Eq 'data-mindustry-world-load-smoke=|data-mindustry-playing-frame=|data-mindustry-playing-state=' "$dom"; then
    echo 'Weather production gate unexpectedly executed deterministic CI gameplay smoke.' >&2
    grep -o '<html[^>]*>' "$dom" >&2 || true
    exit 1
  fi

  echo 'Browser weather: stock rain scheduled locally without Call transport; one WeatherState + water/light attributes + renderer + 3+ production frames PASS'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Weather verifier function anchor no longer matches post-fog locale gate")
text = text.replace(function_anchor, weather_function, 1)

call_anchor = '''run_production_menu
run_production_map
run_legacy_domain_map
run_fog_map
run_locale en
'''
call_replacement = '''run_production_menu
run_production_map
run_legacy_domain_map
run_fog_map
run_weather_map
run_locale en
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Weather verifier call anchor no longer matches post-fog production ordering")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended browser gate with stock local rain weather scheduling, attributes and rendering verification")

# Weather persistence builds on the exact staged weather runtime above. Apply its
# CI-only runtime probes here so the main post-overlay chain stays deterministic.
subprocess.run([sys.executable, str(ROOT / "scripts" / "patch-browser-weather-persistence.py")], check=True)

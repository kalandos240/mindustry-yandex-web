#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing browser verifier: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

function_anchor = '''run_locale(){
'''
function = '''run_team_ai_map(){
  local profile="/tmp/mindustry-web-profile-team-ai-map"
  local dom="/tmp/mindustry-web-team-ai-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryTeamAiSmoke=1" \
    --profile "$profile" \
    --port 9255 \
    --timeout 45 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-local-map-test="maze"' \
    --require 'data-mindustry-team-ai-smoke="ready"' \
    --require 'data-mindustry-team-ai-source="stock-logic-team-rules"' \
    --require 'data-mindustry-team-ai-team="crux"' \
    --require 'data-mindustry-team-ai-build="ready"' \
    --require 'data-mindustry-team-ai-rts="ready"' \
    --require 'data-mindustry-team-ai-prebuild="ready"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' \
    --require 'data-mindustry-links="none"' > "$dom"

  grep -Eq 'data-mindustry-team-ai-core-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-team-ai-unit-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-team-ai-frames="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-team-ai-core-unit="[A-Za-z0-9_-]+"' "$dom"
  echo 'Browser team AI: stock BaseBuilderAI + RtsAI initialized and prebuildAi spawned the real Crux core unit'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Team-AI verifier function anchor no longer matches final verifier")
text = text.replace(function_anchor, function, 1)

call_anchor = '''run_production_menu
run_production_map
run_attack_map
'''
call_replacement = '''run_production_menu
run_production_map
run_attack_map
run_team_ai_map
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Team-AI verifier call anchor no longer matches post-attack ordering")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended Chrome gate with stock build/RTS/prebuild team AI")

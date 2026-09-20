#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing browser verifier: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

function_anchor = '''run_locale(){
'''
attack_function = '''# Attack-mode gate intentionally uses its own browser profile so it cannot inherit
# Survival save/localStorage state from the preceding production-map checks.
run_attack_mode(){
  local profile="/tmp/mindustry-web-profile-attack"
  local dom="/tmp/mindustry-web-attack.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryAttackSmoke=1" \
    --profile "$profile" \
    --port 9254 \
    --timeout 45 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-local-attack-ui="ready"' \
    --require 'data-mindustry-local-map-mode="attack"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-attack-win-smoke="armed"' \
    --require 'data-mindustry-local-attack-gameover="won"' \
    --require 'data-mindustry-local-attack-smoke="complete"' \
    --require 'data-mindustry-local-map-gameover="ready"' \
    --require 'data-mindustry-local-map-loop="game-over"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' \
    --require 'data-mindustry-links="none"' > "$dom"

  grep -Eq 'data-mindustry-local-attack-map="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-local-attack-enemy-cores="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-attack-removed-cores="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-attack-default-team="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-local-attack-winner="[A-Za-z0-9_-]+"' "$dom"

  default_team="$(grep -o 'data-mindustry-local-attack-default-team="[^"]*"' "$dom" | head -1 | cut -d'"' -f2)"
  winner="$(grep -o 'data-mindustry-local-attack-winner="[^"]*"' "$dom" | head -1 | cut -d'"' -f2)"
  if [ -z "$default_team" ] || [ "$winner" != "$default_team" ]; then
    echo "Attack-mode winner mismatch: default=$default_team winner=$winner" >&2
    grep -o '<html[^>]*>' "$dom" >&2 || true
    exit 1
  fi

  echo "Browser Attack mode: first valid multi-team built-in map -> stock local attack rules -> enemy cores removed by CI gate -> local winner resolution -> Game Over PASS"
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Attack verifier function anchor changed")
text = text.replace(function_anchor, attack_function, 1)

call_anchor = '''run_enemy_path

cleanup_locale
'''
call_replacement = '''run_attack_mode
run_enemy_path

cleanup_locale
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Attack verifier call anchor changed")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended browser gate with local Attack-mode start and winner resolution")

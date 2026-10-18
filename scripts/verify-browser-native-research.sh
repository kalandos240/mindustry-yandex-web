#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -m http.server 8139 --bind 127.0.0.1 --directory "$ROOT/web-runtime/build/web" >/tmp/native-research-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for target in ${MINDUSTRY_RESEARCH_TARGETS:-desktop-menu mobile-menu desktop-narrow-menu desktop-campaign mobile-campaign}; do
  args=()
  if [[ "$target" = mobile-* ]]; then args+=(--emulate-mobile); fi
  query='lang=ru&mindustryNativeResearchSmoke=1'
  if [[ "$target" = desktop-narrow-* ]]; then query+='&nativeResearchNarrow=1'; fi
  if [[ "$target" = *-campaign ]]; then query+='&mindustryCampaignSmoke=groundZero&mindustryCampaignSaveSmoke=1'; fi
  python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:8139/index.html?$query" \
    --profile "/tmp/native-research-$target" --port 9673 --timeout 200 --cdp-input-bridge \
    --screenshot "$ROOT/work/menu-ui/native-research-$target.png" \
    --require 'data-mindustry-web="ready"' \
    --after-ready-eval "$(cat "$ROOT/scripts/browser-native-research-stress.js")" \
    --after-ready-require 'data-mindustry-native-research-test="passed"' > "/tmp/native-research-$target.html"
  echo "$target: original ResearchDialog native input, roots, spend and resize PASS"
done

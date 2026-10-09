#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

python3 -m py_compile "$ROOT_DIR/scripts/audit-browser-har.py"
cat >"$tmp/trusted.har" <<'JSON'
{"log":{"entries":[
 {"request":{"url":"https://app-623063.games.s3.yandex.net/623063/assets/sprites.png"},"response":{"status":200}},
 {"request":{"url":"https://games-sdk.yandex.ru/sdk.js"},"response":{"status":200}},
 {"request":{"url":"https://yastatic.net/sdk.css"},"response":{"status":200}}
]}}
JSON
python3 "$ROOT_DIR/scripts/audit-browser-har.py" "$tmp/trusted.har" >"$tmp/trusted.json"
grep -Fq '"unexpected_hosts": {}' "$tmp/trusted.json"
grep -Fq '"game_origin_requests": 1' "$tmp/trusted.json"

cat >"$tmp/foreign.har" <<'JSON'
{"log":{"entries":[
 {"request":{"url":"https://not-yandex.ru/spy.js?token=SECRET"},"response":{"status":200}},
 {"request":{"url":"https://app-623063.games.s3.yandex.net/623063/mindustry.js"},"response":{"status":404}}
]}}
JSON
if python3 "$ROOT_DIR/scripts/audit-browser-har.py" "$tmp/foreign.har" >"$tmp/foreign.json" 2>"$tmp/error"; then
 echo "HAR audit missed untrusted host or broken game asset" >&2; exit 1
fi
grep -Fq '"not-yandex.ru": 1' "$tmp/foreign.json"
if grep -Fq 'SECRET' "$tmp/foreign.json"; then
 echo "HAR report leaked URL query details" >&2; exit 1
fi
echo "HAR network guard: trusted Yandex app and SDK only; flag foreign hosts and broken game assets PASS"

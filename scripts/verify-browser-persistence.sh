#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-persistence-profile"
SEED_HTML="$WEB_DIR/persistence-seed.html"
SEED_DOM="/tmp/mindustry-persistence-seed-dom.html"
GAME_DOM="/tmp/mindustry-persistence-game-dom.html"
PORT=8083

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/browser-storage.js" ]
[ ! -e "$SEED_HTML" ]

cleanup(){
  rm -f "$SEED_HTML"
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cat > "$SEED_HTML" <<'HTML'
<!doctype html>
<html data-persistence-seed="booting">
<body>
<script src="browser-storage.js"></script>
<script>
(async () => {
    await globalThis.__mindustryStorage.init();
    // Includes 0xFF so the cross-reload test also proves Java signed-byte fidelity.
    globalThis.__mindustryStorage.put('ci/persist-probe.bin', new Int8Array([0, 1, 2, 127, -1, 77, 83, 65]));
    await globalThis.__mindustryStorage.flush();
    document.documentElement.setAttribute('data-persistence-seed', 'ready');
})().catch(error => {
    document.documentElement.setAttribute('data-persistence-seed', 'error');
    document.body.textContent = String(error);
});
</script>
</body>
</html>
HTML

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-persistence-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/persistence-seed.html" \
  --profile "$PROFILE" \
  --port 9228 \
  --timeout 15 \
  --require 'data-persistence-seed="ready"' > "$SEED_DOM"

# Start a completely new Chrome process with the same profile and origin. The game
# must hydrate IndexedDB before TeaVM main(), then BrowserFi's Java byte[] probe must
# see the exact persisted bytes (including -1/0xFF) and mark recovery.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en" \
  --profile "$PROFILE" \
  --port 9229 \
  --timeout 35 \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-file-persistence="recovered"' \
  --require 'data-mindustry-ui-sync="ready"' \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-network="local-only"' > "$GAME_DOM"

echo 'Browser persistence smoke: IndexedDB binary write -> browser restart -> TeaVM BrowserFi byte[] recovery PASS'

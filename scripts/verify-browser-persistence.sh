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
    globalThis.__mindustryStorage.put('ci/persist-probe.bin', new Uint8Array([0, 1, 2, 127, 255, 77, 83, 65]));
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

# Keep Chrome alive through the real IndexedDB transaction instead of relying on
# --dump-dom's load-event snapshot.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/persistence-seed.html" \
  --profile "$PROFILE" \
  --port 9227 \
  --timeout 15 \
  --require 'data-persistence-seed="ready"' > "$SEED_DOM"

# New browser process, same profile + origin: IndexedDB must hydrate before TeaVM
# main(), then BrowserFi reads the binary probe into Java and marks recovery.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en" \
  --profile "$PROFILE" \
  --port 9228 \
  --timeout 35 \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-file-persistence="recovered"' \
  --require 'data-mindustry-web="ready"' > "$GAME_DOM"

echo 'Browser persistence smoke: IndexedDB write -> reload -> Java byte[] recovery PASS'

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${WORK_DIR:-$ROOT_DIR/work}"
ARC_DIR="$WORK_DIR/Arc"
MINDUSTRY_DIR="$WORK_DIR/Mindustry"
SOURCE_DIR="$ROOT_DIR/port/arc-web"
ARC_CORE_WEB_SOURCE_DIR="$ROOT_DIR/port/arc-core-web/src"
MINDUSTRY_CORE_WEB_SOURCE_DIR="$ROOT_DIR/port/mindustry-core-web/src"
TARGET_DIR="$ARC_DIR/backends/backend-web"

if [[ ! -d "$ARC_DIR/.git" || ! -d "$MINDUSTRY_DIR/.git" ]]; then
  echo "Run scripts/bootstrap.sh first." >&2
  exit 1
fi

rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"
cp -R "$SOURCE_DIR/." "$TARGET_DIR/"

if ! grep -Fq 'include ":backends:backend-web"' "$ARC_DIR/settings.gradle"; then
  printf '\ninclude ":backends:backend-web"\n' >> "$ARC_DIR/settings.gradle"
fi

# TeaVM's JavaScript class library does not implement Arc's desktop executor setup.
python3 - "$ARC_DIR/arc-core/src/arc/Core.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old = '    public static ExecutorService executor = Threads.executor("Main Executor", OS.cores);'
new = '    public static ExecutorService executor; // Web: initialized by a browser-compatible scheduler when needed.'
if old not in text:
    raise SystemExit('Arc Core.executor initializer no longer matches pinned upstream; update the Web patch explicitly.')
path.write_text(text.replace(old, new))
PY

# BrowserSettings persists synchronously through localStorage and never needs the
# desktop backup executor.
python3 - "$ARC_DIR/arc-core/src/arc/Settings.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old = '    protected ExecutorService executor = Threads.executor("Settings Backup", 1);'
new = '    protected ExecutorService executor; // Web: BrowserSettings persists synchronously without JVM threads.'
if old not in text:
    raise SystemExit('Arc Settings.executor initializer no longer matches pinned upstream; update the Web patch explicitly.')
path.write_text(text.replace(old, new))
PY

# Desktop Arc Sound lazy-loads with ExecutorService and calls JNI SoLoud. Keep all
# sound call sites safe/no-op until the dedicated browser audio backend is wired;
# this prevents unit/entity loading from pulling desktop threading/JNI into TeaVM.
python3 "$ROOT_DIR/scripts/patch-arc-audio-web.py"
# TeaVM performs reachability before runtime constructor arguments can prune every
# disabled-audio branch. Replace the JNI boundary itself with inert Java stubs so
# Control/SoundControl/Music may remain reachable without any native SoLoud method.
python3 "$ROOT_DIR/scripts/patch-arc-soloud-web.py"
python3 "$ROOT_DIR/scripts/patch-arc-task-queue-web.py"
python3 "$ROOT_DIR/scripts/patch-browser-gl-buffer-diagnostics.py"

# Arc's desktop unsafe buffers allocate/free native memory through JNI. TeaVM owns
# JavaScript memory itself, so direct buffers can use the class-library allocator
# and explicit native free becomes a no-op. Keep the rest of Buffers untouched
# until TeaVM proves another native helper reachable.
python3 - "$ARC_DIR/arc-core/src/arc/util/Buffers.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
replacements = {
    '    private static native void freeMemory(ByteBuffer buffer); /*':
        '    private static void freeMemory(ByteBuffer buffer){} /*',
    '    private static native ByteBuffer newDisposableByteBuffer(int numBytes); /*':
        '    private static ByteBuffer newDisposableByteBuffer(int numBytes){ return ByteBuffer.allocateDirect(numBytes); } /*',
}
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f'Arc Buffers Web patch no longer matches pinned upstream: {old!r}')
    text = text.replace(old, new, 1)
path.write_text(text)
PY

# Asset loading stays fully functional but runs on the browser event loop instead
# of ExecutorService/Future. Replace the task implementation and remove the desktop
# executor plumbing from AssetManager.
mkdir -p "$ARC_DIR/arc-core/src/arc/assets"
cp "$ARC_CORE_WEB_SOURCE_DIR/arc/assets/AssetLoadingTask.java" "$ARC_DIR/arc-core/src/arc/assets/AssetLoadingTask.java"
python3 - "$ARC_DIR/arc-core/src/arc/assets/AssetManager.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
replacements = {
    'import java.util.concurrent.*;\n\n': '',
    '    final ExecutorService executor;\n\n': '',
    '        executor = Threads.executor("Assets", 1);\n': '        // Web: asset loaders execute in phases on the browser event loop.\n',
    '        tasks.add(new AssetLoadingTask(this, assetDesc, loader, executor));': '        tasks.add(new AssetLoadingTask(this, assetDesc, loader));',
    '        Threads.await(executor);\n': '        // Web: no worker executor to await.\n',
}
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f'Arc AssetManager Web patch no longer matches pinned upstream: {old!r}')
    text = text.replace(old, new, 1)
path.write_text(text)
PY

# The desktop SpriteBatch uses ForkJoinPool for sorting and requests a client-side
# VertexArray. WebGL has no client-side vertex arrays, so the Web target must use
# Arc's VBO path. Sorting stays serial on the browser event loop, groups contiguous
# same-z requests and stable-sorts reusable int run indices: no ForkJoinPool, long
# key arithmetic or per-frame sort-array allocation in particle-heavy scenes.
python3 - "$ARC_DIR/arc-core/src/arc/graphics/g2d/SpriteBatch.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old_fields = '''    static ForkJoinHolder commonPool;\n    boolean multithreaded = !OS.isIos && !OS.isAndroid;\n'''
old_ctor = '''        if(multithreaded){\n            try{\n                commonPool = new ForkJoinHolder();\n            }catch(Throwable t){\n                multithreaded = false;\n            }\n        }\n'''
old_mesh = '            mesh = new Mesh(true, false, size * 4, size * 6,'
old_sort = '''    protected void sortRequests(){\n        if(multithreaded){\n            sortRequestsThreaded();\n        }else{\n            sortRequestsStandard();\n        }\n    }\n'''
new_sort = '''    protected void sortRequests(){\n        final int count = numRequests;\n        if(count <= 0) return;\n        webSortCalls++;\n        if(count > webMaxSortRequests) webMaxSortRequests = count;\n        if(copy.length < count) copy = new DrawRequest[count + (count >> 3) + 1];\n\n        // Preserve contiguous same-z runs, then stable-sort only those runs. Particle-heavy\n        // scenes often contain many adjacent draws at one layer, so this sorts far fewer\n        // keys than one key per request and avoids TeaVM's comparatively expensive long math.\n        int[] runs = contiguous;\n        int runCount = 0;\n        int z = requestZ[0], start = 0;\n        boolean alreadySorted = true;\n        int previousZ = z;\n        for(int i = 1; i < count; i++){\n            if(requestZ[i] != z){\n                int base = runCount * 3;\n                if(base + 3 > runs.length){\n                    runs = Arrays.copyOf(runs, Math.max(runs.length << 1, base + 3));\n                }\n                runs[base] = z;\n                runs[base + 1] = start;\n                runs[base + 2] = i - start;\n                runCount++;\n                z = requestZ[i];\n                if(z < previousZ) alreadySorted = false;\n                previousZ = z;\n                start = i;\n            }\n        }\n        int base = runCount * 3;\n        if(base + 3 > runs.length){\n            runs = Arrays.copyOf(runs, Math.max(runs.length << 1, base + 3));\n        }\n        runs[base] = z;\n        runs[base + 1] = start;\n        runs[base + 2] = count - start;\n        runCount++;\n        contiguous = runs;\n        if(runCount > webMaxSortRuns) webMaxSortRuns = runCount;\n\n        if(alreadySorted){\n            webSortedFastPaths++;\n            System.arraycopy(requests, 0, copy, 0, count);\n            return;\n        }\n\n        if(sortOrder.length < runCount){\n            int size = runCount + (runCount >> 3) + 1;\n            sortOrder = new int[size];\n            sortScratch = new int[size];\n        }\n        for(int i = 0; i < runCount; i++) sortOrder[i] = i;\n\n        int[] src = sortOrder, dst = sortScratch;\n        for(int width = 1; width < runCount; width <<= 1){\n            for(int left = 0; left < runCount; left += width << 1){\n                int mid = Math.min(left + width, runCount);\n                int right = Math.min(left + (width << 1), runCount);\n                int a = left, b = mid, out = left;\n                while(a < mid && b < right){\n                    int za = runs[src[a] * 3];\n                    int zb = runs[src[b] * 3];\n                    // <= keeps insertion order stable for equal z layers.\n                    dst[out++] = za <= zb ? src[a++] : src[b++];\n                }\n                while(a < mid) dst[out++] = src[a++];\n                while(b < right) dst[out++] = src[b++];\n            }\n            int[] swap = src; src = dst; dst = swap;\n        }\n\n        int ptr = 0;\n        for(int i = 0; i < runCount; i++){\n            int run = src[i] * 3;\n            int pos = runs[run + 1], length = runs[run + 2];\n            System.arraycopy(requests, pos, copy, ptr, length);\n            ptr += length;\n        }\n\n        // Keep whichever reusable buffer finished as the sorted source for next frame.\n        sortOrder = src;\n        sortScratch = dst;\n    }\n'''
for old, new, name in [
    (old_fields, '    public static int webSortCalls, webMaxSortRequests, webMaxSortRuns, webSortedFastPaths;\n    int[] sortOrder = new int[0], sortScratch = new int[0];\n', 'fields'),
    (old_ctor, '        // Web: serial request sorting; no ForkJoinPool is initialized.\n', 'constructor'),
    (old_mesh, '            mesh = new Mesh(false, false, size * 4, size * 6,', 'VBO mesh storage'),
    (old_sort, new_sort, 'sortRequests'),
]:
    if old not in text:
        raise SystemExit(f'Arc SpriteBatch Web patch no longer matches pinned upstream ({name}).')
    text = text.replace(old, new, 1)

# The custom Web sorter above is the only sorting implementation we need. Remove
# the desktop threaded/counting-sort region entirely so TeaVM never sees
# ForkJoinHolder/Future/RecursiveAction through SpriteBatch class metadata.
threaded_start = text.find('    protected void sortRequestsThreaded(){')
region_end = text.find('    //endregion', threaded_start)
if threaded_start < 0 or region_end < 0:
    raise SystemExit('Arc SpriteBatch threaded sort region no longer matches pinned upstream.')
text = text[:threaded_start] + (
    '    // Web: desktop threaded/counting sort implementation removed.\n\n'
) + text[region_end:]

text = text.replace('import java.util.concurrent.*;\n', '')

for forbidden in (
    'ForkJoinHolder',
    'sortRequestsThreaded',
    'CountingSort',
    'PopulateTask',
    'RecursiveAction',
    'Future<?>',
):
    if forbidden in text:
        raise SystemExit(f'Arc SpriteBatch Web patch left desktop sorter marker reachable: {forbidden}')

path.write_text(text)
PY

# Stock Renderer polls a dozen settings every frame and calls glGetError every 10
# frames. The lean Web UI has no live stock Settings dialog, so sample settings at a
# low cadence and keep GL error probing diagnostic rather than a hot-path operation.
python3 "$ROOT_DIR/scripts/patch-mindustry-renderer-web.py" \
  "$MINDUSTRY_DIR/core/src/mindustry/core/Renderer.java"

# ClientLauncher contains desktop/JVM-only startup probes. Patch only the temporary
# Web checkout: launch-marker/file logging will return with writable browser Fi,
# while Runtime.maxMemory has no JavaScript equivalent.
python3 - "$MINDUSTRY_DIR/core/src/mindustry/ClientLauncher.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
replacements = {
    '        checkLaunch();': '        // Web: launch marker is deferred until writable browser Fi persistence is installed.',
    '        loadFileLogger();': '        // Web: keep console logging; browser file logging is not available.',
    '        long ram = Runtime.getRuntime().maxMemory();': '        long ram = 0L; // Web: JVM heap size has no browser equivalent.',
}
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f'Mindustry ClientLauncher Web patch no longer matches pinned upstream: {old!r}')
    text = text.replace(old, new, 1)
path.write_text(text)
PY

# Browser builds must never instantiate ArcNetProvider: raw TCP/UDP/NIO sockets are
# impossible in browser JavaScript. WebClientLauncher supplies the permanent
# single-player WebNetProvider instead.
python3 - "$MINDUSTRY_DIR/core/src/mindustry/core/Platform.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old = '''    default NetProvider getNet(){\n        return new ArcNetProvider();\n    }\n'''
new = '''    default NetProvider getNet(){\n        throw new UnsupportedOperationException("A platform-specific NetProvider is required on Web");\n    }\n'''
if old not in text:
    raise SystemExit('Mindustry Platform.getNet Web patch no longer matches pinned upstream.')
path.write_text(text.replace(old, new, 1))
PY

# Net's ping helper is desktop-threaded. The Web provider is intentionally
# single-player, so call the provider directly and keep JVM executor/LZ4 error-type
# reachability out of the common Net facade.
python3 - "$MINDUSTRY_DIR/core/src/mindustry/net/Net.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
replacements = {
    'import net.jpountz.lz4.*;\n\n': '',
    'import java.util.concurrent.*;\n': '',
    '''    private final ExecutorService pingExecutor =\n        OS.isIos ? Threads.boundedExecutor("Ping Servers", 32) : //on IOS, 256 threads can crash, so limit the amount\n        Threads.unboundedExecutor();\n\n''': '',
    ' || e instanceof LZ4Exception': '',
    '        pingExecutor.submit(() -> provider.pingHost(address, port, valid, failed));': '        provider.pingHost(address, port, valid, failed);',
}
for old, new in replacements.items():
    if old not in text:
        raise SystemExit(f'Mindustry Net Web patch no longer matches pinned upstream: {old!r}')
    text = text.replace(old, new, 1)
path.write_text(text)
PY

# Incremental streams cannot block one browser thread waiting for another JVM thread.
mkdir -p "$MINDUSTRY_DIR/core/src/mindustry/net"
cp "$MINDUSTRY_CORE_WEB_SOURCE_DIR/mindustry/net/Streamable.java" "$MINDUSTRY_DIR/core/src/mindustry/net/Streamable.java"

# The current main branch reads stock v13 saves back through SaveIO.load(). Keep
# browser-specific load compatibility isolated from the writer overlay.
python3 "$ROOT_DIR/scripts/patch-mindustry-save-load-web.py"

# SaveSlot itself remains stock, including save metadata and current-v13 SaveIO.
# Its preview PNG is deferred through the browser event loop instead of the JVM
# main ExecutorService, preserving previews without pulling unsupported threads.
python3 "$ROOT_DIR/scripts/patch-mindustry-save-preview-web.py"

# Stock mobile/desktop input is part of the Web reachability graph now. Patch only
# the browser-incompatible lock/zoom, formation executor and anonymous config-class
# reflection paths while preserving stock gameplay semantics.
python3 "$ROOT_DIR/scripts/patch-mindustry-input-web.py" \
  "$MINDUSTRY_DIR/core/src/mindustry/input/InputHandler.java" \
  "$MINDUSTRY_DIR/core/src/mindustry/input/MobileInput.java"

# Logic's sector captured/lost events only need to update campaign state. The stock
# Serpulo visual mesh refresh uses ExecutorService, which is unavailable in TeaVM.
python3 "$ROOT_DIR/scripts/patch-mindustry-planet-events-web.py"

# The browser has no NetServer/NetClient gameplay role. Keep stock Logic server
# bookkeeping null-safe and expose the incremental single-thread Web transition paths.
python3 "$ROOT_DIR/scripts/patch-mindustry-logic-web.py"

# Re-enable the already-proven stock local survival wave timer/spawn lifecycle on top
# of the lean Web playing-core path. WaveSpawner stays local-only and avoids generated
# multiplayer Call transport.
python3 "$ROOT_DIR/scripts/patch-mindustry-waves-web.py"

# Restore the proven local survival core-loss path without desktop restart/network
# transport. BrowserLocalMapRuntime owns the lean Game Over overlay.
python3 "$ROOT_DIR/scripts/patch-mindustry-gameover-web.py"

# Fog visibility/exploration keeps its stock data, save chunk, rasterizer and
# double-buffer logic, but executes on the browser frame instead of JVM daemon threads.
python3 "$ROOT_DIR/scripts/patch-mindustry-fog-web.py"

# Pathfinder keeps its stock packed-tile, flow-field, refresh and preload algorithms.
# Only its daemon thread/sleep scheduler is replaced with an explicit browser-frame step.
python3 "$ROOT_DIR/scripts/patch-mindustry-pathfinder-web.py"

# The Web/Yandex build is intentionally single-player. Remove Join from both menu
# layouts, assign local PlayEvent players to rules.defaultTeam, and disable PvP auto-host.
python3 "$ROOT_DIR/scripts/patch-mindustry-singleplayer-web.py"

echo "Applied Arc Web overlay to $TARGET_DIR"
echo "Applied Web-only Arc settings/core/audio/buffer compatibility patches"
echo "Applied Web single-thread asset, SpriteBatch, FogControl and Pathfinder scheduler patches"
echo "Applied Web-only Mindustry startup/single-player/save/input/gameplay patches"
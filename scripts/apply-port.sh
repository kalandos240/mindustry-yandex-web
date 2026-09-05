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
# Arc's VBO path. Sorting stays serial on the browser event loop and reuses its key
# buffer so busy scenes do not allocate a new long[] every sorted frame.
python3 - "$ARC_DIR/arc-core/src/arc/graphics/g2d/SpriteBatch.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old_fields = '''    static ForkJoinHolder commonPool;\n    boolean multithreaded = !OS.isIos && !OS.isAndroid;\n'''
old_ctor = '''        if(multithreaded){\n            try{\n                commonPool = new ForkJoinHolder();\n            }catch(Throwable t){\n                multithreaded = false;\n            }\n        }\n'''
old_mesh = '            mesh = new Mesh(true, false, size * 4, size * 6,'
old_sort = '''    protected void sortRequests(){\n        if(multithreaded){\n            sortRequestsThreaded();\n        }else{\n            sortRequestsStandard();\n        }\n    }\n'''
new_sort = '''    protected void sortRequests(){\n        int count = numRequests;\n        if(copy.length < count) copy = new DrawRequest[count + (count >> 3) + 1];\n        if(sortKeys.length < count) sortKeys = new long[count + (count >> 3) + 1];\n        for(int i = 0; i < count; i++){\n            // High 32 bits preserve signed z ordering; low 32 bits preserve insertion order.\n            sortKeys[i] = ((long)requestZ[i] << 32) | (i & 0xffffffffL);\n        }\n        Arrays.sort(sortKeys, 0, count);\n        for(int i = 0; i < count; i++){\n            copy[i] = requests[(int)sortKeys[i]];\n        }\n    }\n'''
for old, new, name in [
    (old_fields, '    static ForkJoinHolder commonPool;\n    long[] sortKeys = new long[0];\n', 'fields'),
    (old_ctor, '        // Web: serial request sorting; no ForkJoinPool is initialized.\n', 'constructor'),
    (old_mesh, '            mesh = new Mesh(false, false, size * 4, size * 6,', 'VBO mesh storage'),
    (old_sort, new_sort, 'sortRequests'),
]:
    if old not in text:
        raise SystemExit(f'Arc SpriteBatch Web patch no longer matches pinned upstream ({name}).')
    text = text.replace(old, new, 1)
path.write_text(text)
PY

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
# impossible in browser JavaScript. WebClientLauncher supplies WebNetProvider.
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

# Net's ping helper is also desktop-threaded. The Web provider owns the asynchronous
# browser transport boundary, so call it directly and remove the JVM executor/LZ4
# error-type reachability from the common Net class.
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

# Stock mobile/desktop input is part of the Web reachability graph now. Patch only
# the browser-incompatible lock/zoom, formation executor and anonymous config-class
# reflection paths while preserving stock gameplay semantics.
python3 "$ROOT_DIR/scripts/patch-mindustry-input-web.py" \
  "$MINDUSTRY_DIR/core/src/mindustry/input/InputHandler.java" \
  "$MINDUSTRY_DIR/core/src/mindustry/input/MobileInput.java"

echo "Applied Arc Web overlay to $TARGET_DIR"
echo "Applied Web-only Arc settings/core/audio/buffer compatibility patches"
echo "Applied Web single-thread asset and allocation-stable SpriteBatch VBO patches"
echo "Applied Web-only Mindustry startup/network/stream/save/input patches"

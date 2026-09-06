#!/usr/bin/env python3
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]
BROWSER_GL = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserGL20.java"

if not BROWSER_GL.is_file():
    raise SystemExit(f"Missing BrowserGL20 source: {BROWSER_GL}")

# Gameplay/Renderer makes Arc VBOs, uniforms and texture uploads reachable. Apply the
# permanent Java-NIO -> JavaScript typed-array bridges before adding the temporary DOM
# diagnostic marker used to identify any remaining Buffer-bearing GL entry point.
runpy.run_path(str(ROOT / "scripts" / "patch-arc-webgl-nio.py"), run_name="__main__")
runpy.run_path(str(ROOT / "scripts" / "patch-browser-gl-texture-pixels.py"), run_name="__main__")

text = BROWSER_GL.read_text(encoding="utf-8")

# Temporary runtime diagnostic: record the last GL20 entry point whose Java signature
# carries a java.nio.Buffer. TeaVM's JSO bridge throws before Java gets a useful stack
# trace when an ordinary NIO buffer is passed where a native JS ArrayBufferView is
# required. The CI DOM dump will therefore identify the exact offending method.
pattern = re.compile(
    r'(public\s+[^\n{]+\s+(gl[A-Za-z0-9_]+)\s*\([^\n)]*(?:Buffer|ByteBuffer|FloatBuffer|IntBuffer|ShortBuffer)[^\n)]*\)\s*\{)'
)

seen = []


def instrument(match):
    whole, name = match.group(1), match.group(2)
    marker = f'markBufferBridge("{name}");'
    if marker in whole:
        return whole
    seen.append(name)
    return whole + "\n        " + marker


text = pattern.sub(instrument, text)
if not seen:
    raise SystemExit("BrowserGL20 diagnostic found no Buffer-bearing GL methods")

anchor = '    private boolean validUniform(int location){ return location >= 0 && uniforms.get(location) != null; }'
helper = '''    @JSBody(params = {"name"}, script = "document.documentElement.setAttribute('data-mindustry-last-gl-buffer', name);")
    private static native void markBufferBridge(String name);

'''
if helper.strip() not in text:
    if text.count(anchor) != 1:
        raise SystemExit("BrowserGL20 diagnostic helper anchor no longer matches current Web backend")
    text = text.replace(anchor, helper + anchor, 1)

BROWSER_GL.write_text(text, encoding="utf-8")
print("Instrumented BrowserGL20 Buffer entry points: " + ", ".join(seen))

#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing BrowserLocalMapRuntime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")
old = '''    @JSBody(script = "const p=new URLSearchParams(location.search); for(const key of p.keys()){ if(key.startsWith('mindustry') && key.toLowerCase().endsWith('smoke')) return true; } return false;")
    private static native boolean smokeTelemetryRequested();
'''
new = '''    @JSBody(script = "var query=String(location.search || ''); if(query.charAt(0)==='?') query=query.substring(1); var parts=query.split('&'); for(var i=0;i<parts.length;i++){ var raw=parts[i].split('=')[0] || ''; var key; try{ key=decodeURIComponent(raw.replace(/\\+/g,' ')); }catch(e){ key=raw; } var lower=key.toLowerCase(); if(key.indexOf('mindustry')===0 && lower.length>=5 && lower.lastIndexOf('smoke')===lower.length-5) return true; } return false;")
    private static native boolean smokeTelemetryRequested();
'''

old_count = text.count(old)
new_count = text.count(new)
if old_count == 1 and new_count == 0:
    text = text.replace(old, new, 1)
elif old_count == 0 and new_count == 1:
    pass
else:
    raise SystemExit(
        "Browser smoke JSBody compatibility patch is ambiguous: "
        f"old={old_count}, new={new_count}"
    )

# TeaVM 0.15's JSBody parser accepts classic var/for syntax but rejects ES2015
# for...of in this path while still allowing generateJavaScript to exit successfully.
# Keep the source gate explicit so silent parser diagnostics cannot recur here.
if "for(const key of p.keys())" in text or "const p=new URLSearchParams(location.search)" in text:
    raise SystemExit("Unsupported ES2015 smoke telemetry JSBody syntax remains after patch")

RUNTIME.write_text(text, encoding="utf-8")
print("Patched smoke telemetry JSBody to TeaVM-0.15-compatible syntax")

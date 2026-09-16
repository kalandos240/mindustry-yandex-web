#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserBuildPlacementSmoke.java"

if not PATH.is_file():
    raise SystemExit(f"Missing browser build-placement smoke source: {PATH}")

text = PATH.read_text(encoding="utf-8")
old = '''            if(!findTarget(unit)){
                throw new IllegalStateException("Build-placement smoke found no visible valid conveyor tile within local builder range");
            }
'''
new = '''            markBuildAudioState(
                Sounds.loopBuild != null,
                Sounds.loopBuild != null && Sounds.loopBuild.file != null,
                Sounds.loopBuild instanceof BrowserSound
            );

            if(!findTarget(unit)){
                throw new IllegalStateException("Build-placement smoke found no visible valid conveyor tile within local builder range");
            }
'''
if text.count(old) != 1:
    raise SystemExit("Build-placement audio preflight anchor no longer matches browser smoke source")
text = text.replace(old, new, 1)

marker = '''    @JSBody(params = {"x", "y", "sx", "sy"}, script = "document.documentElement.setAttribute('data-mindustry-build-placement-smoke', 'targeted'); document.documentElement.setAttribute('data-mindustry-build-placement-tile-x', String(x)); document.documentElement.setAttribute('data-mindustry-build-placement-tile-y', String(y)); document.documentElement.setAttribute('data-mindustry-build-placement-pointer-x', String(sx)); document.documentElement.setAttribute('data-mindustry-build-placement-pointer-y', String(sy));")
    private static native void markTarget(int x, int y, float sx, float sy);
'''
replacement = marker + '''
    @JSBody(params = {"present", "hasFile", "browserSound"}, script = "document.documentElement.setAttribute('data-mindustry-build-loop-sound-present', String(present)); document.documentElement.setAttribute('data-mindustry-build-loop-sound-file', String(hasFile)); document.documentElement.setAttribute('data-mindustry-build-loop-sound-browser', String(browserSound));")
    private static native void markBuildAudioState(boolean present, boolean hasFile, boolean browserSound);
'''
if text.count(marker) != 1:
    raise SystemExit("Build-placement audio marker anchor no longer matches browser smoke source")
text = text.replace(marker, replacement, 1)

PATH.write_text(text, encoding="utf-8")
print("Added zero-catch BuilderComp audio preflight to browser placement smoke")

# Keep the next diagnostic equally cheap: integer stage writes only, no Throwable
# wrappers or gameplay branching. The existing post-overlay orchestrator invokes this
# script, so chain the stage probe here without expanding the top-level patch list.
subprocess.run([sys.executable, str(ROOT / "scripts" / "patch-mindustry-builder-stage-diagnostic.py")], check=True)

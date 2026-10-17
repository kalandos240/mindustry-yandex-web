#!/usr/bin/env python3
"""Use current browser pointer coordinates for gameplay/UI hit guards."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "work/Arc/arc-core/src/arc/scene/Scene.java"
text = path.read_text(encoding="utf-8")
old = """    public @Nullable Element getHoverElement(){
        return mouseOverElement;
    }
"""
new = """    public @Nullable Element getHoverElement(){
        // DOM move/down can arrive in the same browser frame. Control polls
        // world actions before UI.act() updates mouseOverElement, so a cached
        // world hover can close the native configuration popup during its
        // own pointerdown. Query the current stage hit for Web input guards.
        // Keep act()'s cached actor untouched: it still owns enter/exit events.
        if(Core.app != null && Core.app.isWeb() && Core.input != null){
            screenToStageCoordinates(tempCoords.set(Core.input.mouseX(), Core.input.mouseY()));
            return hit(tempCoords.x, tempCoords.y, true);
        }
        return mouseOverElement;
    }
"""
if text.count(old) != 1:
    raise SystemExit("Arc Scene Web hover patch no longer matches pinned upstream")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Enabled current-pointer Web Scene hit guards; preserved native hover events")

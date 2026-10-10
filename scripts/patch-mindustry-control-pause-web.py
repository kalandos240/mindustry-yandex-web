#!/usr/bin/env python3
"""Route browser Space pause through the web sector state machine.

The desktop Control.update() directly calls GameState.set(paused), bypassing
BrowserCampaignRuntime.pause()/BrowserLocalMapRuntime.pause() and their frozen
clock tracking. Paused browser frames intentionally skip Control.update(), so
a second Space can never resume. Leave all non-pause stock controls intact.
"""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Control.java"
text = path.read_text(encoding="utf-8")
anchor = "            if(!net.client() && Core.input.keyTap(Binding.pause)"
end_anchor = "            if(state.isCampaign() && state.afterGameOver){"
if text.count(anchor) != 1 or text.count(end_anchor) != 1:
    raise SystemExit("Pinned stock pause key handler no longer matches Web integration")
start = text.index(anchor)
end = text.index(end_anchor, start)
original = text[start:end]
if "state.set(state.isPaused() ? State.playing : State.paused);" not in original:
    raise SystemExit("Stock Space pause state setter was changed upstream")
text = text[:start] + """            // Web: Space pause/resume belongs to BrowserGameplayRuntime; stock
            // direct state.set(paused) bypasses the sector pause clock and
            // wedges the single-thread browser scheduler after the key press.

""" + text[end:]
path.write_text(text, encoding="utf-8")
print("Routed stock Space pause through Web sector pause/resume state machines")

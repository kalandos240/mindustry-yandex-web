#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"
SPAWNER = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "ai" / "WaveSpawner.java"

for path in (LOGIC, SPAWNER):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry wave source: {path}")

text = LOGIC.read_text(encoding="utf-8")
method_start = text.index("    public void updateWebPlayingCore(){")
method_end = text.index("    @Override\n    public void update(){", method_start)
if method_start < 0 or method_end < 0:
    raise SystemExit("Logic Web wave guard patch: updateWebPlayingCore scope not found")
method = text[method_start:method_end]
old_guard = "state.rules.fog || state.rules.waves || state.rules.attackMode"
new_guard = "state.rules.fog || state.rules.attackMode"
if method.count(old_guard) != 1:
    raise SystemExit("Logic Web wave guard patch: guard token no longer matches staged updateWebPlayingCore")
method = method.replace(old_guard, new_guard, 1)
text = text[:method_start] + method + text[method_end:]

old_anchor = '''        // Weather is asserted absent above; retain the stock base rule attributes.
        state.envAttrs.clear();
'''
new_anchor = '''        // Stock survival-wave timer and spawn lifecycle. Permanent Web single-player is
        // authoritative, so the desktop !net.client() server gate is implicit here.
        if(state.rules.waves && state.rules.waveTimer && !state.gameOver){
            if(!isWaitingWave()){
                state.wavetime = Math.max(state.wavetime - Time.delta, 0);
            }
        }

        if(state.wavetime <= 0 && state.rules.waves){
            runWave();
        }

        // Weather is still asserted absent above; retain the stock base rule attributes.
        state.envAttrs.clear();
'''
if text.count(old_anchor) != 1:
    raise SystemExit("Logic Web wave insertion anchor no longer matches staged updateWebPlayingCore")
text = text.replace(old_anchor, new_anchor, 1)
LOGIC.write_text(text, encoding="utf-8")

spawner = SPAWNER.read_text(encoding="utf-8")
old_effect = '''        Events.fire(new UnitSpawnEvent(unit));
        Call.spawnEffect(unit.x, unit.y, unit.rotation, unit.type);
'''
new_effect = '''        Events.fire(new UnitSpawnEvent(unit));
        // Web/Yandex is permanent local single-player: invoke the same visual effect
        // directly instead of retaining the generated multiplayer Call transport.
        spawnEffect(unit.x, unit.y, unit.rotation, unit.type);
'''
if spawner.count(old_effect) != 1:
    raise SystemExit("WaveSpawner Web local spawn-effect patch no longer matches pinned upstream")
SPAWNER.write_text(spawner.replace(old_effect, new_effect, 1), encoding="utf-8")

print("Enabled stock local survival wave timer/spawning without multiplayer Call transport")

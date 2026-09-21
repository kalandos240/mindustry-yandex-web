#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"

if not LOGIC.is_file():
    raise SystemExit(f"Missing staged campaign Logic source: {LOGIC}")

text = LOGIC.read_text(encoding="utf-8")

old_guard = '''        if(state.isCampaign() || state.rules.pvp){
'''
new_guard = '''        if(state.rules.pvp){
'''
if text.count(old_guard) != 1:
    raise SystemExit("Logic Web campaign guard no longer matches team-AI playing core")
text = text.replace(old_guard, new_guard, 1)

old_tick = '''        if(state.rules.fog){
            fogControl.update();
        }

        Time.update();
'''
new_tick = '''        if(state.rules.fog){
            fogControl.update();
        }

        // Stock campaign tick order: SectorInfo tracks production/attack state first,
        // then Universe advances global campaign state before Time/GlobalVars/entities.
        if(state.isCampaign()){
            if(state.rules.sector == null){
                throw new IllegalStateException("Campaign Web tick lost its active sector");
            }
            state.rules.sector.info.update();
            universe.update();
        }

        Time.update();
'''
if text.count(old_tick) != 1:
    raise SystemExit("Logic Web campaign tick anchor no longer matches fog-enabled playing core")
text = text.replace(old_tick, new_tick, 1)

LOGIC.write_text(text, encoding="utf-8")
print("Enabled stock SectorInfo/Universe campaign tick in Web playing core")

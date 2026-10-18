#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"

if not LOGIC.is_file():
    raise SystemExit(f"Missing staged campaign Logic source: {LOGIC}")

text = LOGIC.read_text(encoding="utf-8")

# The browser MSAV is authoritative. Settings-backed SectorInfo may be stale
# after a cold tab restart and must not roll a loaded wave/rules back to defaults.
old_load = """                    SectorInfo info = state.rules.sector.info;
                    info.write();"""
new_load = """                    SectorInfo info = state.rules.sector.info;
                    info.wave = state.wave;
                    info.waves = state.rules.waves;
                    info.waveSpacing = state.rules.waveSpacing;
                    info.winWave = state.rules.winWave;
                    info.attack = state.rules.attackMode;
                    // Legacy/unreadable metadata must not erase the MSAV core.
                    // Explicitly decoded campaign metadata remains authoritative for
                    // research costs, launch spending and off-sector production.
                    if(!info.webInventoryLoaded){
                        info.items.clear();
                        if(state.rules.defaultTeam.core() != null){
                            info.items.add(state.rules.defaultTeam.items());
                        }
                        info.webInventoryLoaded = true;
                    }
                    info.write();"""
if text.count(old_load) != 1:
    raise SystemExit("Campaign saved-sector metadata authority anchor changed")
text = text.replace(old_load, new_load, 1)

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

# The lean Web playing core intentionally does not call desktop Logic.checkGameState().
# Restore only the stock campaign branch here, keeping it local-authoritative so the
# browser does not retain generated multiplayer Call transport.
old_post = '''        if(!state.gameOver){
            if(!state.rules.attackMode && state.rules.canGameOver && state.teams.playerCores().size == 0){
                state.gameOver = true;
                state.won = false;
                Events.fire(new GameOverEvent(state.rules.waveTeam));
            }else if(state.rules.attackMode){
                int countAlive = state.teams.getActive().count(t -> t.isAlive() && t.team != Team.derelict);
                if(countAlive <= 1 || (!state.rules.pvp && state.rules.defaultTeam.core() == null)){
                    TeamData left = state.teams.getActive().find(t -> t.isAlive() && t.team != Team.derelict);
                    Team winner = left == null ? Team.derelict : left.team;
                    state.gameOver = true;
                    state.won = player != null && player.team() == winner;
                    Events.fire(new GameOverEvent(winner));
                }
            }
        }

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
'''
new_post = '''        if(state.isCampaign()){
            if(state.rules.sector == null){
                throw new IllegalStateException("Campaign Web state check lost its active sector");
            }

            // Stock campaign loss semantics: losing every player core ends the run.
            if(state.teams.playerCores().size == 0 && !state.gameOver){
                state.gameOver = true;
                state.won = false;
                Events.fire(new GameOverEvent(state.rules.waveTeam));
            }

            // Match stock Logic.checkGameState(): maps with no remaining spawn source
            // stop waves, and winWave/attack victory captures the active sector.
            if(state.rules.waves && spawner.countSpawns() + state.teams.cores(state.rules.waveTeam).size <= 0){
                state.rules.waves = false;
            }

            boolean waveVictory = state.rules.waves && state.enemies == 0
                && state.rules.winWave > 0 && state.wave >= state.rules.winWave
                && !spawner.isSpawning();
            boolean attackVictory = state.rules.attackMode
                && (webCampaignAttackVictory || !state.rules.waveTeam.isAlive());

            if(waveVictory || attackVictory){
                if(state.rules.sector.preset != null
                && state.rules.sector.preset.attackAfterWaves
                && !state.rules.attackMode){
                    state.rules.attackMode = true;
                    state.rules.waves = false;
                }else{
                    // Same local body as stock Call.sectorCapture(), without RPC.
                    // The browser campaign smoke may arm a one-shot victory predicate
                    // instead of mutating live TeamData; never let it leak to a later sector.
                    webCampaignAttackVictory = false;
                    sectorCapture();
                }
            }
        }

        // Stock checkGameState() uses a campaign branch and a mutually-exclusive
        // non-campaign branch. Do not run ordinary attack/wave Game Over handling after
        // a campaign capture has already mutated attackMode/waves locally.
        if(!state.isCampaign() && !state.gameOver){
            if(!state.rules.attackMode && state.rules.canGameOver && state.teams.playerCores().size == 0){
                state.gameOver = true;
                state.won = false;
                Events.fire(new GameOverEvent(state.rules.waveTeam));
            }else if(state.rules.attackMode){
                int countAlive = state.teams.getActive().count(t -> t.isAlive() && t.team != Team.derelict);
                if(countAlive <= 1 || (!state.rules.pvp && state.rules.defaultTeam.core() == null)){
                    TeamData left = state.teams.getActive().find(t -> t.isAlive() && t.team != Team.derelict);
                    Team winner = left == null ? Team.derelict : left.team;
                    state.gameOver = true;
                    state.won = player != null && player.team() == winner;
                    Events.fire(new GameOverEvent(winner));
                }
            }
        }

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
'''
if text.count(old_post) != 1:
    raise SystemExit("Logic Web campaign state-check anchor no longer matches attack-mode playing core")
text = text.replace(old_post, new_post, 1)

# sectorCapture() is invoked locally above. The generated Call.clearObjectives() wrapper
# is only a transport hop; mutate the same authoritative rules collection directly.
old_clear = '''        Call.clearObjectives();
'''
new_clear = '''        state.rules.objectives.clear();
'''
if text.count(old_clear) != 1:
    raise SystemExit("Logic Web campaign objective-clear anchor no longer matches pinned sectorCapture")
text = text.replace(old_clear, new_clear, 1)

LOGIC.write_text(text, encoding="utf-8")
print("Enabled stock local campaign tick + victory/capture state check in Web playing core")

# Explicit validity separates legacy metadata from persisted campaign changes:
# research costs and off-sector production must still reach the loaded core.
INFO = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "game" / "SectorInfo.java"
info = INFO.read_text(encoding="utf-8")
info = info.replace("public class SectorInfo{", "public class SectorInfo{\n    public transient boolean webInventoryLoaded;", 1)
anchor = "    public void prepare(Sector sector){"
if info.count(anchor) != 1:
    raise SystemExit("SectorInfo inventory validity anchor changed")
info = info.replace(anchor, anchor + "\n        webInventoryLoaded = true;", 1)
INFO.write_text(info, encoding="utf-8")

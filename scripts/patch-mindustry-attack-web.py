#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"

if not LOGIC.is_file():
    raise SystemExit(f"Missing staged Mindustry attack-mode source: {LOGIC}")

text = LOGIC.read_text(encoding="utf-8")

old_guard = '''        if(state.isCampaign() || state.rules.attackMode || state.rules.pvp){
'''
new_guard = '''        // Local Attack mode is now part of the browser playing core. Campaign and
        // PvP still remain outside the permanent single-player Yandex target.
        if(state.isCampaign() || state.rules.pvp){
'''
if text.count(old_guard) != 1:
    raise SystemExit("Logic Web Attack-mode guard no longer matches post-weather playing core")
text = text.replace(old_guard, new_guard, 1)

old_preflight = '''        if(state.rules.canGameOver && !state.gameOver && state.teams.playerCores().size == 0){
            state.gameOver = true;
            state.won = false;
            Events.fire(new GameOverEvent(state.rules.waveTeam));
            return;
        }

        for(TeamData data : state.teams.getActive()){
'''
new_preflight = '''        if(updateWebLocalGameOver()) return;

        for(TeamData data : state.teams.getActive()){
'''
if text.count(old_preflight) != 1:
    raise SystemExit("Logic Web Attack preflight anchor no longer matches local game-over overlay")
text = text.replace(old_preflight, new_preflight, 1)

old_post = '''        // Permanent Web/Yandex survival is local-authoritative. Keep the stock
        // non-attack loss condition after the entity tick as well, so a core destroyed
        // during this frame ends the match immediately. Campaign, PvP and attack-mode
        // winner resolution remain intentionally unreachable above.
        if(state.rules.canGameOver && !state.gameOver && state.teams.playerCores().size == 0){
            state.gameOver = true;
            state.won = false;
            Events.fire(new GameOverEvent(state.rules.waveTeam));
        }

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
    }

    @Override
    public void update(){
'''
new_post = '''        // Re-evaluate after entity updates so a core destroyed during this frame ends
        // survival/Attack mode immediately, matching stock single-player semantics.
        updateWebLocalGameOver();

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
    }

    /**
     * Stock non-campaign local winner/loss resolution without server transport.
     * Attack mode ends when one core-owning team remains or the local/default core dies.
     */
    private boolean updateWebLocalGameOver(){
        if(!state.rules.canGameOver || state.gameOver) return false;

        if(state.rules.attackMode){
            int countAlive = state.teams.getActive().count(t -> t.isAlive() && t.team != Team.derelict);
            if(countAlive <= 1 || state.rules.defaultTeam.core() == null){
                TeamData left = state.teams.getActive().find(t -> t.isAlive() && t.team != Team.derelict);
                Team winner = left == null ? Team.derelict : left.team;
                state.gameOver = true;
                state.won = winner == state.rules.defaultTeam;
                Events.fire(new GameOverEvent(winner));
                return true;
            }
        }else if(state.teams.playerCores().size == 0){
            state.gameOver = true;
            state.won = false;
            Events.fire(new GameOverEvent(state.rules.waveTeam));
            return true;
        }

        return false;
    }

    @Override
    public void update(){
'''
if text.count(old_post) != 1:
    raise SystemExit("Logic Web Attack post-update game-over anchor no longer matches")
text = text.replace(old_post, new_post, 1)

LOGIC.write_text(text, encoding="utf-8")
print("Enabled stock local Attack-mode win/loss resolution without PvP/network transport")

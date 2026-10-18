#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'web-runtime/build/web/mindustry.js'
s=p.read_text()
start=s.index('mw_BrowserCampaignRuntime_saveCampaignCheckpoint =')
at=s.index('        if (mw_BrowserCampaignRuntime_current === null)',start)
fixture="""        if(new URLSearchParams(location.search).has('lostCore') && !globalThis.__lostCoreFixture){
            globalThis.__lostCoreFixture=true;
            const core=mg_Team_core(m_Vars_state.$rules2.$defaultTeam);
            mw_Tile_setBlock(core.$tile5,mc_Blocks_air);
            if($rt_suspending()) throw new Error('Lost-core seed unexpectedly suspended');
            setTimeout(()=>mc_GameState_set(m_Vars_state,mc_GameState$State_paused),0);
        }
"""
p.write_text(s[:at]+fixture+s[at:])

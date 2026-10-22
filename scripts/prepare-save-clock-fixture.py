#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'web-runtime/build/web/mindustry.js'
s=p.read_text()
start=s.index('mw_Campaign_saveCampaignCheckpoint =')
at=s.index('        if (mw_Campaign_current === null)',start)
seed="""        if(!globalThis.__clockSeedDone){
            globalThis.__clockSeedDone=true;
            m_Vars_state.$wave=3;
            m_Vars_state.$tick=Number(new URLSearchParams(location.search).get('clockFixtureTick'));
        }
"""
p.write_text(s[:at]+seed+s[at:])

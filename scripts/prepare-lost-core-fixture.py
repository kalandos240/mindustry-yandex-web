#!/usr/bin/env python3
"""Stage a lost-core checkpoint and a retained-derelict sentinel, never ship probes."""
from pathlib import Path
import sys
p=Path(__file__).resolve().parents[1]/'web-runtime/build/web/mindustry.js'
s=p.read_text()
if '--read-probe' in sys.argv:
    marker='$rt_exports.main = $rt_export_main;'
    assert s.count(marker)==1
    probe="""globalThis.__mindustryReadLostSentinel=()=>{
        const raw=globalThis.__mindustryStorage.get('test/lost-sentinel.json');
        if(!raw)throw new Error('Lost-sector sentinel metadata missing');
        const pos=JSON.parse(new TextDecoder().decode(raw));
        const build=mc_World_build(m_Vars_world,pos.x,pos.y);
        return !!build && mg_Building_team(build)===mg_Team_derelict &&
            mw_Tile_block(mc_World_tile(m_Vars_world,pos.x,pos.y))===mc_Blocks_copperWall &&
            mg_Building_health(build)>0;
    };
"""
    p.write_text(s.replace(marker,probe+marker))
    raise SystemExit
start=s.index('mw_BrowserCampaignRuntime_saveCampaignCheckpoint =')
at=s.index('        if (mw_BrowserCampaignRuntime_current === null)',start)
fixture="""        if(new URLSearchParams(location.search).has('lostCore') && !globalThis.__lostCoreFixture){
            globalThis.__lostCoreFixture=true;
            const core=mg_Team_core(m_Vars_state.$rules2.$defaultTeam);
            if(mw_BrowserCampaignRuntime_current.$planet===mc_Planets_serpulo){
                let found=false;
                outer:for(let x=2;x<mc_World_width(m_Vars_world)-2;x++){
                    for(let y=2;y<mc_World_height(m_Vars_world)-2;y++){
                        const tile=mc_World_tile(m_Vars_world,x,y);
                        if(mw_Tile_block(tile)!==mc_Blocks_air ||
                            Math.hypot(x-mg_Building_tileX(core),y-mg_Building_tileY(core))<15 ||
                            !mw_Build_validPlace1(mc_Blocks_copperWall,mg_Team_derelict,x,y,0,false,false))continue;
                        mw_Tile_setBlock1(tile,mc_Blocks_copperWall,mg_Team_derelict);
                        globalThis.__mindustryStorage.put('test/lost-sentinel.json',new TextEncoder().encode(JSON.stringify({x,y})));
                        found=true;break outer;
                    }
                }
                if(!found)throw new Error('No valid lost-sector sentinel tile');
            }
            mw_Tile_setBlock(core.$tile5,mc_Blocks_air);
            if($rt_suspending()) throw new Error('Lost-core seed unexpectedly suspended');
            setTimeout(()=>mc_GameState_set(m_Vars_state,mc_GameState$State_paused),0);
        }
"""
p.write_text(s[:at]+fixture+s[at:])

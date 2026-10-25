#!/usr/bin/env python3
"""Install a read-only test probe in the compiled closure; restore in reverse fixture order."""
import argparse
import hashlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MARKER=b'/* Mindustry QA read-only state probe. Test builds only. */'
ANCHOR=b'$rt_exports.main = $rt_export_main;'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('runtime',nargs='?',type=Path,default=ROOT/'web-runtime/build/web/mindustry.js')
    parser.add_argument('--restore',action='store_true')
    args=parser.parse_args()
    path=args.runtime
    backup=path.with_name(path.name+'.qa-probe-original')
    data=path.read_bytes()
    if args.restore:
        if not backup.exists():
            if MARKER in data:raise SystemExit('QA probe present without original-byte backup')
            print('QA probe is not installed')
            return
        original=backup.read_bytes()
        if MARKER not in data and data!=original:raise SystemExit('Runtime changed outside QA fixture; restore nested fixtures first')
        path.write_bytes(original);backup.unlink()
        print('QA probe restored exact original SHA256',hashlib.sha256(original).hexdigest())
        return
    if MARKER in data:
        if not backup.exists():raise SystemExit('QA probe present without original-byte backup')
        print('Read-only QA probe already installed')
        return
    if backup.exists():raise SystemExit('Original-byte backup exists for a different runtime; restore first')
    if data.count(ANCHOR)!=1:raise SystemExit('TeaVM export anchor changed')
    for required in [b'mw_Campaign_active =',b'mc_GameState_isPlaying =',b'mc_GameState_isPaused =',
                     b'mc_GameState_isMenu =',b'mc_GameState_isCampaign =',b'a_Settings_get =',
                     b'as_ObjectMap_get =',b'a_Input_keyDown0 =',b'$updateId',b'$tick',b'$wave',
                     b'mc_Renderer_laserOpacity',b'mc_Renderer_unitLaserOpacity',b'mc_Renderer_bridgeOpacity']:
        if required not in data:raise SystemExit('QA probe prerequisite missing: '+required.decode())
    settings_get=data.split(b'a_Settings_get =',1)[1].split(b'\n},',1)[0]
    if b'jl_Object_monitorEnter' in settings_get:raise SystemExit('QA settings read requires browser-confined Settings implementation')
    helper=(ROOT/'scripts/browser-qa-probe.js').read_bytes()
    if helper.count(MARKER)!=1:raise SystemExit('QA helper marker changed')
    backup.write_bytes(data)
    path.write_bytes(data.replace(ANCHOR,helper+b'\n'+ANCHOR,1))
    print('Read-only QA probe installed; original SHA256',hashlib.sha256(data).hexdigest())

if __name__=='__main__':main()

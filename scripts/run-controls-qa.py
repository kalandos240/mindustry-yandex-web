#!/usr/bin/env python3
"""Sequential native control QA, with exact commands and machine-readable gates.

Requires a freshly compiled web-runtime/build/web, Chrome and Playwright Chromium.
Run without --dry-run only against an idle staged build: probes temporarily wrap JS.
This runner does not build, publish, or claim unenumerated controls are covered.
"""
import argparse, hashlib, json, os, signal, subprocess, sys, time
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SUITES=[
    ('inventory','discovery',['python3','scripts/verify-ui-control-inventory.py'],1200),
    ('menu','navigation',['bash','scripts/verify-browser-ui-button-stress.sh'],1200),
    ('pause','navigation',['bash','scripts/verify-browser-gameplay-button-stress.sh'],1500),
    ('mobile-hud','navigation',['bash','scripts/verify-browser-mobile-hud.sh'],900),
    ('planet-hud','campaign',['bash','scripts/verify-browser-hud-planet-stress.sh'],600),
    ('research','navigation',['bash','scripts/verify-browser-native-research.sh'],1500),
    ('custom-existing','navigation',['bash','scripts/verify-browser-custom-game.sh'],1200),
    ('sorter-existing','navigation',['bash','scripts/verify-browser-building-config-stress.sh'],600),
    ('hud-layout','navigation',['bash','scripts/verify-browser-hud-layout.sh'],900),
    ('narrow-menu','navigation',['bash','scripts/verify-browser-narrow-menu.sh'],600),
    ('pause-captions','navigation',['bash','scripts/verify-browser-pause-captions.sh'],600),
    ('campaign-menu-entry','campaign',['bash','scripts/verify-browser-play-campaign-stress.sh'],600),
    ('planet-dialog','campaign',['bash','scripts/verify-browser-planet-dialog.sh'],600),
    ('save-library','library',['python3','scripts/verify-native-save-library.py'],1800),
    ('schematic-library','library',['python3','scripts/verify-native-schematic-clipboard.py'],1200),
    ('secondary-desktop','secondary',['python3','scripts/verify-secondary-controls.py'],3600),
    ('secondary-mobile','secondary',['python3','scripts/verify-secondary-controls.py','--mobile'],3600),
]
SCOPE={
    'inventory':'Actual control discovery and navigation; not inner-action business effects',
    'menu':'Desktop/mobile menu callbacks and full button bounds',
    'pause':'Local/campaign pause actions and exit Cancel/Confirm',
    'mobile-hud':'Mobile toolbar callbacks, dialog navigation and compact scrolling',
    'planet-hud':'Cross-planet native HUD entry, selected Go, actual pause/frozen/advancing simulation',
    'research':'Native node purchase/resources, roots, mouse drag and resizing in all configured targets',
    'custom-existing':'Catalog preview, first-map selection, mode/rule callback and map launch',
    'sorter-existing':'Sorter native Copper choice, reopen and clear',
    'hud-layout':'HUD bounds/non-overlap, native category/conveyor selection, scaled resize input and minimap open/Escape; legacy heartbeat is not independent tick proof',
    'narrow-menu':'Russian narrow desktop full-button hit rectangles and menu callbacks',
    'pause-captions':'English pause labels, 320px stacked layout, wide resize and native callbacks',
    'campaign-menu-entry':'Home Play to native 3D Campaign, first chooser closed, Java scheduler continuity; not sector launch',
    'planet-dialog':'Serpulo/Erekir selector buttons, 3D rendering and native TechTree entry; not sector launch',
    'secondary-desktop':'Finite Database/56-rule/config-family semantic ledger, desktop',
    'secondary-mobile':'Finite Database/56-rule/config-family semantic ledger, mobile',
}

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite',choices=['controls','secondary-desktop','secondary-mobile'],default='controls')
    parser.add_argument('--groups',default='all',help='Comma separated discovery,navigation,campaign,library,secondary or all')
    parser.add_argument('--suites',help='Optional comma separated exact suite names')
    parser.add_argument('--dry-run',action='store_true')
    parser.add_argument('--fail-fast',action='store_true')
    parser.add_argument('--output',default='work/comprehensive-qa/controls-logs')
    args=parser.parse_args()
    groups=args.groups.split(',');names=args.suites.split(',') if args.suites else None
    known={s[0] for s in SUITES};known_groups={s[1] for s in SUITES}
    if names and set(names)-known:parser.error('Unknown suites: '+','.join(set(names)-known))
    if set(groups)-known_groups-{'all'}:parser.error('Unknown groups')
    suite_names={s[0] for s in SUITES if s[1] not in ['library','secondary']} if args.suite=='controls' else {args.suite}
    chosen=[s for s in SUITES if s[0] in suite_names and ('all' in groups or s[1] in groups) and (not names or s[0] in names)]
    if not chosen:parser.error('No suites selected')
    out=ROOT/args.output;out.mkdir(parents=True,exist_ok=True)
    runtime=ROOT/'web-runtime/build/web/mindustry.js'
    report={'format':'mindustry-controls-qa-v1','suite':args.suite,'scope':'Only enumerated suite assertions; inventory is discovery, not semantic coverage',
            'dryRun':args.dry_run,'commands':[],'passed':False}
    before=digest(runtime) if runtime.is_file() else None
    report['runtimeSha256Before']=before
    if not args.dry_run and before is None:parser.error('Compile web-runtime/build/web/mindustry.js before running')
    try:
        for name,group,command,timeout in chosen:
            entry={'name':name,'group':group,'command':command,'timeoutSeconds':timeout,'status':'planned','assertionScope':SCOPE.get(name,'See individual suite')}
            report['commands'].append(entry);print(name+': '+subprocess.list2cmdline(command),flush=True)
            if args.dry_run:continue
            start=time.monotonic()
            with (out/(name+'.log')).open('wb') as log:
                process=subprocess.Popen(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                try:
                    result=process.wait(timeout=timeout);entry.update(exitCode=result,status='passed' if result==0 else 'failed')
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid,signal.SIGTERM)
                    try:process.wait(timeout=10)
                    except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
                    entry.update(status='timeout',exitCode=process.returncode)
            entry['seconds']=round(time.monotonic()-start,2);entry['log']=str(out/(name+'.log'))
            print(name+': '+entry['status'],flush=True)
            # A broken restore must block subsequent tests on contaminated JS.
            if digest(runtime)!=before:
                entry['runtimeRestoreError']=True;entry['status']='failed';break
            if args.fail_fast and entry['status']!='passed':break
        report['runtimeSha256After']=digest(runtime) if runtime.is_file() else None
        report['passed']=not args.dry_run and len(report['commands'])==len(chosen) and all(s['status']=='passed' for s in report['commands']) and report['runtimeSha256After']==before
    finally:
        result_path=ROOT/('work/comprehensive-qa/'+args.suite+'.json')
        result_path.parent.mkdir(parents=True,exist_ok=True)
        result_path.write_text(json.dumps(report,indent=2)+'\n')
    return 0 if args.dry_run or report['passed'] else 1

if __name__=='__main__':sys.exit(main())

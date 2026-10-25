#!/usr/bin/env python3
"""Sequential persistence release gates; run --list for exact commands/prerequisites.

Uses the already compiled web-runtime/build/web candidate. Never builds or downloads
anything. Runtime probes are temporary; every case must restore candidate bytes.
"""
import argparse, fcntl, hashlib, importlib.util, json, os, shlex, shutil, signal, subprocess, sys, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / 'web-runtime/build/web'
CASES = [
    ('dom-text', 'controls', ['python3', 'scripts/verify-browser-text-input.py'], {}, 180),
    ('native-save-library', 'controls', ['python3', 'scripts/verify-native-save-library.py'], {}, 1200),
    ('native-schematic', 'controls', ['python3', 'scripts/verify-native-schematic-clipboard.py'], {}, 600),
    ('cloud-startup', 'storage', ['node', 'scripts/test-cloud-startup-restore.js'], {}, 120),
    ('cloud-reset-preferences', 'storage', ['node', 'scripts/test-cloud-reset-preferences.js'], {}, 120),
    # This existing aggregate already chains local cold Continue, desktop/mobile
    # exit autosave, periodic autosave, and campaign resource cold restore.
    ('storage-and-cold-restore', 'storage', ['bash', 'scripts/verify-browser-persistence.sh'], {}, 2400),
    ('resources-empty', 'resources', ['bash', 'scripts/verify-browser-campaign-save-resume.sh'], {'INVENTORY_EMPTY':'1'}, 900),
    ('resources-spent-partial', 'resources', ['bash', 'scripts/verify-browser-campaign-save-resume.sh'], {'INVENTORY_DEVICES':'desktop','INVENTORY_SPENT':'partial'}, 600),
    ('resources-spent-all', 'resources', ['bash', 'scripts/verify-browser-campaign-save-resume.sh'], {'INVENTORY_DEVICES':'mobile','INVENTORY_SPENT':'all'}, 600),
    ('save-clock', 'cold', ['bash', 'scripts/verify-browser-save-clock-go.sh'], {}, 1200),
    ('cold-native-sector-go', 'cold', ['bash', 'scripts/verify-browser-cold-sector-go.sh'], {}, 1500),
    ('cold-lost-core-go', 'cold', ['bash', 'scripts/verify-browser-cold-sector-go.sh'], {'MINDUSTRY_LOST_CORE':'1'}, 1500),
    ('fog-cold-continue', 'cold', ['bash', 'scripts/verify-browser-fog-persistence.sh'], {}, 900),
    ('weather-cold-continue', 'cold', ['bash', 'scripts/verify-browser-weather-persistence.sh'], {}, 900),
]
PREREQUISITES = [
    'Run from a checkout with an already built candidate in web-runtime/build/web.',
    'python3 + Python playwright package; node; bash; curl; google-chrome on PATH.',
    'Candidate assets include assets/maps/default/maze.msav, browser-storage.js and browser-audio.js.',
    'No other browser verification may mutate this stage or use fixed test localhost ports/profiles concurrently.',
    'Chrome must support headless SwiftShader; tests own temporary localhost HTTP servers and browser profiles.',
    'Run serially; native tests install QA/UI probes and restore them in strict reverse order.',
]

def digest(data): return hashlib.sha256(data).hexdigest()
def spec(case):
    name, group, command, env, timeout = case
    return dict(id=name, group=group, command=command, environment=env, timeoutSeconds=timeout,
                display=' '.join([*(f'{k}={shlex.quote(v)}' for k,v in env.items()), shlex.join(command)]))
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--list', action='store_true', help='Print machine-readable manifest without executing tests')
    parser.add_argument('--suite', choices=['persistence','libraries'], default='persistence')
    parser.add_argument('--group', action='append', choices=['controls','storage','resources','cold'])
    parser.add_argument('--case', action='append', choices=[c[0] for c in CASES])
    parser.add_argument('--output', type=Path, default=ROOT/'work/comprehensive-qa')
    parser.add_argument('--keep-going', action='store_true', help='Continue independent cases after a failure; final exit still fails')
    args=parser.parse_args()
    selected=[c for c in CASES if (c[1]=='controls')==(args.suite=='libraries') and (not args.group or c[1] in args.group) and (not args.case or c[0] in args.case)]
    manifest={'schema':1,'suite':args.suite,'prerequisites':PREREQUISITES,'cases':[spec(c) for c in selected],
              'failGates':['missing prerequisite','nonzero test exit','timeout','runtime bytes not restored','unexecuted requested case']}
    if args.list: print(json.dumps(manifest,indent=2)); return 0
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=True)
    report={**manifest,'status':'running','results':[],'root':str(ROOT)}
    def save():
        temp=output/(args.suite+'.json.tmp');temp.write_text(json.dumps(report,indent=2)+'\n');temp.replace(output/(args.suite+'.json'))
    missing=[exe for exe in ['python3','node','bash','curl','google-chrome'] if not shutil.which(exe)]
    if importlib.util.find_spec('playwright') is None: missing.append('Python playwright package')
    for name in ['mindustry.js','index.html','browser-storage.js','browser-audio.js','assets/maps/default/maze.msav']:
        file=STAGE/name
        if not file.is_file() or not file.stat().st_size: missing.append(str(file))
    if not selected: missing.append('at least one selected case')
    if missing:
        report.update(status='failed',prerequisiteErrors=missing);save();print(json.dumps(report));return 2
    with (ROOT/'work/persistence-qa.lock').open('a') as lock:
        try: fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:
            report.update(status='failed',prerequisiteErrors=['Another persistence runner owns the stage']);save();return 2
        runtime=STAGE/'mindustry.js';pristine=runtime.read_bytes();report['candidateSha256']=digest(pristine);save()
        for case in selected:
            item=spec(case);log=output/(case[0]+'.log');item['log']=str(log);start=time.monotonic();process=None
            print('RUN '+item['display'],flush=True)
            try:
                env=os.environ.copy()
                for key in ['INVENTORY_EMPTY','INVENTORY_SPENT','INVENTORY_DEVICES','CLOCK_DEVICES','CLOCK_SECTORS','MINDUSTRY_COLD_SECTORS','MINDUSTRY_LOST_CORE']:
                    env.pop(key,None)
                env.update(case[3]);env['PYTHONUNBUFFERED']='1'
                with log.open('w') as stream:
                    process=subprocess.Popen(case[2],cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
                    try: item['exitCode']=process.wait(timeout=case[4])
                    except subprocess.TimeoutExpired:
                        item['timedOut']=True;os.killpg(process.pid,signal.SIGTERM)
                        try: process.wait(timeout=8)
                        except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL);process.wait()
                        item['exitCode']=124
            except KeyboardInterrupt:
                if process and process.poll() is None: os.killpg(process.pid,signal.SIGKILL);process.wait()
                item.update(exitCode=130,interrupted=True)
            except Exception as error: item.update(exitCode=1,error=str(error))
            finally:
                current=runtime.read_bytes() if runtime.exists() else b''
                item['runtimeRestoredByTest']=current==pristine
                if current!=pristine:
                    runtime.write_bytes(pristine);item['runnerRestoredRuntime']=True
                item['durationSeconds']=round(time.monotonic()-start,3)
            item['status']='passed' if item['exitCode']==0 and item['runtimeRestoredByTest'] else 'failed'
            report['results'].append(item);save();print(item['status'].upper()+' '+case[0]+' '+str(log),flush=True)
            if item['status']=='failed' and (not args.keep_going or item.get('interrupted')): break
        passed=len(report['results'])==len(selected) and all(x['status']=='passed' for x in report['results'])
        report['status']='passed' if passed else 'failed';report['unexecuted']=[c[0] for c in selected[len(report['results']):]];save()
        print(str(output/(args.suite+'.json')),flush=True);return 0 if passed else 1
if __name__=='__main__': sys.exit(main())

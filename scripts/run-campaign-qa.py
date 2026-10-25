#!/usr/bin/env python3
"""Campaign QA orchestration. Reports partial coverage, retains failures and runs independent checks."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[1]
DEVICES=['desktop','mobile']
SERPULO='groundZero frozenForest crateredBattleground ruinousShores windsweptIslands biomassFacility fungalPass frontier saltFlats tarFields impact0078 stainedMountains nuclearComplex infestedCanyons desolateRift facility32m perilousHarbor extractionOutpost coastline navalFortress overgrowth mycelialBastion littoralShipyard planetaryTerminal taintedWoods atolls testingGrounds sunkenPier weatheredChannels'.split()
EREKIR='onset aegis lake intersect atlas split basin marsh peaks ravine caldera-erekir stronghold crevice siege crossroads karst origin'.split()

def bash(name):return ['bash','scripts/'+name]

def specs(suite):
    if suite.startswith('maps-'):
        planet=suite.removeprefix('maps-');presets=SERPULO if planet=='serpulo' else EREKIR
        return [dict(id=planet+'-map-matrix',command=bash('verify-browser-all-campaign-maps.sh'),
            environment={'MINDUSTRY_MATRIX_DEVICES':'desktop mobile','MINDUSTRY_MATRIX_PLANETS':planet},
            mode='preset map boot with isolated unlock bypass; actual playing tick/updateId required',
            expected_cases=[planet+'/'+p+'/'+d for d in DEVICES for p in presets],
            case_pattern=r'^Campaign map (\w+)/([\w-]+) \((desktop|mobile)\):.* PASS$',
            evidence=['work/campaign-map-'+planet+'-*.html'])]
    return [
        dict(id='native-production-launch',command=bash('verify-browser-real-campaign-launch.sh'),environment={},
            mode='native production menu/planet clicks; no gameplay smoke branch',
            expected_cases=[s+'/'+d for d in DEVICES for s in ['direct-serpulo','direct-erekir','planet-serpulo','planet-erekir']],
            case_pattern=r'^(direct-serpulo|direct-erekir|planet-serpulo|planet-erekir)/(desktop|mobile):.* PASS$',
            evidence=['/tmp/mindustry-real-campaign-*.html']),
        dict(id='fresh-firefox-graphics',command=['xvfb-run','-a',sys.executable,'scripts/diagnose-fresh-firefox-renderer.py'],
            environment={'LIBGL_ALWAYS_SOFTWARE':'1','MOZ_WEBRENDER':'1'},
            mode='native production Firefox, WebGL1/2 and persisted graphics choices; actual simulation required',
            expected_cases=['webgl'+str(gl)+'-'+p for gl in [1,2] for p in ['default','pixelate','animated','bloom']],
            case_pattern=r'^(webgl[12]-(?:default|pixelate|animated|bloom)) fresh native Go and actual live simulation PASS(?: .*)?$',
            evidence=['work/fresh-*.html','work/fresh-*.png']),
        dict(id='serpulo-progression',command=bash('verify-browser-campaign-capture.sh'),environment={},
            mode='accelerated objective/research/capture traversal, NOT an unassisted human playthrough',
            expected_cases=DEVICES,case_pattern=r'^Stock campaign progression \((desktop|mobile)\):.* PASS$',
            evidence=['/tmp/mindustry-campaign-capture-*.html']),
        dict(id='erekir-progression',command=bash('verify-browser-erekir-progression.sh'),environment={},
            mode='accelerated objective/research/capture traversal, NOT an unassisted human playthrough',
            expected_cases=DEVICES,case_pattern=r'^Erekir progression \((desktop|mobile)\):.* PASS$',
            evidence=['/tmp/mindustry-erekir-progress-*.html']),
        dict(id='native-wave-hud',command=bash('verify-browser-wave-hud-stress.sh'),environment={},
            mode='trusted native skip-wave clicks; actual wave/tick/updateId required',
            expected_cases=[p+'/'+d for d in DEVICES for p in ['frozenForest','intersect']],
            case_pattern=r'^(frozenForest|intersect)/(desktop|mobile):.* PASS$',
            evidence=['/tmp/mindustry-wave-hud-*.html'])
    ]

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def utc():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def write_report(path,report):
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_suffix(path.suffix+'.tmp')
    temp.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    temp.replace(path)

def stop_process(process):
    try:os.killpg(process.pid,signal.SIGTERM)
    except ProcessLookupError:return
    try:process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        try:os.killpg(process.pid,signal.SIGKILL)
        except ProcessLookupError:pass
        process.wait(timeout=10)

def evidence_files(patterns,root,started):
    import glob
    files=[]
    for pattern in patterns:
        full=str(root/pattern) if not Path(pattern).is_absolute() else pattern
        for name in glob.glob(full):
            path=Path(name)
            if path.is_file() and path.stat().st_mtime>=started-1:
                files.append(str(path))
    return sorted(set(files))

def run_suite(root,suite,tests,output,timeout_seconds=3600):
    runtime=root/'web-runtime/build/web/mindustry.js'
    if not runtime.is_file():raise RuntimeError('Staged game missing: '+str(runtime))
    original=runtime.read_bytes();original_hash=hashlib.sha256(original).hexdigest()
    initial_backups=set(runtime.parent.glob('*.qa-probe-original'))
    report=dict(schema=1,suite=suite,started_at=utc(),status='running',
        runtime_sha256_initial=original_hash,results=[],
        coverage_note='Map suites cover 46 pinned presets, not procedural numbered sectors. Progression checks accelerate victory conditions; they are not full unassisted playthroughs.')
    write_report(output,report)
    for test in tests:
        test_started=time.time();log=root/'work/comprehensive-qa/logs'/suite/(test['id']+'.log')
        log.parent.mkdir(parents=True,exist_ok=True)
        result=dict(id=test['id'],command=test['command'],environment=test['environment'],mode=test['mode'],
            expected_cases=test['expected_cases'],started_at=utc(),status='running',log=str(log))
        report['results'].append(result);write_report(output,report)
        print('START '+suite+'/'+test['id'],flush=True)
        process=None
        try:
            with log.open('w') as stream:
                process=subprocess.Popen(test['command'],cwd=root,env={**os.environ,**test['environment']},
                    stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
                result['exit_code']=process.wait(timeout=timeout_seconds)
            result['status']='passed' if result['exit_code']==0 else 'failed'
        except subprocess.TimeoutExpired:
            if process is not None:stop_process(process)
            result.update(status='failed',exit_code=process.returncode if process else None,reason='timeout')
        except Exception as error:
            if process is not None and process.poll() is None:stop_process(process)
            result.update(status='failed',reason=type(error).__name__+': '+str(error))
        text=log.read_text(errors='replace') if log.exists() else ''
        cases=['/'.join(match.groups()) for match in re.finditer(test['case_pattern'],text,re.MULTILINE)]
        result['observed_cases']=cases
        result['missing_cases']=sorted(set(test['expected_cases'])-set(cases))
        result['unexpected_cases']=sorted(set(cases)-set(test['expected_cases']))
        if result['missing_cases'] or result['unexpected_cases'] or len(cases)!=len(set(cases)):
            result['status']='failed'
            result.setdefault('reason','missing, duplicate or unexpected scenario evidence')
        result['runtime_restored']=runtime.is_file() and digest(runtime)==original_hash
        backups=set(runtime.parent.glob('*.qa-probe-original'))
        result['new_probe_backups']=[str(p) for p in sorted(backups-initial_backups)]
        if not result['runtime_restored'] or result['new_probe_backups']:
            result['status']='failed';result.setdefault('reason','test fixture did not restore the staged runtime')
            # This is an isolated test checkout. Keep the failure, repair only our
            # compiled test fixture, then continue independent checks on original bytes.
            runtime.write_bytes(original)
            for backup in backups-initial_backups:backup.unlink()
            result['fixture_cleanup_repaired']=True
        result['evidence']=evidence_files(test.get('evidence',[]),root,test_started)
        result['elapsed_seconds']=round(time.time()-test_started,3);result['finished_at']=utc()
        write_report(output,report)
        print('END '+suite+'/'+test['id']+' '+result['status'].upper()+' '+str(result['elapsed_seconds'])+'s',flush=True)
        if result['status']!='passed':print('\n'.join(text.splitlines()[-25:]),flush=True)
    report.update(status='passed' if all(r['status']=='passed' for r in report['results']) else 'failed',
        finished_at=utc(),runtime_sha256_final=digest(runtime))
    report['counts']={name:sum(r['status']==name for r in report['results']) for name in ['passed','failed']}
    write_report(output,report)
    return 0 if report['status']=='passed' else 1

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite',choices=['maps-serpulo','maps-erekir','campaign'],required=True)
    parser.add_argument('--output',type=Path)
    parser.add_argument('--timeout-seconds',type=int,default=3600)
    parser.add_argument('--list',action='store_true')
    args=parser.parse_args();tests=specs(args.suite)
    if args.list:
        print(json.dumps(dict(suite=args.suite,tests=tests),ensure_ascii=False,indent=2));return 0
    if args.timeout_seconds<=0:parser.error('--timeout-seconds must be positive')
    output=args.output or ROOT/'work/comprehensive-qa'/(args.suite+'.json')
    return run_suite(ROOT,args.suite,tests,output,args.timeout_seconds)

if __name__=='__main__':sys.exit(main())

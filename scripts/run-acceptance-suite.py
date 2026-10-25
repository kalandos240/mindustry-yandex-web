#!/usr/bin/env python3
"""Dispatch independent jobs against one frozen candidate, preserving failure gates."""
import argparse,json,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def run(argv):
    print('RUN',json.dumps(argv),flush=True)
    subprocess.run(argv,cwd=ROOT,check=True)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('suite');args=parser.parse_args();suite=args.suite
    out=ROOT/'work/comprehensive-qa';out.mkdir(parents=True,exist_ok=True)
    report={'suite':suite,'status':'failed','started':time.time()};probes=[]
    try:
        if suite.startswith('settings-'):
            _,browser,device=suite.split('-')
            for name in ['prepare-qa-probe.py','prepare-ui-control-probe.py']:
                run(['python3','scripts/'+name]);probes.append(name)
            run(['python3','scripts/verify-settings-functional.py','--browser',browser]+(['--mobile'] if device=='mobile' else []))
        elif suite.startswith('portal-'):
            browser=suite.split('-',1)[1]
            run(['python3','scripts/verify-portal-campaign-lifecycle.py','--browser',browser]+(['--headless'] if browser=='chromium' else []))
        elif suite=='data-reset':
            run(['node','scripts/test-cloud-reset-preferences.js'])
            run(['python3','scripts/verify-native-data-reset.py'])
        elif suite in ['campaign','maps-serpulo','maps-erekir']:
            run(['python3','scripts/run-campaign-qa.py','--suite',suite])
        elif suite in ['controls','secondary-desktop','secondary-mobile']:
            run(['python3','scripts/run-controls-qa.py','--suite',suite])
        elif suite in ['persistence','libraries']:
            run(['python3','scripts/run-persistence-qa.py','--suite',suite,'--keep-going'])
        elif suite in ['factories','platform','audio']:
            run(['python3','scripts/run-general-qa.py','--suite',suite])
        else:raise ValueError('Unknown acceptance suite: '+suite)
        report['status']='passed'
    finally:
        try:
            for name in reversed(probes):run(['python3','scripts/'+name,'--restore'])
            run(['python3','scripts/verify-qa-stage-identity.py'])
        except Exception:
            report['status']='failed';raise
        finally:
            report['seconds']=round(time.time()-report['started'],2)
            (out/(suite+'-gate.json')).write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':main()

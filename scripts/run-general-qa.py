#!/usr/bin/env python3
"""Run independent factory, platform, or audio gates against one exact staged build.

Requires Chrome and the dependencies of the named baseline checks. No build or
publication occurs. A failed check never prevents subsequent independent checks.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / 'web-runtime/build/web'


def shell(name, timeout=900, *args):
    return (name, ['bash', 'scripts/' + name + '.sh', *args], timeout)


def node(name):
    return (name, ['node', 'scripts/' + name + '.js'], 120)


SUITES = {
    'factories': [
        shell('verify-browser-building-mechanics'),
        shell('verify-browser-unit-production', 1200),
        shell('verify-browser-unit-factory-matrix', 1200),
        shell('verify-browser-unit-assembler', 1200),
        ('verify-browser-unit-assembler-advanced', ['bash', 'scripts/verify-browser-unit-assembler.sh', 'advanced'], 1200),
        shell('verify-browser-rts-command', 1200),
        shell('verify-browser-mobile-rts'),
        shell('verify-browser-block-registry'),
        shell('verify-browser-factory-freeze'),
        shell('verify-browser-palette-visibility', 1200),
    ],
    'platform': [
        node('test-yandex-device-info'),
        node('test-player-lookup-timeout'),
        node('test-cloud-checkpoint-queue'),
        node('test-asset-network-recovery'),
        node('test-campaign-map-loading'),
        shell('verify-browser-map-filter-json'),
        shell('verify-browser-network-har'),
        shell('verify-yandex-sdk', 1500),
        shell('verify-browser-device-autodetect'),
        shell('verify-browser-resize-orientation'),
        shell('verify-browser-portal-bounds', 1200),
        shell('verify-browser-frame-budget'),
        shell('verify-browser-runtime-load'),
        ('audit-web-performance', ['python3', 'scripts/audit-web-performance.py'], 120),
        # Locales additionally rechecks SDK bootstrap, intentionally excluding its
        # expensive attack-cloud roundtrip (covered by the full SDK case above).
        shell('verify-browser-locales', 1500),
        shell('verify-browser-autosave-cadence', 1200),
    ],
    'audio': [
        node('test-audio-lifecycle'),
        node('test-sfx-loading'),
        node('test-audio-interruption'),
        node('test-browser-error-report'),
        ('verify-browser-audio-lifecycle', ['python3', 'scripts/verify-browser-audio-lifecycle.py'], 900),
        ('verify-browser-sfx-recovery', ['python3', 'scripts/verify-browser-sfx-recovery.py'], 900),
    ],
}


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inventory():
    return {p.relative_to(WEB).as_posix(): digest(p) for p in WEB.rglob('*') if p.is_file()}


def restore_stage(snapshot, baseline, current):
    """Restore only test-changed files from our own private pre-test snapshot."""
    changed = sorted(k for k in set(baseline) | set(current) if baseline.get(k) != current.get(k))
    for name in changed:
        target = WEB / name
        if name not in baseline:
            target.unlink()
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(snapshot / name, target)
    return changed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--suite', required=True, choices=SUITES)
    parser.add_argument('--dry-run', action='store_true', help='List commands only; report is never marked passed')
    args = parser.parse_args()
    out = ROOT / 'work/comprehensive-qa'
    logs = out / (args.suite + '-logs')
    logs.mkdir(parents=True, exist_ok=True)
    report = {'format': 'mindustry-general-qa-v1', 'suite': args.suite,
              'scope': 'Only the assertions of the enumerated baseline checks; no claim of exhaustive gameplay correctness.',
              'dryRun': args.dry_run, 'passed': False, 'commands': []}
    report['sourceHead'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    result_path = out / (args.suite + '.json')
    env = dict(os.environ)
    env.setdefault('GITHUB_WORKSPACE', str(ROOT))
    try:
        if not args.dry_run and not (WEB / 'mindustry.js').is_file():
            raise RuntimeError('Missing compiled staged mindustry.js')
        baseline = inventory() if not args.dry_run else {}
        report['runtimeSha256Before'] = baseline.get('mindustry.js')
        report['stageFilesBefore'] = baseline
        with tempfile.TemporaryDirectory(prefix='general-qa-stage-', dir=out) as temporary:
            snapshot = Path(temporary) / 'web'
            if not args.dry_run:
                shutil.copytree(WEB, snapshot)
            for name, command, timeout in SUITES[args.suite]:
                entry = {'name': name, 'command': command, 'timeoutSeconds': timeout, 'status': 'planned'}
                report['commands'].append(entry)
                print(name + ': ' + subprocess.list2cmdline(command), flush=True)
                if args.dry_run:
                    continue
                started = time.monotonic()
                log_path = logs / (name + '.log')
                entry['log'] = str(log_path.relative_to(ROOT))
                try:
                    with log_path.open('wb') as log:
                        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
                        try:
                            code = process.wait(timeout=timeout)
                            entry.update(exitCode=code, status='passed' if code == 0 else 'failed')
                        except subprocess.TimeoutExpired:
                            os.killpg(process.pid, signal.SIGTERM)
                            try:
                                process.wait(timeout=10)
                            except subprocess.TimeoutExpired:
                                os.killpg(process.pid, signal.SIGKILL)
                                process.wait()
                            entry.update(exitCode=process.returncode, status='timeout')
                except Exception as error:
                    entry.update(status='error', error=str(error))
                entry['seconds'] = round(time.monotonic() - started, 2)
                current = inventory()
                changed = restore_stage(snapshot, baseline, current)
                if changed:
                    entry.update(status='failed', stageRestoreError=changed)
                    if inventory() != baseline:
                        raise RuntimeError('Could not restore exact staged package after ' + name)
                print(name + ': ' + entry['status'], flush=True)
                result_path.write_text(json.dumps(report, indent=2) + '\n')
        report['runtimeSha256After'] = digest(WEB / 'mindustry.js') if (WEB / 'mindustry.js').is_file() else None
        report['passed'] = not args.dry_run and len(report['commands']) == len(SUITES[args.suite]) and all(c['status'] == 'passed' for c in report['commands'])
    except Exception as error:
        report['error'] = str(error)
        print(str(error), file=sys.stderr)
    finally:
        result_path.write_text(json.dumps(report, indent=2) + '\n')
    return 0 if args.dry_run and 'error' not in report or report['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())

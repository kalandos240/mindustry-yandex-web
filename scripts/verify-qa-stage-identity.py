#!/usr/bin/env python3
"""Require all test fixtures to restore the exact packaged runtime bytes."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
stage=root/'web-runtime/build/web'
identity=json.loads((root/'work/qa-stage-identity.json').read_text())
actual={str(p.relative_to(stage)):hashlib.sha256(p.read_bytes()).hexdigest() for p in stage.rglob('*') if p.is_file()}
expected=identity['staged_files']
assert actual==expected,{'added':sorted(actual.keys()-expected.keys()),'removed':sorted(expected.keys()-actual.keys()),'changed':[p for p in actual.keys()&expected.keys() if actual[p]!=expected[p]]}
print('All staged files retain exact candidate bytes:',identity['archive_sha256'])

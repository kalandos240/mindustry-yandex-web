#!/usr/bin/env python3
"""Validate source identity and stage an immutable compiled QA candidate."""
import argparse,hashlib,json,re,shutil,subprocess,zipfile
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[1]
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('candidate',type=Path);args=parser.parse_args()
    manifest=json.loads((args.candidate/'source-manifest.json').read_text())
    archive=args.candidate/'Mindustry-Yandex-qa-candidate.zip'
    assert digest(archive)==manifest['archive_sha256'],'Candidate archive identity changed'
    current=set()
    for folder in ['port','web-runtime/src']:
        current.update(p.relative_to(ROOT).as_posix() for p in (ROOT/folder).rglob('*') if p.is_file())
    for pattern in ['patch-*.py','generate-*.py']:
        current.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'scripts').glob(pattern))
    current.update(['scripts/apply-port.sh','scripts/bootstrap.sh','upstream.lock','web-runtime/build.gradle',
        'scripts/sanitize-teavm-runtime-links.py','scripts/compact-teavm-state-vars.cjs','scripts/test-teavm-state-vars.cjs'])
    assert current==set(manifest['files']),'Production source file set differs from compiled candidate'
    # The build overlays deliberately rewrite nine tracked Java inputs in place.
    # The artifact records those *generated* hashes. Verify checkout inputs against
    # the actual immutable build commit, and retain both identities in the ledger.
    source=manifest['source'];assert re.fullmatch('[a-f0-9]{40}',source)
    check=subprocess.run(['git','cat-file','-e',source+'^{commit}'],cwd=ROOT,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if check.returncode:subprocess.run(['git','fetch','--depth=1','origin',source],cwd=ROOT,check=True)
    generated={
        'BrowserApplication.java','BrowserBuildPlacementSmoke.java','BrowserCampaignResearch.java',
        'BrowserGL20.java','BrowserGameplayRuntime.java','BrowserLocalMapRuntime.java',
        'BrowserUiRuntime.java','Campaign.java','WebClientLauncher.java'}
    input_hashes={};overlays=[]
    for name,expected in manifest['files'].items():
        p=ROOT/name
        original=subprocess.check_output(['git','show',source+':'+name],cwd=ROOT)
        input_hash=hashlib.sha256(original).hexdigest();input_hashes[name]=input_hash
        assert p.is_file() and digest(p)==input_hash,'Production input differs from compiled commit: '+name
        if input_hash!=expected:
            assert name.startswith('web-runtime/src/main/java/mindustry/web/') and p.name in generated,'Unexpected build-generated source change: '+name
            overlays.append(name)
    manifest['input_source_files']=input_hashes;manifest['generated_overlays']=overlays
    stage=ROOT/'web-runtime/build/web'
    if stage.exists():shutil.rmtree(stage)
    stage.mkdir(parents=True)
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None,'Archive CRC failed'
        assert len(z.namelist())==len(set(z.namelist())),'Duplicate archive paths'
        for name in z.namelist():
            p=PurePosixPath(name)
            assert not p.is_absolute() and '..' not in p.parts and '\\' not in name,name
            assert not any(x in name for x in ['qa-probe','ui-probe','.map','.qa-']),name
        assert sum(x.file_size for x in z.infolist())<=100_000_000
        assert z.getinfo('mindustry.js').file_size<=41_200_000
        z.extractall(stage)
    manifest['staged_files']={str(p.relative_to(stage)):digest(p) for p in stage.rglob('*') if p.is_file()}
    (ROOT/'work/qa-stage-identity.json').write_text(json.dumps(manifest,indent=2))
    print('Exact candidate staged:',manifest['archive_sha256'],'source:',manifest['source'],flush=True)

if __name__=='__main__':main()

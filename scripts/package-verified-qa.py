#!/usr/bin/env python3
"""Publish only the exact frozen archive after every required browser job passes."""
import argparse,hashlib,json,shutil,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SUITES=['controls','secondary-desktop','secondary-mobile','campaign','maps-serpulo','maps-erekir','factories','platform','audio','persistence','libraries','settings-chromium-desktop','settings-chromium-mobile','settings-firefox-desktop','settings-firefox-mobile','portal-chromium','portal-firefox']
SUITES.append('data-reset')
def main():
    parser=argparse.ArgumentParser();parser.add_argument('results',type=Path);args=parser.parse_args()
    reports={}
    for suite in SUITES:
        files=list(args.results.rglob(suite+'-gate.json'))
        assert len(files)==1,(suite,'Missing or duplicate final gate',files)
        data=json.loads(files[0].read_text());assert data['suite']==suite and data['status']=='passed',data
        reports[suite]=data
    candidate=ROOT/'work/qa-candidate';manifest=json.loads((candidate/'source-manifest.json').read_text())
    archive=candidate/'Mindustry-Yandex-qa-candidate.zip'
    sha=hashlib.sha256(archive.read_bytes()).hexdigest();assert sha==manifest['archive_sha256']
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        count=len(z.namelist());size=sum(x.file_size for x in z.infolist())
    out=ROOT/'work/verified-qa';out.mkdir(parents=True,exist_ok=True)
    target=out/'Mindustry-Yandex-tested.zip';shutil.copyfile(archive,target)
    (out/'Mindustry-Yandex-tested.zip.sha256').write_text(sha+'  '+target.name+'\n')
    limitations=[]
    for f in args.results.rglob('*.json'):
        try:data=json.loads(f.read_text())
        except (ValueError,OSError):continue
        if isinstance(data,dict) and data.get('semanticLimitations'):limitations.append({'file':f.name,'settings':data['semanticLimitations']})
    result={'source':manifest['source'],'archive_sha256':sha,'files':count,'unpacked_bytes':size,'passed_suites':reports,'settings_context_limitations':limitations,
        'scope':['46 authored preset campaign maps on desktop and mobile','Native UI controls, settings, storage, factories and portal lifecycle as enumerated in individual suite reports'],
        'limits':['Procedural numbered sectors remain unsupported.','Campaign progression tests accelerate objectives and waves; they are not complete normal-player playthroughs.','Controlled portal SDK protocol is not the live Yandex SDK or every graphics driver.','Passing tests cannot establish absence of every possible bug.']}
    (out/'qa-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    text='Проверенная сборка Mindustry Web\n\nИсходники: '+manifest['source']+'\nSHA256: '+sha+'\nФайлов: '+str(count)+'\nРазмер после распаковки: '+str(size)+' байт\n\nВсе '+str(len(SUITES))+' обязательных групп прошли. Подробности — в qa-results.json и журналах CI.\n\nГраницы проверки: 46 авторских карт; процедурные номерные сектора не поддерживаются. Проверки прохождения ускоряют волны и цели и не заменяют полное обычное прохождение. Некоторые настройки проверены по сохранению значения без воспроизведения всех игровых условий. SDK проверен контролируемым стендом портала. Отсутствие всех возможных ошибок не гарантируется.\n'
    (out/'QA-REPORT-RU.txt').write_text(text)
    print('Verified exact archive:',sha)
if __name__=='__main__':main()

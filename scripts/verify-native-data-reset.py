#!/usr/bin/env python3
"""Native confirmed Data actions, cancellation, durable marker and cold reload.
Isolated disposable browser profiles; production reset callbacks are never invoked
through JavaScript. Storage fixtures are explicit and do not emulate world loading.
"""
import argparse,asyncio,json,os,shutil,subprocess,tempfile,threading
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'
OUT=ROOT/'work/native-data-reset'

async def main(target_filter=None):
    OUT.mkdir(parents=True,exist_ok=True)
    runtime=STAGE/'mindustry.js';original=runtime.read_bytes();installed=[];server=None
    reports=[]
    try:
        for script in ['prepare-qa-probe.py','prepare-ui-control-probe.py']:
            subprocess.run(['python3',str(ROOT/'scripts'/script)],check=True);installed.append(script)
        server=ThreadingHTTPServer(('127.0.0.1',8172),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
        threading.Thread(target=server.serve_forever,daemon=True).start()
        async with async_playwright() as pw:
            browser=await pw.chromium.launch(executable_path=os.environ.get('MINDUSTRY_CHROME_EXECUTABLE') or shutil.which('google-chrome'),headless=True,args=['--no-sandbox','--disable-dev-shm-usage','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
            try:
                # Actual localized native labels are discovered, not hardcoded
                # coordinates or compiled callback names.
                async def run(target=None,planet=False):
                    context=await browser.new_context(viewport={'width':1280,'height':900})
                    fixture='MWS1|'+''.join(t+str(len(k))+':'+k+str(len(v))+':'+v for t,k,v in [('s','qa-device-pref','retain-me'),('b','titanium-unlocked','1'),('i','req-router-copper','99'),('i','req-duct-beryllium','77')])
                    await context.add_init_script("if(!localStorage.getItem('qa-data-fixture-once')){localStorage.setItem('qa-data-fixture-once','1');localStorage.setItem('mindustry.web.settings.v1',"+json.dumps(fixture)+");}")
                    page=await context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
                    async def inventory():return await page.evaluate('()=>__mindustryUiControls({includeOffscreen:true,includeDisabled:true})')
                    async def choose(predicate,label):
                        for attempt in range(35):
                            value=await inventory();items=[c for c in value['controls'] if c['onScreen'] and c['enabled'] and predicate(c)]
                            if items:
                                c=items[0];await page.mouse.click(c['x'],c['y']);await page.wait_for_timeout(400);return c
                            panes=[c for c in value['controls'] if c['kind']=='scrollpane' and c['onScreen'] and c.get('maxY',0)>0]
                            if panes:
                                p=max(panes,key=lambda c:c['maxY']);await page.mouse.move(p['x'],p['y']);await page.mouse.wheel(0,230 if attempt<22 else -230)
                            await page.wait_for_timeout(150)
                        raise AssertionError(label+' absent: '+json.dumps(value)[:16000])
                    async def text(label):return await choose(lambda c:c['kind']=='button' and c['label'].strip().lower()==label.lower(),label)
                    async def ready(query=""):
                        await page.goto('http://127.0.0.1:8172/index.html?lang=en'+query)
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=150000)
                    async def open_data():
                        await choose(lambda c:c['name']==('web-pause-settings' if target=='__game_guard__' else 'web-main-settings'),'settings')
                        await text('Game Data')
                        if planet:await choose(lambda c:c['kind']=='button' and 'planet' in c['label'].lower() and 'clear' in c['label'].lower(),'planet data')
                    try:
                        if target=='__game_guard__':
                            await ready('&mindustryMapSmoke=maze&mindustryLocalMode=sandbox')
                            await page.wait_for_function('__mindustryQaSnapshot().playing',timeout=120000)
                            await page.keyboard.press('Escape')
                            await page.wait_for_function('__mindustryQaSnapshot().paused')
                        elif target is not None:
                            # Explicit fixtures create fully valid MSAVs and live
                            # inventory through production save code; all reset
                            # actions and confirmations below remain native clicks.
                            for sector in ['onset','groundZero']:
                                await ready('&mindustryCampaignSmoke='+sector+'&mindustryCampaignSaveSmoke=1')
                                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-campaign-save-flush')==='ready'",timeout=150000)
                            await ready()
                        else:await ready()
                        await open_data()
                        value=await inventory()
                        labels=sorted({c['label'].strip() for c in value['controls'] if c['kind']=='button' and c['label'].strip().lower().startswith('clear') and (planet or 'planet' not in c['label'].lower())})
                        if target is None:return labels
                        if target=='__game_guard__':
                            destructive=[c for c in value['controls'] if c['kind']=='button' and c['label'].strip().lower().startswith('clear') and (planet or 'planet' not in c['label'].lower())]
                            assert len(destructive)==(2 if planet else 4),destructive
                            assert all(not c['enabled'] for c in destructive),destructive
                            before=await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")')
                            assert 'main menu' in json.dumps(await page.evaluate('__mindustryUiText()')).lower()
                            await page.keyboard.press('Escape');await page.wait_for_timeout(350)
                            await page.keyboard.press('Escape');await page.wait_for_timeout(350)
                            await page.keyboard.press('Escape');await page.wait_for_timeout(350)
                            assert await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")')==before
                            assert not errors,errors
                            reports.append({'liveWorld':True,'planet':planet,'disabledActions':len(destructive),'markerUnchanged':True})
                            print('Live-world Data destructive controls disabled PASS',flush=True)
                            return
                        assert target in labels,(target,labels)
                        before=await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")')
                        baseline=await page.evaluate("()=>({paths:__mindustryStorage.paths().filter(p=>p.endsWith('.msav')),pref:__mindustryQaSetting('qa-device-pref'),research:__mindustryQaSetting('req-router-copper'),otherResearch:__mindustryQaSetting('req-duct-beryllium'),unlocked:__mindustryQaSetting('titanium-unlocked')})")
                        assert any('sector-serpulo-' in p for p in baseline['paths']),baseline
                        assert any('sector-erekir-' in p for p in baseline['paths']),baseline
                        assert baseline['pref']=='retain-me',baseline
                        assert baseline['research']==99 and baseline['otherResearch']==77 and baseline['unlocked'] is True,baseline
                        await text(target)
                        async def require_confirmation(stage):
                            # Distinguish a real application failure from an
                            # invalid test click before a destructive action.
                            for _ in range(30):
                                shown=(await inventory()).get('modal') or {}
                                if shown.get('title')=='Confirm':return
                                await page.wait_for_timeout(100)
                            seen=await inventory()
                            await page.screenshot(path=str(OUT/('missing-confirm-'+str(stage)+'.png')))
                            (OUT/('missing-confirm-'+str(stage)+'.json')).write_text(json.dumps(seen,indent=2))
                            raise AssertionError(stage+': native confirmation not opened; top modal='+json.dumps(seen.get('modal')))
                        await require_confirmation('initial')
                        # Cancel must not create a reset marker or mutate durable data.
                        await choose(lambda c:c['kind']=='button' and c['label'].strip().lower() in ['cancel','back'],'cancel reset')
                        assert await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")')==before
                        assert await page.evaluate("()=>__mindustryStorage.paths().filter(p=>p.endsWith('.msav'))")==baseline['paths'],'Cancel deleted saves'
                        assert await page.evaluate("__mindustryQaSetting('req-router-copper')")==baseline['research'],'Cancel changed research'
                        await text(target)
                        await require_confirmation('repeat')
                        await text('OK')
                        await page.wait_for_function('()=>Number(__mindustryQaSetting("web-campaign-reset-at"))>0')
                        marker=await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")')
                        assert marker!=before
                        after=await page.evaluate("()=>({paths:__mindustryStorage.paths().filter(p=>p.endsWith('.msav')),pref:__mindustryQaSetting('qa-device-pref'),research:__mindustryQaSetting('req-router-copper'),otherResearch:__mindustryQaSetting('req-duct-beryllium'),unlocked:__mindustryQaSetting('titanium-unlocked')})")
                        action=target.lower()
                        if 'research' in action:
                            assert after['research'] in [None,0,'0'],after
                            assert after['unlocked'] in [None,False,0],after
                            assert after['paths']==baseline['paths'],'Research reset removed saves'
                            if planet:assert after['otherResearch']==baseline['otherResearch'],'Serpulo reset changed Erekir research'
                        elif 'campaign' in action:
                            assert not any('sector-serpulo-' in p for p in after['paths']),after
                            if planet:assert [p for p in after['paths'] if 'sector-erekir-' in p]==[p for p in baseline['paths'] if 'sector-erekir-' in p],after
                            else:assert not any('sector-erekir-' in p for p in after['paths']),after
                        elif 'saves' in action:
                            assert not after['paths'],after
                        else:
                            assert not after['paths'] and after['pref'] is None,after
                        if 'research' in action or 'campaign' in action or 'saves' in action:assert after['pref']==baseline['pref'],'Selective reset changed device preference'
                        # ForceSave must persist the actual typed MWS1 marker;
                        # durable flush is awaited before cold bootstrap assertions.
                        await page.evaluate('()=>__mindustryStorage.flush()')
                        stored=await page.evaluate("()=>new TextDecoder().decode(__mindustryStorage.get('settings/mindustry.web.settings.v1.mws1'))")
                        assert 'web-campaign-reset-at' in stored,stored[:100]
                        await ready()
                        assert str(await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")'))==str(marker)
                        assert await page.get_attribute('html','data-yandex-cloud-restore')=='skipped-local-reset'
                        assert (await page.evaluate('__mindustryQaSnapshot()'))['menu']
                        assert not errors,errors
                        reports.append({'label':target,'planet':planet,'marker':str(marker),'cancelUnchanged':True,'durableReload':True,'before':baseline,'after':after})
                        print(target+': native cancel/confirm, marker, forceSave and reload PASS',flush=True)
                    finally:
                        if target and errors:(OUT/'errors.json').write_text(json.dumps(errors,indent=2))
                        await context.close()
                if target_filter:
                    await run(target_filter)
                    return
                ordinary=await run();planet=await run(planet=True)
                assert len(ordinary)==4,ordinary
                assert len(planet)==2,planet
                for label in ordinary:await run(label)
                for label in planet:await run(label,True)
                await run("__game_guard__")
                await run("__game_guard__",True)
            finally:await browser.close()
    finally:
        (OUT/'report.json').write_text(json.dumps(reports,indent=2))
        if server:server.shutdown()
        for script in reversed(installed):subprocess.run(['python3',str(ROOT/'scripts'/script),'--restore'],check=True)
        assert runtime.read_bytes()==original,'QA helpers changed production runtime bytes'

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--target',help='Limit to one destructive action for isolated native diagnostics')
    args=parser.parse_args()
    asyncio.run(main(args.target))

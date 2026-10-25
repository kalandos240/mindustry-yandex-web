"""Real file picker -> imported slot -> supported world -> cold Continue.

Also checks that a valid-header/corrupt-world import cannot replace the good
canonical MSAV, and that native slot rename/delete are not inert on mobile.
"""
import asyncio,base64,hashlib,json,os,shutil,struct,subprocess,tempfile,threading,zlib
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'
OUT=ROOT/'work/save-library-qa'
CANONICAL='saves/web-local-survival.msav'

def corrupt_world(source):
    raw=bytearray(zlib.decompress(source));assert raw[:4]==b'MSAV' and struct.unpack_from('>i',raw,4)[0]==13
    metadata=struct.unpack_from('>i',raw,8)[0];patches_at=12+metadata
    patches=struct.unpack_from('>i',raw,patches_at)[0];content_at=patches_at+4+patches
    assert 0<struct.unpack_from('>i',raw,content_at)[0]<len(raw)-content_at
    # Valid checksum and byte-identical metadata, invalid content-region length.
    # getMeta/isSaveValid succeeds; full SaveIO.load must reject this world.
    struct.pack_into('>i',raw,content_at,0)
    return zlib.compress(raw)

async def main():
    OUT.mkdir(parents=True,exist_ok=True)
    runtime=STAGE/'mindustry.js';pristine=runtime.read_bytes();installed=[]
    server=None
    try:
        for script in ['prepare-qa-probe.py','prepare-ui-control-probe.py']:
            subprocess.run(['python3',str(ROOT/'scripts'/script)],check=True);installed.append(script)
        server=ThreadingHTTPServer(('127.0.0.1',8169),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
        threading.Thread(target=server.serve_forever,daemon=True).start()
        async with async_playwright() as pw:
            for mobile in [False,True]:
                mode='mobile' if mobile else 'desktop'
                with tempfile.TemporaryDirectory(dir=OUT) as temp:
                    profile=Path(temp)/'profile'
                    async def launch():
                        return await pw.chromium.launch_persistent_context(str(profile),
                            executable_path=os.environ.get('MINDUSTRY_CHROME_EXECUTABLE') or shutil.which('google-chrome'),
                            headless=True,viewport={'width':390 if mobile else 1280,'height':844 if mobile else 800},
                            is_mobile=mobile,has_touch=mobile,
                            args=['--no-sandbox','--disable-dev-shm-usage','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
                    context=await launch();page=await context.new_page();errors=[]
                    await context.add_init_script("if(!localStorage.getItem('mindustry.web.settings.v1'))localStorage.setItem('mindustry.web.settings.v1','MWS1|i12:saveinterval2:10');")
                    page.on('pageerror',lambda e:errors.append(str(e)))
                    async def ready(url):
                        await page.goto('http://127.0.0.1:8169/index.html'+url)
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=150000)
                    async def controls():
                        return await page.evaluate('()=>__mindustryUiControls({includeOffscreen:true,includeDisabled:true})')
                    async def choose(predicate,label,activate=True):
                        for attempt in range(30):
                            value=await controls()
                            choices=[x for x in value['controls'] if predicate(x) and x['enabled'] and x['onScreen']]
                            if choices:
                                target=choices[0]
                                if not activate:return target
                                if mobile:await page.touchscreen.tap(target['x'],target['y'])
                                else:await page.mouse.click(target['x'],target['y'])
                                await page.wait_for_timeout(450);return target
                            panes=[x for x in value['controls'] if x['kind']=='scrollpane' and x['onScreen'] and x.get('maxY',0)>0]
                            if panes:
                                p=max(panes,key=lambda x:x['maxY']);await page.mouse.move(p['x'],p['y']);await page.mouse.wheel(0,220 if attempt<20 else -220)
                            await page.wait_for_timeout(200)
                        raise AssertionError(label+' missing: '+json.dumps(value,ensure_ascii=False)[:16000])
                    async def named(name):return await choose(lambda x:x['name']==name,name)
                    async def label(text):return await choose(lambda x:x['kind']=='button' and text.lower() in x['label'].lower(),text)
                    async def close_dialogs():
                        for _ in range(7):
                            if not (await controls())['modal']:return
                            await page.keyboard.press('Escape');await page.wait_for_timeout(500)
                        raise AssertionError('Native dialogs remained open')
                    async def open_saves():
                        await close_dialogs()
                        game=await page.evaluate('__mindustryQaSnapshot()')
                        if not game['menu']:
                            if not game['paused']:
                                # A modal hide/Scene rebuild can retain the last
                                # touch/key focus while its exit action finishes.
                                # Exercise the actual Escape key after the
                                # original actor transition and check real state.
                                for attempt in range(3):
                                    await page.wait_for_timeout(450)
                                    await page.keyboard.press('Escape')
                                    await page.wait_for_timeout(600)
                                    if (await page.evaluate('__mindustryQaSnapshot()'))['paused']:break
                                    print('NATIVE_ESCAPE_DIAGNOSTIC '+json.dumps({
                                        'attempt':attempt+1,
                                        'game':await page.evaluate('__mindustryQaSnapshot()'),
                                        'modal':(await controls())['modal'],
                                        'focus':await page.evaluate('document.activeElement?.tagName'),
                                        'inputMode':await page.get_attribute('html','data-mindustry-input-mode')
                                    }),flush=True)
                                else:raise AssertionError('Live imported world did not pause after trusted Escape')
                            await named('web-pause-saves')
                        else:
                            value=await controls()
                            if not mobile and not any(x['name']=='web-main-saves' and x['onScreen'] for x in value['controls']):await named('web-main-play')
                            await named('web-main-saves')
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-vanilla-modal-visible').includes('saves')")
                    async def upload(name,data):
                        async with page.expect_file_chooser() as selected:
                            await label('Import Save')
                        picker=await selected.value
                        await picker.set_files({'name':name,'mimeType':'application/octet-stream','buffer':data})
                        await page.wait_for_timeout(1200)
                    async def bytes_at(path=CANONICAL):
                        return bytes(await page.evaluate('p=>Array.from(Uint8Array.from(__mindustryStorage.get(p)||[]))',path))
                    try:
                        # Only seed creation uses a deterministic map fixture. Every
                        # subsequent import, slot action and Continue is native input.
                        await ready('?lang=en&mindustryMapSmoke=maze&mindustryLocalMode=sandbox&mindustrySaveSmoke=1')
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-local-save-flush')==='ready'",timeout=120000)
                        seed=await bytes_at();assert len(seed)>128
                        actual_rules=await page.evaluate("""()=>({
                            native:__mindustryQaRead('state','rules.infiniteResources'),
                            mode:document.documentElement.getAttribute('data-mindustry-local-map-mode'),
                            infinite:document.documentElement.getAttribute('data-mindustry-local-map-infinite-resources'),
                            waveTimer:document.documentElement.getAttribute('data-mindustry-local-map-wave-timer'),
                            gameState:document.documentElement.getAttribute('data-mindustry-local-map-state')
                        })""")
                        print('SANDBOX_RULES_DIAGNOSTIC '+json.dumps(actual_rules),flush=True)
                        assert bool(actual_rules['native']) is True,actual_rules
                        await context.close();context=await launch();page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
                        await ready('?lang=en')
                        await open_saves()
                        before=await bytes_at();assert before==seed
                        async with page.expect_file_chooser() as item:
                            await label('Import Save')
                        empty=await item.value;await empty.set_files([])
                        await page.wait_for_timeout(300)
                        assert await page.locator('input[type=file]').count()==0
                        assert await page.get_attribute('html','data-mindustry-browser-file-state')=='cancelled'
                        assert await bytes_at()==seed
                        corrupt=corrupt_world(seed)
                        await upload('QA corrupt world.msav',corrupt)
                        await choose(lambda x:x['kind']=='button' and 'QA corrupt world' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'valid-header corrupt-world card')
                        await page.wait_for_timeout(800)
                        assert (await page.evaluate('__mindustryQaSnapshot()'))['menu']
                        assert await bytes_at()==seed,'A failed full world load replaced the good canonical save'
                        assert await page.get_attribute('html','data-mindustry-web')=='ready'
                        await close_dialogs();await open_saves()
                        bad=await choose(lambda x:x['kind']=='button' and 'QA corrupt world' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'corrupt source card',False)
                        await choose(lambda x:x['kind']=='button' and x['path'].startswith(bad['path']+'.') and 'trash' in x.get('icons',[]),'corrupt source delete')
                        await choose(lambda x:x['kind']=='button' and x['label'].strip()=='OK','delete corrupt confirmation')
                        assert await bytes_at()==seed
                        # Valid, real browser-file-picker import keeps the source slot.
                        filename='QA imported sandbox 日本語.msav'
                        await upload(filename,seed)
                        card=await choose(lambda x:x['kind']=='button' and 'QA imported sandbox' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'imported save card',False)
                        async with page.expect_download() as item:
                            await choose(lambda x:x['kind']=='button' and x['path'].startswith(card['path']+'.') and 'export' in x.get('icons',[]),'slot export')
                        exported=await item.value;assert Path(await exported.path()).read_bytes()==seed
                        await choose(lambda x:x['kind']=='button' and 'QA imported sandbox' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'imported save card')
                        await page.wait_for_function('__mindustryQaSnapshot().playing && !__mindustryQaSnapshot().campaign',timeout=90000)
                        state=await page.evaluate('__mindustryQaSnapshot()');tick=state['tick']
                        await page.wait_for_function('t=>__mindustryQaSnapshot().tick>t+30',arg=tick)
                        assert bool(await page.evaluate('__mindustryQaRead("state","rules.infiniteResources")')) is True
                        assert bool(await page.evaluate('__mindustryQaRead("state","rules.waveTimer")')) is False
                        assert await bytes_at()==seed,'Canonical changes only to the completely validated imported bytes'
                        sources=await page.evaluate("()=>__mindustryStorage.paths().filter(p=>/^saves\\/\\d+\\.msav$/.test(p))")
                        assert sources and await bytes_at(sources[0])==seed,'Original imported slot retained'
                        # Import while an existing world is paused, then manually
                        # save that old world. The imported archive must remain exact;
                        # importing must not change Saves.current before loading.
                        await open_saves()
                        await upload('QA paused archive.msav',seed)
                        paused_sources=await page.evaluate("()=>__mindustryStorage.paths().filter(p=>/^saves\\/\\d+\\.msav$/.test(p))")
                        paused_source=next(p for p in paused_sources if p not in sources)
                        await close_dialogs();await named('web-pause-save')
                        await page.evaluate('()=>__mindustryStorage.flush()')
                        assert await bytes_at(paused_source)==seed,'Saving old paused world overwrote newly imported archive'
                        await open_saves()
                        archive=await choose(lambda x:x['kind']=='button' and 'QA paused archive' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'paused archive card',False)
                        await choose(lambda x:x['kind']=='button' and x['path'].startswith(archive['path']+'.') and 'trash' in x.get('icons',[]),'paused archive delete')
                        await choose(lambda x:x['kind']=='button' and x['label'].strip()=='OK','paused archive delete confirmation')
                        # Stock BaseDialog confirmation fades out asynchronously.
                        # Wait for that transition to finish so the next *real*
                        # save-card click is not intercepted by the old modal.
                        await page.wait_for_timeout(850)
                        # Restore the untouched original imported fixture before the
                        # cold restart and ensure the canonical adopts its bytes.
                        await choose(lambda x:x['kind']=='button' and 'QA imported sandbox' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'original import reload')
                        await page.wait_for_function('__mindustryQaSnapshot().playing && !__mindustryQaSnapshot().paused',timeout=90000)
                        assert await bytes_at()==seed
                        await page.evaluate('()=>__mindustryStorage.flush()')
                        # Cold restart with a new browser process and original profile.
                        await context.close();context=await launch();page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
                        await ready('?lang=en')
                        await open_saves()
                        await choose(lambda x:x['kind']=='button' and 'QA imported sandbox' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'cold imported card')
                        await page.wait_for_function('__mindustryQaSnapshot().playing && !__mindustryQaSnapshot().campaign',timeout=90000)
                        assert bool(await page.evaluate('__mindustryQaRead("state","rules.infiniteResources")')) is True
                        assert bool(await page.evaluate('__mindustryQaRead("state","rules.waveTimer")')) is False
                        assert await bytes_at()==seed
                        # The Saved Games action also works from the paused world.
                        await open_saves()
                        await choose(lambda x:x['kind']=='button' and 'QA imported sandbox' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'paused-world imported card')
                        await page.wait_for_function('__mindustryQaSnapshot().playing && !__mindustryQaSnapshot().paused',timeout=90000)
                        # Source remains a manageable native library slot; rename is
                        # exercised through the real text dialog on both input modes.
                        await open_saves()
                        value=await controls()
                        card=await choose(lambda x:x['kind']=='button' and 'QA imported sandbox' in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'imported rename card',False)
                        prefix=card['path']
                        await choose(lambda x:x['kind']=='button' and x['path'].startswith(prefix+'.') and any('pencil' in i for i in x.get('icons',[])),'slot rename')
                        # Both input modes use the same native DOM Unicode field.
                        renamed='Переименовано QA 日本語'
                        await page.wait_for_selector('#mindustry-text-input')
                        field=page.locator('#mindustry-text-input').locator('input,textarea');await field.fill(renamed);await field.press('Enter')
                        await page.wait_for_timeout(700)
                        assert any(renamed in x['label'] for x in (await controls())['controls'])
                        # An invalid extension must give feedback and remove its
                        # hidden picker, without changing any MSAV bytes.
                        before=await bytes_at();await upload('invalid.txt',b'not a save')
                        assert await page.get_attribute('html','data-mindustry-browser-file-state')=='rejected'
                        assert await page.locator('input[type=file]').count()==0
                        assert await bytes_at()==before
                        await close_dialogs();await open_saves()
                        card=await choose(lambda x:x['kind']=='button' and renamed in x['label'] and x['cssRect']['bottom']-x['cssRect']['top']>90,'renamed source card',False)
                        await choose(lambda x:x['kind']=='button' and x['path'].startswith(card['path']+'.') and 'trash' in x.get('icons',[]),'source delete')
                        await choose(lambda x:x['kind']=='button' and x['label'].strip()=='OK','delete confirmation')
                        # The original confirmation modal fades out after its
                        # clicked() handler returns. A nested SaveSlot toggle
                        # under that overlay must not be clicked during fade.
                        await page.wait_for_timeout(950)
                        assert not await page.evaluate('p=>__mindustryStorage.exists(p)',sources[0])
                        assert await bytes_at()==before,'Deleting source archive must not delete canonical checkpoint'
                        assert not await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")'),'Deleting non-cloud archive wrote cloud reset marker'
                        card=await choose(lambda x:x['kind']=='button' and 'untitled' in x['label'].lower() and x['cssRect']['bottom']-x['cssRect']['top']>90,'canonical card',False)
                        assert any(x['kind']=='button' and x['path'].startswith(card['path']+'.') and 'trash' in x.get('icons',[]) and not x['enabled'] for x in (await controls())['controls']),'Active canonical delete must be guarded'
                        before_auto=await page.evaluate('__mindustryQaSetting("save-web-local-survival-autosave")')
                        await choose(lambda x:x['kind']=='button' and x['path'].startswith(card['path']+'.') and 'save' in x.get('icons',[]),'disable canonical autosave')
                        # Stock SaveSlot.setAutosave writes the setting directly in
                        # the nested ImageButton clicked() handler. A deferred
                        # test result must not be confused with a never-hit icon.
                        for trial in range(15):
                            if not bool(await page.evaluate('__mindustryQaSetting("save-web-local-survival-autosave")')):break
                            await page.wait_for_timeout(100)
                        actual_auto=await page.evaluate('__mindustryQaSetting("save-web-local-survival-autosave")')
                        if bool(actual_auto):
                            ui_state=await controls()
                            related=[{k:c.get(k) for k in ('path','label','icons','enabled','onScreen','x','y','checked','visibleCssRect')}
                                     for c in ui_state['controls'] if c.get('path','').startswith(card['path']+'.') and 'save' in c.get('icons',[])]
                            print('NATIVE_AUTOSAVE_TOGGLE_DIAGNOSTIC '+json.dumps({'before':before_auto,'after':actual_auto,'controls':related,
                               'game':await page.evaluate('__mindustryQaSnapshot()')},ensure_ascii=False),flush=True)
                        assert bool(actual_auto) is False,('native save slot toggled without callback',before_auto,actual_auto)
                        await close_dialogs();await named('web-pause-resume')
                        tick=(await page.evaluate('__mindustryQaSnapshot()'))['tick'];before=await bytes_at()
                        await page.wait_for_function('t=>__mindustryQaSnapshot().tick>t+660',arg=tick,timeout=30000)
                        assert await bytes_at()==before,'Disabled autosave wrote the canonical slot'
                        # Manual Save remains functional with automatic saving off.
                        await page.keyboard.press('Escape');await page.wait_for_function('__mindustryQaSnapshot().paused')
                        await named('web-pause-save');await page.evaluate('()=>__mindustryStorage.flush()')
                        assert await bytes_at()!=before,'Manual Save was incorrectly disabled with autosave'
                        await open_saves()
                        card=await choose(lambda x:x['kind']=='button' and 'untitled' in x['label'].lower() and x['cssRect']['bottom']-x['cssRect']['top']>90,'canonical card',False)
                        await choose(lambda x:x['kind']=='button' and x['path'].startswith(card['path']+'.') and 'save' in x.get('icons',[]),'enable canonical autosave')
                        assert bool(await page.evaluate('__mindustryQaSetting("save-web-local-survival-autosave")')) is True
                        await close_dialogs();await named('web-pause-resume');before=await bytes_at()
                        await page.wait_for_function('q=>JSON.stringify(Array.from(Uint8Array.from(__mindustryStorage.get(q.path)||[])))!==q.bytes',arg={'path':CANONICAL,'bytes':json.dumps(list(before),separators=(',',':'))},timeout=30000)
                        # Deleting the stable slot from the stopped menu must be
                        # durable authority over older cloud data. Cancel is inert.
                        await page.keyboard.press('Escape');await page.wait_for_function('__mindustryQaSnapshot().paused')
                        await named('web-pause-menu');await choose(lambda x:x['kind']=='button' and x['label'].strip()=='OK','exit confirmation')
                        await page.wait_for_function('__mindustryQaSnapshot().menu')
                        await open_saves()
                        card=await choose(lambda x:x['kind']=='button' and 'untitled' in x['label'].lower() and x['cssRect']['bottom']-x['cssRect']['top']>90,'menu canonical card',False)
                        before=await bytes_at();marker=await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")')
                        await choose(lambda x:x['kind']=='button' and x['path'].startswith(card['path']+'.') and 'trash' in x.get('icons',[]),'canonical delete cancel')
                        await label('Cancel')
                        assert await bytes_at()==before and await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")')==marker
                        await choose(lambda x:x['kind']=='button' and x['path'].startswith(card['path']+'.') and 'trash' in x.get('icons',[]),'canonical delete confirm')
                        await choose(lambda x:x['kind']=='button' and x['label'].strip()=='OK','canonical delete confirmation')
                        assert not await page.evaluate('p=>__mindustryStorage.exists(p)',CANONICAL)
                        assert await page.evaluate('__mindustryQaSetting("web-campaign-reset-at")')>0
                        await page.evaluate('()=>__mindustryStorage.flush()')
                        await context.close();context=await launch();page=await context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
                        await ready('?lang=en')
                        assert not await page.evaluate('p=>__mindustryStorage.exists(p)',CANONICAL),'Cold restart resurrected deleted canonical save'
                        assert not errors,errors
                        print(mode+': native MSAV file import/export/load, sandbox rules, source preservation, cold restart, paused-world load, Unicode rename/delete, rejected-file atomicity and disabled/manual/enabled autosave PASS',flush=True)
                        await page.screenshot(path=str(OUT/(mode+'.png')))
                    except Exception:
                        (OUT/(mode+'-failure.json')).write_text(json.dumps(await controls(),ensure_ascii=False,indent=2))
                        await page.screenshot(path=str(OUT/(mode+'-failure.png')));raise
                    finally:await context.close()
    finally:
        if server:server.shutdown()
        for script in reversed(installed):subprocess.run(['python3',str(ROOT/'scripts'/script),'--restore'],check=True)
        assert runtime.read_bytes()==pristine,'Native library probes changed the packaged runtime'

asyncio.run(main())

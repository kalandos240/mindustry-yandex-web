"""Paste a real MSCH through native DOM input and round-trip native export."""
import asyncio,base64,hashlib,json,os,shutil,struct,subprocess,threading,zlib
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1];STAGE=ROOT/'web-runtime/build/web';OUT=ROOT/'work/schematic-clipboard-qa'

def schematic():
    def utf(value):
        b=value.encode('utf-8');return struct.pack('>H',len(b))+b
    # Pinned MSCH v1: one 1x1 conveyor, name tag, null config and rotation zero.
    body=struct.pack('>hhB',1,1,1)+utf('name')+utf('QA схема')
    body+=bytes([1])+utf('conveyor')+struct.pack('>iBiBB',1,0,0,0,0)
    return b'msch\x01'+zlib.compress(body)

def read_tags(value):
    assert value[:5]==b'msch\x01'
    data=memoryview(zlib.decompress(value[5:]));width,height,count=struct.unpack_from('>hhB',data);offset=5;tags={}
    for _ in range(count):
        size=struct.unpack_from('>H',data,offset)[0];offset+=2;key=bytes(data[offset:offset+size]).decode();offset+=size
        size=struct.unpack_from('>H',data,offset)[0];offset+=2;val=bytes(data[offset:offset+size]).decode();offset+=size;tags[key]=val
    return width,height,tags

async def main():
    OUT.mkdir(parents=True,exist_ok=True);runtime=STAGE/'mindustry.js';pristine=runtime.read_bytes();installed=[];server=None
    try:
        for script in ['prepare-qa-probe.py','prepare-ui-control-probe.py']:
            subprocess.run(['python3',str(ROOT/'scripts'/script)],check=True);installed.append(script)
        server=ThreadingHTTPServer(('127.0.0.1',8170),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
        threading.Thread(target=server.serve_forever,daemon=True).start()
        async with async_playwright() as pw:
            browser=await pw.chromium.launch(executable_path=os.environ.get('MINDUSTRY_CHROME_EXECUTABLE') or shutil.which('google-chrome'),headless=True,
                args=['--no-sandbox','--disable-dev-shm-usage','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
            try:
                for mobile in [False,True]:
                    mode='mobile' if mobile else 'desktop';context=await browser.new_context(viewport={'width':390 if mobile else 1280,'height':844 if mobile else 800},is_mobile=mobile,has_touch=mobile)
                    await context.add_init_script("""(() => {
                        window.__qaMsImport = {changes:[],reads:[],loads:[],errors:[]};
                        document.addEventListener('change', event => {
                            if(event.target instanceof HTMLInputElement && event.target.type === 'file'){
                                __qaMsImport.changes.push(Array.from(event.target.files||[],f=>({name:f.name,size:f.size})));
                            }
                        },true);
                        const original = FileReader.prototype.readAsArrayBuffer;
                        FileReader.prototype.readAsArrayBuffer = function(file){
                            __qaMsImport.reads.push({name:file.name,size:file.size});
                            this.addEventListener('load',()=>__qaMsImport.loads.push({name:file.name,bytes:this.result?.byteLength||0}),{once:true});
                            this.addEventListener('error',()=>__qaMsImport.errors.push({name:file.name,error:String(this.error)}),{once:true});
                            return original.call(this,file);
                        };
                    })()""")
                    page=await context.new_page();errors=[];console_events=[]
                    page.on('pageerror',lambda e:errors.append(str(e)))
                    page.on('console',lambda m:console_events.append({'type':m.type,'text':m.text[:1600]}) if m.type in ['error','warning','log'] else None)
                    async def controls():return await page.evaluate('()=>__mindustryUiControls({includeOffscreen:true,includeDisabled:true})')
                    async def choose(predicate,label):
                        for attempt in range(28):
                            value=await controls();choices=[x for x in value['controls'] if predicate(x) and x['enabled'] and x['onScreen']]
                            if choices:
                                t=choices[0]
                                if mobile:await page.touchscreen.tap(t['x'],t['y'])
                                else:await page.mouse.click(t['x'],t['y'])
                                await page.wait_for_timeout(450);return
                            panes=[x for x in value['controls'] if x['kind']=='scrollpane' and x['onScreen'] and x.get('maxY',0)>0]
                            if panes:
                                p=max(panes,key=lambda x:x['maxY']);await page.mouse.move(p['x'],p['y']);await page.mouse.wheel(0,220 if attempt<18 else -220)
                            await page.wait_for_timeout(200)
                        raise AssertionError(label+' missing: '+json.dumps(value,ensure_ascii=False)[:15000])
                    async def button(label):await choose(lambda x:x['kind']=='button' and label.lower() in x['label'].lower(),label)
                    async def close_all():
                        for _ in range(6):
                            if not (await controls())['modal']:return
                            await page.keyboard.press('Escape');await page.wait_for_timeout(450)
                    try:
                        await page.goto('http://127.0.0.1:8170/index.html?lang=en')
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=150000)
                        if not mobile:await choose(lambda x:x['name']=='web-main-database-group','database group')
                        await choose(lambda x:x['name']=='web-main-schematics','schematics')
                        await button('Import')
                        await button('Clipboard')
                        await page.wait_for_selector('#mindustry-text-input')
                        payload=schematic();encoded=base64.b64encode(payload).decode()
                        field=page.locator('#mindustry-text-input textarea');await field.fill(encoded)
                        await field.press('Control+Enter')
                        await page.wait_for_function("()=>__mindustryUiText().text.includes('QA схема')")
                        files=await page.evaluate("()=>__mindustryStorage.paths().filter(p=>p.startsWith('schematics/')&&p.endsWith('.msch'))")
                        assert len(files)==1,files
                        saved=bytes(await page.evaluate('p=>Array.from(Uint8Array.from(__mindustryStorage.get(p)))',files[0]))
                        assert read_tags(saved)[0:2]==(1,1) and read_tags(saved)[2]['name']=='QA схема'
                        # Native Info -> Export -> Copy path must show selectable
                        # text even if clipboard permission is denied by the host.
                        await button('Export');await button('Clipboard')
                        await page.wait_for_selector('#mindustry-text-input textarea[readonly]')
                        export_field=page.locator('#mindustry-text-input textarea')
                        exported=base64.b64decode(await export_field.input_value())
                        assert read_tags(exported)[2]['name']=='QA схема'
                        await page.locator('#mindustry-text-input button').nth(0).click()
                        await page.wait_for_function("()=>{const t=document.querySelector('#mindustry-text-input button').textContent;return t.startsWith('Copied') || t.includes('Ctrl+C');}")
                        await page.locator('#mindustry-text-input button').nth(1).click()
                        await page.wait_for_function("!document.getElementById('mindustry-text-input')")
                        # Actual Blob download, parsed as a real MSCH rather than
                        # merely checking that an export click callback fired.
                        await button('Export')
                        async with page.expect_download() as item:
                            await button('File')
                        download=await item.value;downloaded=Path(await download.path()).read_bytes()
                        # Preserve the actual *stock-exported* MSCH bytes for
                        # offline v1 parsing. Header/tag-only validation cannot
                        # prove that the block-data section round-trips.
                        (OUT/(mode+'-native-export.msch')).write_bytes(downloaded)
                        (OUT/(mode+'-original-fixture.msch')).write_bytes(payload)
                        assert read_tags(downloaded)[2]['name']=='QA схема'
                        assert not errors,errors
                        # File import also round-trips through FileReader, native
                        # Schematics parser, persistent library and Info dialog.
                        await close_all()
                        # Original BaseDialog.hide() animates its modal overlay.
                        # A click issued while it is still fading can be consumed
                        # by that old dialog. The desktop menu also has a
                        # Database/Schematics submenu, not a direct home button.
                        await page.wait_for_timeout(850)
                        async def schematics_entry_visible():
                            data=await controls()
                            return any(c.get('name')=='web-main-schematics' and c['onScreen'] for c in data['controls'])
                        for retry in range(4):
                            if await schematics_entry_visible():break
                            if not mobile:
                                await choose(lambda x:x['name']=='web-main-database-group','open Database submenu for Schematics')
                            await page.wait_for_timeout(350)
                        assert await schematics_entry_visible(),'Stock Schematics submenu never appeared'
                        await choose(lambda x:x['name']=='web-main-schematics','schematics')
                        await page.wait_for_function("()=>__mindustryUiControls().modal?.title === 'Schematics'")
                        await button('Import')
                        async with page.expect_file_chooser() as item:
                            await button('File')
                        chooser=await item.value;await chooser.set_files({'name':'roundtrip.msch','mimeType':'application/octet-stream','buffer':downloaded})
                        # A real FileReader callback runs asynchronously; allow it
                        # to finish before checking disk. If this is the same-name
                        # duplicate, stock Schematics.findFile() must generate a
                        # distinct numbered .msch file rather than clobbering it.
                        async def file_import_done():
                            paths=await page.evaluate("()=>__mindustryStorage.paths()")
                            return len([p for p in paths if p.startswith('schematics/') and p.endswith('.msch')])>=2
                        for attempt in range(70):
                            if await file_import_done():break
                            await page.wait_for_timeout(150)
                        paths=await page.evaluate("()=>__mindustryStorage.paths()")
                        if not await file_import_done():
                            diagnostic=await page.evaluate("""()=>({
                                fileReader:window.__qaMsImport,
                                fileInputCount:document.querySelectorAll('input[type=file]').length,
                                console:window.__qaNativeConsole||[],
                                stockStage:__mindustryQaSetting('qa-schematic-import-stage'),
                                stockBeforeCatch:__mindustryQaSetting('qa-schematic-last-stage'),
                                stockErrorClass:__mindustryQaSetting('qa-schematic-error-class'),
                                stockErrorMessage:__mindustryQaSetting('qa-schematic-error-message'),
                                nativePhase:document.documentElement.getAttribute('data-mindustry-browser-native-import-phase'),
                                nativeCount:document.documentElement.getAttribute('data-mindustry-browser-native-import-count'),
                                pickerState:document.documentElement.getAttribute('data-mindustry-browser-file-state'),
                                pickerError:document.documentElement.getAttribute('data-mindustry-browser-file-error'),
                                gameError:document.documentElement.getAttribute('data-mindustry-web-error'),
                                paths:__mindustryStorage.paths(),
                                text:__mindustryUiText().text.slice(0,700)
                            })""")
                            diagnostic['consoleEvents']=console_events[-35:]
                            print('SCHEMATIC_FILE_IMPORT_DIAGNOSTIC '+json.dumps(diagnostic,ensure_ascii=False),flush=True)
                            raise AssertionError('Native MSCH file-import callback failed to persist a second numbered schematic')
                        assert 'QA схема' in (await page.evaluate('__mindustryUiText()'))['text']
                        assert not any(p.startswith('tmp/import-') for p in paths),paths
                        assert not errors,errors
                        await page.screenshot(path=str(OUT/(mode+'.png')))
                        print(mode+': actual MSCH clipboard import, persistent library, native copy/fallback, Blob export and FileReader round-trip PASS',flush=True)
                    except Exception:
                        (OUT/(mode+'-failure.json')).write_text(json.dumps(await controls(),ensure_ascii=False,indent=2));await page.screenshot(path=str(OUT/(mode+'-failure.png')));raise
                    finally:await context.close()
            finally:await browser.close()
    finally:
        if server:server.shutdown()
        for script in reversed(installed):subprocess.run(['python3',str(ROOT/'scripts'/script),'--restore'],check=True)
        assert runtime.read_bytes()==pristine

asyncio.run(main())

"""Paste a real MSCH through native DOM input and round-trip native export."""
import asyncio,base64,hashlib,json,os,re,shutil,struct,subprocess,threading,zlib
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1];STAGE=ROOT/'web-runtime/build/web';OUT=ROOT/'work/schematic-clipboard-qa'

def schematic():
    def utf(value):
        b=value.encode('utf-8');return struct.pack('>H',len(b))+b
    # Pinned MSCH v1: a 1x1 sorter configured to copper. Deliberately use a
    # foreign item ID to prove contentMap restores by *name* rather than ID.
    # ContentType.item=0, TypeIO MappableContent=5, stock sorter config=Item.
    foreign_id=1234
    mapping=json.dumps({'0':{'copper':foreign_id}},separators=(',',':'))
    body=struct.pack('>hhB',1,1,2)+utf('name')+utf('QA схема')+utf('contentMap')+utf(mapping)
    body+=bytes([1])+utf('sorter')+struct.pack('>iBiBBhB',1,0,0,5,0,foreign_id,0)
    return b'msch\x01'+zlib.compress(body)

def read_tags(value):
    assert value[:5]==b'msch\x01'
    data=memoryview(zlib.decompress(value[5:]));width,height,count=struct.unpack_from('>hhB',data);offset=5;tags={}
    for _ in range(count):
        size=struct.unpack_from('>H',data,offset)[0];offset+=2;key=bytes(data[offset:offset+size]).decode();offset+=size
        size=struct.unpack_from('>H',data,offset)[0];offset+=2;val=bytes(data[offset:offset+size]).decode();offset+=size;tags[key]=val
    return width,height,tags

def assert_sorter_mapping(value):
    width,height,tags=read_tags(value)
    assert (width,height)==(1,1)
    # Arc's JsonIO writes relaxed JSON with bare object keys, e.g.
    # {0:{copper:0}}. The stock Java JsonReader accepts this syntax, while
    # Python's strict json.loads does not. Quote only bare keys for our QA
    # decoder; then inspect the saved TypeIO bytes independently.
    map_text=re.sub(r'(?<=[{,])\s*([A-Za-z_][\w.-]*|\d+)\s*:',r'"\1":',tags['contentMap'])
    mapping=json.loads(map_text)
    assert 'copper' in mapping.get('0',{}),mapping
    # Inspect the actual exported TypeIO config, not just the UI label or
    # metadata. The imported foreign ID 1234 must become the runtime copper ID.
    data=memoryview(zlib.decompress(value[5:]));offset=5
    for _ in range(data[4]):
        for _ in range(2):
            size=struct.unpack_from('>H',data,offset)[0]
            offset+=2+size
    assert data[offset]==1,'Expected one dictionary block'
    offset+=1
    size=struct.unpack_from('>H',data,offset)[0];offset+=2
    assert bytes(data[offset:offset+size])==b'sorter'
    offset+=size
    total,index,position,kind,content_type,item_id,rotation=struct.unpack_from('>iBiBBhB',data,offset)
    assert (total,index,position,kind,content_type,rotation)==(1,0,0,5,0,0)
    assert item_id==mapping['0']['copper'] and item_id!=1234,(item_id,mapping)

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
                    page=await context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
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
                    async def open_database_group():
                        # The desktop group may receive its first click during
                        # asynchronous initial menu layout. Confirm actual native
                        # submenu visibility before moving on; never fire Java
                        # callbacks directly or infer success from a click alone.
                        for attempt in range(3):
                            value=await controls()
                            if any(x.get('name')=='web-main-schematics' and x['onScreen']
                                   for x in value['controls']):
                                print('NATIVE_DATABASE_FIRST_CLICK '+json.dumps({
                                    'attempt':attempt,'submenu':await page.get_attribute(
                                        'html','data-mindustry-main-menu-database-submenu'),
                                    'scene':value.get('stage')},ensure_ascii=False),flush=True)
                                return
                            if await page.get_attribute('html','data-mindustry-main-menu-database-submenu')=='open':
                                await page.wait_for_timeout(450)
                                continue
                            await choose(lambda x:x['name']=='web-main-database-group','database group')
                            try:
                                await page.wait_for_function(
                                    """() => document.documentElement.getAttribute('data-mindustry-main-menu-database-submenu')==='open'
                                      && __mindustryUiControls({includeOffscreen:true,includeDisabled:true})
                                        .controls.some(c=>c.name==='web-main-schematics' && c.onScreen)""",
                                    timeout=2500)
                                print('NATIVE_DATABASE_FIRST_CLICK '+json.dumps({
                                    'attempt':attempt,'submenu':await page.get_attribute(
                                        'html','data-mindustry-main-menu-database-submenu'),
                                    'scene':(await controls()).get('stage')},ensure_ascii=False),flush=True)
                                return
                            except Exception:
                                # Retry an ordinary pointer click, not a
                                # programmatic Arc button callback.
                                await page.wait_for_timeout(350)
                        value=await controls()
                        raise AssertionError('Native desktop Database submenu failed to open after trusted clicks: '
                            +json.dumps({'menuAttribute':await page.get_attribute(
                                'html','data-mindustry-main-menu-database-submenu'),'controls':value},ensure_ascii=False)[:12000])
                    async def button(label):await choose(lambda x:x['kind']=='button' and label.lower() in x['label'].lower(),label)
                    async def close_all():
                        for _ in range(6):
                            if not (await controls())['modal']:return
                            await page.keyboard.press('Escape');await page.wait_for_timeout(450)
                    try:
                        await page.goto('http://127.0.0.1:8170/index.html?lang=en')
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=150000)
                        if not mobile:await open_database_group()
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
                        assert read_tags(saved)[2]['name']=='QA схема'
                        assert_sorter_mapping(saved)
                        # Native Info -> Export -> Copy path must show selectable
                        # text even if clipboard permission is denied by the host.
                        await button('Export');await button('Clipboard')
                        await page.wait_for_selector('#mindustry-text-input textarea[readonly]')
                        export_field=page.locator('#mindustry-text-input textarea')
                        exported=base64.b64decode(await export_field.input_value())
                        assert read_tags(exported)[2]['name']=='QA схема'
                        assert_sorter_mapping(exported)
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
                        assert read_tags(downloaded)[2]['name']=='QA схема'
                        assert_sorter_mapping(downloaded)
                        assert not errors,errors
                        # File import also round-trips through FileReader, native
                        # Schematics parser, persistent library and Info dialog.
                        await close_all()
                        if not mobile:
                            value=await controls()
                            if not any(x['name']=='web-main-schematics' and x['onScreen'] for x in value['controls']):await open_database_group()
                        await choose(lambda x:x['name']=='web-main-schematics','schematics')
                        await button('Import')
                        async with page.expect_file_chooser() as item:
                            await button('File')
                        chooser=await item.value;await chooser.set_files({'name':'roundtrip.msch','mimeType':'application/octet-stream','buffer':downloaded})
                        await page.wait_for_timeout(1200)
                        assert 'QA схема' in (await page.evaluate('__mindustryUiText()'))['text']
                        paths=await page.evaluate("()=>__mindustryStorage.paths()")
                        assert not any(p.startswith('tmp/import-') for p in paths),paths
                        assert len([p for p in paths if p.startswith('schematics/') and p.endswith('.msch')])>=2
                        assert not errors,errors
                        await page.screenshot(path=str(OUT/(mode+'.png')))
                        print(mode+': real MSCH item-ID remapping, clipboard import, persistent library, native copy/fallback, Blob export and FileReader round-trip PASS',flush=True)
                    except Exception:
                        (OUT/(mode+'-failure.json')).write_text(json.dumps(await controls(),ensure_ascii=False,indent=2));await page.screenshot(path=str(OUT/(mode+'-failure.png')));raise
                    finally:await context.close()
            finally:await browser.close()
    finally:
        if server:server.shutdown()
        for script in reversed(installed):subprocess.run(['python3',str(ROOT/'scripts'/script),'--restore'],check=True)
        assert runtime.read_bytes()==pristine

asyncio.run(main())

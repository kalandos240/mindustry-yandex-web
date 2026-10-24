"""Native cold sector Go: old Settings monitor wait vs browser-confined settings."""
import asyncio,shutil,subprocess,tempfile,threading
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from zipfile import ZipFile
from playwright.async_api import async_playwright,Error

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'
JS=STAGE/'mindustry.js'

def held_settings(source):
    anchor='mw_Campaign_diagPhase = $phase => {'
    assert source.count(anchor)==1,'Campaign phase anchor changed'
    assert '$owner0' in source and 'jl_Object_createMonitor =' in source
    fault='''
    if($rt_ustr($phase)==='resume-world-loaded' && !globalThis.__heldSettings){
        globalThis.__heldSettings=true;
        jl_Object_createMonitor(a_Core_settings);
        a_Core_settings.$monitor.$owner0={};
        a_Core_settings.$monitor.$count3=1;
        document.documentElement.setAttribute('data-settings-monitor-fixture','held');
    }
'''
    return source.replace(anchor,anchor+fault,1)

async def main():
    pristine=JS.read_bytes()
    with ZipFile(ROOT/'work/monitor-original/mindustry-yandex-release.zip') as z:
        previous=z.read('mindustry.js')
    server=ThreadingHTTPServer(('127.0.0.1',8146),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        async with async_playwright() as pw:
            async def launch(profile):
                return await pw.firefox.launch_persistent_context(str(profile),headless=False,
                    viewport={'width':2400,'height':610},
                    firefox_user_prefs={'webgl.force-enabled':True,'gfx.webrender.software':True})
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);seed=root/'seed'
                # A real v13 world is saved to real IndexedDB, then the entire browser closes.
                subprocess.run(['python3',str(ROOT/'scripts/prepare-save-clock-fixture.py')],check=True)
                context=await launch(seed)
                try:
                    page=await context.new_page()
                    await page.goto('http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSmoke=groundZero&mindustryCampaignSaveSmoke=1&clockFixtureTick=216000.99949999998')
                    await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-campaign-save-flush')==='ready'",timeout=180000)
                finally:await context.close()
                for name,source,blocked in [('previous-held',held_settings(previous.decode()),True),
                                             ('fixed-held',held_settings(pristine.decode()),False),
                                             ('fixed-normal',pristine.decode(),False)]:
                    JS.write_text(source)
                    profile=root/name;shutil.copytree(seed,profile)
                    context=await launch(profile)
                    try:
                        page=await context.new_page()
                        async def click(request):
                            await page.mouse.click(request['x'],request['y'])
                            await page.evaluate("id=>document.documentElement.setAttribute('data-mindustry-cdp-click-ack',id)",request['id'])
                        await page.expose_function('__firefoxNativeClick',click)
                        await page.goto('http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSaveSmoke=0&savedPlanet=serpulo&expectedTickMillis=216000999')
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=180000)
                        await page.evaluate("""()=>new MutationObserver(ms=>{for(const m of ms){if(m.attributeName==='data-mindustry-cdp-click-request')window.__firefoxNativeClick(JSON.parse(document.documentElement.getAttribute(m.attributeName)));}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-mindustry-cdp-click-request']})""")
                        try:
                            await page.evaluate((ROOT/'scripts/browser-save-clock-go.js').read_text())
                            assert not blocked,'Old monitor unexpectedly allowed Go'
                        except Error:
                            if not blocked:raise
                        if blocked:
                            assert await page.get_attribute('html','data-settings-monitor-fixture')=='held'
                            assert await page.get_attribute('html','data-mindustry-campaign-phase')=='resume-world-loaded'
                            assert await page.get_attribute('html','data-mindustry-cold-sector-go')!='passed'
                            print('Previous runtime: occupied Settings monitor REPRODUCES native Ground Zero stop exactly at resume-world-loaded',flush=True)
                        else:
                            assert await page.get_attribute('html','data-mindustry-cold-sector-go')=='passed'
                            assert not await page.get_attribute('html','data-mindustry-campaign-launch-failure')
                            assert await page.get_attribute('html','data-mindustry-campaign-resume-wave')=='3'
                            assert await page.get_attribute('html','data-mindustry-campaign-resume-tick-ms')=='216000999'
                            print(name+': Firefox 2400x610 native Ground Zero Go, exact wave/tick and live simulation PASS',flush=True)
                        await page.screenshot(path=str(ROOT/f'work/monitor-{name}.png'))
                        (ROOT/f'work/monitor-{name}.html').write_text(await page.content())
                    finally:await context.close()
    finally:
        JS.write_bytes(pristine);server.shutdown()

asyncio.run(main())

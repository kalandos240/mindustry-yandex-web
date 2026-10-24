#!/usr/bin/env python3
"""Reproduce native cold lost-sector Go in Firefox at the portal viewport."""
import asyncio
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
import subprocess,tempfile,threading,json,base64
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'
JS=STAGE/'mindustry.js'
async def main():
    pristine=JS.read_bytes()
    server=ThreadingHTTPServer(('127.0.0.1',8146),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        async with async_playwright() as pw:
            for sector,planet in [('groundZero','serpulo'),('onset','erekir')]:
                with tempfile.TemporaryDirectory() as profile:
                    async def launch():
                        return await pw.firefox.launch_persistent_context(profile,headless=False,viewport={'width':2400,'height':660},firefox_user_prefs={'webgl.force-enabled':True,'gfx.webrender.software':True})
                    JS.write_bytes(pristine)
                    subprocess.run(['python3',str(ROOT/'scripts/prepare-lost-core-fixture.py')],check=True)
                    ctx=await launch();page=await ctx.new_page()
                    page.on('console',lambda msg:print('SEED CONSOLE',msg.type,msg.text,flush=True))
                    page.on('pageerror',lambda e:print('SEED PAGEERROR',str(e),flush=True))
                    await page.goto(f'http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSmoke={sector}&mindustryCampaignSaveSmoke=1&lostCore=1')
                    await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-campaign-save-flush')==='ready'",timeout=150000)
                    await ctx.close()
                    JS.write_bytes(pristine)
                    subprocess.run(['python3',str(ROOT/'scripts/prepare-lost-core-fixture.py'),'--read-probe'],check=True)
                    ctx=await launch();page=await ctx.new_page()
                    downloads=[]
                    page.on('download',lambda download:downloads.append(download))
                    page.on('console',lambda msg:print('GO CONSOLE',msg.type,msg.text,flush=True))
                    page.on('pageerror',lambda e:print('GO PAGEERROR',str(e),flush=True))
                    async def click(q):
                        await page.mouse.click(q['x'],q['y'],delay=100)
                        await page.evaluate("id=>document.documentElement.setAttribute('data-mindustry-cdp-click-ack',id)",q['id'])
                    await page.expose_function('__firefoxNativeClick',click)
                    await page.goto(f'http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSaveSmoke=0&savedPlanet={planet}&expectLostCore=1')
                    await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=150000)
                    await page.evaluate("""()=>new MutationObserver(ms=>{for(const m of ms){const value=document.documentElement.getAttribute(m.attributeName);if(m.attributeName==='data-mindustry-cdp-click-request')window.__firefoxNativeClick(JSON.parse(value));else if(m.attributeName==='data-mindustry-campaign-phase')console.log('LAUNCH PHASE',value);}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-mindustry-cdp-click-request','data-mindustry-campaign-phase']})""")
                    try:
                        driver=(ROOT/'scripts/browser-cold-sector-go.js').read_text()
                        driver=driver.replace(" await click('planet-hit-launch');", " globalThis.__expectedExportBytes=Array.from(savedBytes); await click('planet-hit-export'); await wait(()=>v('sector-export')==='ready','native sector export'); await click('planet-hit-launch');")
                        await asyncio.wait_for(page.evaluate(driver),timeout=100)
                        for attempt in range(100):
                            if downloads:break
                            await page.wait_for_timeout(100)
                        assert len(downloads)==1,'Native export did not download one file'
                        exported_path=ROOT/f'work/firefox-export-{sector}.json'
                        await downloads[0].save_as(str(exported_path))
                        exported=json.loads(exported_path.read_text())
                        assert exported['format']=='mindustry-web-sector-v1'
                        assert exported['sector']==sector
                        expected=bytes(await page.evaluate('globalThis.__expectedExportBytes'))
                        assert base64.b64decode(exported['files'][exported['savePath']])==expected
                        print(f'Firefox {sector}: native selected-sector export exact bytes PASS',flush=True)
                        assert await page.get_attribute('html','data-mindustry-cold-sector-go')=='passed'
                        await page.screenshot(path=str(ROOT/f'work/firefox-lost-{sector}.png'))
                        print(f'Firefox {sector}: native cold lost-sector Go, retained buildings and exact backup PASS',flush=True)
                    finally:
                        (ROOT/f'work/firefox-lost-{sector}.html').write_text(await page.content())
                        await ctx.close()
    finally:
        JS.write_bytes(pristine)
        server.shutdown()
asyncio.run(main())

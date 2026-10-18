#!/usr/bin/env python3
"""Cold native Go with wave-three saves in an actual Firefox process."""
import asyncio
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import subprocess
import tempfile
import threading

from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / 'web-runtime/build/web'
JS = STAGE / 'mindustry.js'


async def main():
    pristine = JS.read_bytes()
    server = ThreadingHTTPServer(('127.0.0.1', 8146), partial(SimpleHTTPRequestHandler, directory=str(STAGE)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        async with async_playwright() as pw:
            for sector, planet, tick, expected in [
                ('groundZero', 'serpulo', '216000.99949999998', '216000999'),
                ('onset', 'erekir', '9999999.999499999', '9999999999'),
            ]:
                with tempfile.TemporaryDirectory() as profile:
                    async def launch():
                        return await pw.firefox.launch_persistent_context(
                            profile, headless=False, viewport={'width': 1280, 'height': 800},
                            firefox_user_prefs={'webgl.force-enabled': True, 'gfx.webrender.software': True},
                        )
                    subprocess.run(['python3', str(ROOT / 'scripts/prepare-save-clock-fixture.py')], check=True)
                    context = await launch()
                    page = await context.new_page()
                    await page.goto(f'http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSmoke={sector}&mindustryCampaignSaveSmoke=1&clockFixtureTick={tick}')
                    await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-campaign-save-flush')==='ready'", timeout=150000)
                    await context.close()
                    JS.write_bytes(pristine)
                    context = await launch()
                    page = await context.new_page()

                    async def click(request):
                        await page.mouse.click(request['x'], request['y'])
                        await page.evaluate("id=>document.documentElement.setAttribute('data-mindustry-cdp-click-ack',id)", request['id'])

                    await page.expose_function('__firefoxNativeClick', click)
                    await page.goto(f'http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSaveSmoke=0&savedPlanet={planet}&expectedTickMillis={expected}')
                    await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'", timeout=150000)
                    await page.evaluate("""()=>new MutationObserver(ms=>{for(const m of ms){if(m.attributeName==='data-mindustry-cdp-click-request')window.__firefoxNativeClick(JSON.parse(document.documentElement.getAttribute(m.attributeName)));}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-mindustry-cdp-click-request']})""")
                    result = await page.evaluate((ROOT / 'scripts/browser-save-clock-go.js').read_text())
                    assert await page.get_attribute('html', 'data-mindustry-cold-sector-go') == 'passed'
                    for width, height in [(1280, 800), (390, 371), (320, 320), (390, 844)]:
                        await page.set_viewport_size({'width': width, 'height': height})
                        await page.wait_for_timeout(2500)
                        assert await page.get_attribute('html', 'data-mindustry-campaign-state') == 'playing'
                        assert not await page.get_attribute('html', 'data-mindustry-campaign-launch-failure')
                        await page.screenshot(path=str(ROOT / f'work/firefox-{sector}-{width}x{height}.png'))
                    await page.screenshot(path=str(ROOT / f'work/firefox-{sector}.png'))
                    (ROOT / f'work/firefox-{sector}.html').write_text(await page.content())
                    print(f'Firefox {sector}: wave=3 tickMillis={expected} PASS: {result}', flush=True)
                    await context.close()
    finally:
        JS.write_bytes(pristine)
        server.shutdown()


asyncio.run(main())

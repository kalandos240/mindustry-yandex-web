#!/usr/bin/env python3
"""Native Firefox input on the unchanged compiled package, with loss screenshots."""
import asyncio
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'

async def main():
    server=ThreadingHTTPServer(('127.0.0.1',8147),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        async with async_playwright() as pw:
            for seed in ['groundZero','onset']:
                browser=await pw.firefox.launch(headless=False,firefox_user_prefs={'webgl.force-enabled':True,'gfx.webrender.software':True})
                page=await browser.new_page(viewport={'width':2400,'height':660})
                async def native(request):
                    kind=request['kind']; q=request['request']
                    if kind=='click':
                        await page.mouse.click(q['x'],q['y'],delay=100)
                    else:
                        await page.mouse.move(q['x0'],q['y0']);await page.mouse.down()
                        for step in range(1,q.get('steps',18)+1):
                            f=step/q.get('steps',18)
                            await page.mouse.move(q['x0']+(q['x1']-q['x0'])*f,q['y0']+(q['y1']-q['y0'])*f)
                            await page.wait_for_timeout(18)
                        await page.mouse.up()
                    await page.evaluate("q=>document.documentElement.setAttribute('data-mindustry-cdp-'+q.kind+'-ack',q.id)",{'kind':kind,'id':q['id']})
                await page.expose_function('__firefoxNative',native)
                async def bridge():
                    await page.evaluate("""()=>new MutationObserver(ms=>{for(const m of ms){const kind=m.attributeName.includes('-drag-')?'drag':'click';window.__firefoxNative({kind,request:JSON.parse(document.documentElement.getAttribute(m.attributeName))});}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-mindustry-cdp-click-request','data-mindustry-cdp-drag-request']})""")
                await page.goto(f'http://127.0.0.1:8147/index.html?lang=en&mindustrySettingsNestedSmoke=1&mindustryCampaignSmoke={seed}')
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-campaign-state')==='playing'",timeout=150000)
                await bridge()
                await page.evaluate((ROOT/'scripts/browser-gameplay-button-stress.js').read_text())
                assert await page.get_attribute('html','data-mindustry-gameplay-button-stress')=='passed'
                print(f'Firefox {seed}: native sound drags, settings close, same-world save/resume PASS',flush=True)
                await page.goto(f'http://127.0.0.1:8147/index.html?lang=ru&mindustryDefeatSmoke=1&mindustryCampaignSmoke={seed}')
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-defeat-visible')==='yes'",timeout=150000)
                await page.screenshot(path=str(ROOT/f'work/firefox-defeat-{seed}.png'))
                await bridge()
                await page.evaluate((ROOT/'scripts/browser-defeat-exit.js').read_text())
                assert await page.get_attribute('html','data-mindustry-defeat-exit-test')=='passed'
                print(f'Firefox {seed}: real core loss and trusted menu exit PASS',flush=True)
                await browser.close()
    finally: server.shutdown()
asyncio.run(main())

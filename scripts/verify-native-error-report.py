import asyncio,threading
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'
async def main():
    js=STAGE/'mindustry.js';pristine=js.read_bytes()
    text=pristine.decode();anchor='mw_Campaign_updateFrame = () => {';assert text.count(anchor)==1
    fixture='''
        globalThis.__rendererErrorFixture=function(){
            var inner=new jl_IllegalArgumentException;
            jl_Exception__init_(inner,$rt_str('renderer-inner-fixture'));
            $rt_throw(jl_IllegalStateException__init_2($rt_str('c:97'),inner));
        };
'''
    js.write_text(text.replace(anchor,anchor+fixture,1))
    server=ThreadingHTTPServer(('127.0.0.1',8146),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        async with async_playwright() as pw:
            browser=await pw.firefox.launch(headless=False,firefox_user_prefs={'webgl.force-enabled':True,'gfx.webrender.software':True})
            context=await browser.new_context(viewport={'width':2400,'height':610})
            try:
                page=await context.new_page()
                async def click(q):
                    await page.mouse.click(q['x'],q['y'])
                    await page.evaluate("id=>document.documentElement.setAttribute('data-mindustry-cdp-click-ack',id)",q['id'])
                await page.expose_function('__firefoxNativeClick',click)
                await page.goto('http://127.0.0.1:8146/index.html?lang=ru&savedPlanet=serpulo')
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=120000)
                await page.evaluate("""()=>new MutationObserver(ms=>{for(const m of ms){if(m.attributeName==='data-mindustry-cdp-click-request')window.__firefoxNativeClick(JSON.parse(document.documentElement.getAttribute(m.attributeName)));}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-mindustry-cdp-click-request']})""")
                script=(ROOT/'scripts/browser-save-clock-go.js').read_text().split(' const lostExpected')[0]
                script+=" await click('planet-hit-launch');await wait(()=>v('campaign-state')==='playing'&&v('campaign-save')==='valid','fresh world');return 'native Go';})()"
                await page.evaluate(script)
                await page.wait_for_function('typeof __rendererErrorFixture==="function"')
                before=await page.evaluate("()=>Object.fromEntries(__mindustryStorage.paths().filter(p=>p.endsWith('.msav')).map(p=>[p,Array.from(__mindustryStorage.get(p))]))")
                assert before
                await page.evaluate('()=>{setTimeout(()=>__rendererErrorFixture(),0);}')
                await page.wait_for_function('window.__mindustryErrorReport?.causes.some(c=>c.message==="renderer-inner-fixture")')
                report=await page.evaluate('__mindustryErrorReport')
                assert report['causes'][0]['message']=='c:97',report
                assert report['causes'][1]['message']=='renderer-inner-fixture',report
                assert report['causes'][1]['stack'],report
                await page.get_by_role('button',name='Скопировать отчёт',exact=True).click()
                await page.wait_for_function('''()=>Array.from(document.querySelectorAll('#mindustry-error-actions button')).some(b=>['Скопировано','Отчёт выделен'].includes(b.textContent))''')
                await page.screenshot(path=str(ROOT/'work/error-report-native.png'))
                await page.get_by_role('button',name='Перезагрузить',exact=True).click()
                await page.wait_for_load_state()
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=120000)
                assert await page.locator('#mindustry-error-actions').count()==0
                after=await page.evaluate("()=>Object.fromEntries(__mindustryStorage.paths().filter(p=>p.endsWith('.msav')).map(p=>[p,Array.from(__mindustryStorage.get(p))]))")
                assert before==after,'Reload/report must not change sector bytes'
                print('Native Firefox: real nested Java exception, original cause/stack, trusted copy button and reload with identical MSAV bytes PASS',flush=True)
            finally:await context.close();await browser.close()
    finally:js.write_bytes(pristine);server.shutdown()
asyncio.run(main())

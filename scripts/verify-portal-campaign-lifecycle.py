"""Native game in nested cross-origin iframes with a controlled SDK protocol.

This is not a claim of testing the real Yandex SDK or a user's graphics driver.
It checks real Java state/ticks, real WebGL, native input and SDK callback orders.
Requires the read-only probe from prepare-qa-probe.py; all package bytes are restored.
"""
import argparse,asyncio,json,os,shutil,subprocess,threading
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'
MAIN_PORT=8166
MIDDLE_PORT=8167
BASE=f'http://127.0.0.1:{MAIN_PORT}'
CASES={
    'desktop-default':dict(mobile=False,gl1=False,high=False,planet='serpulo'),
    'desktop-combined':dict(mobile=False,gl1=False,high=True,planet='serpulo'),
    'webgl1-combined':dict(mobile=False,gl1=True,high=True,planet='serpulo'),
    'mobile-default':dict(mobile=True,gl1=False,high=False,planet='erekir'),
}

class Handler(SimpleHTTPRequestHandler):
    def log_message(self,format,*args):
        if len(args)>1 and str(args[1]) in {'200','304'}:return
        super().log_message(format,*args)
    def do_GET(self):
        path=urlsplit(self.path).path
        if path=='/sdk.js':return self.reply((ROOT/'scripts/verify-portal-campaign-sdk.js').read_text(),'text/javascript')
        if path=='/portal.html':
            query=urlsplit(self.path).query
            return self.reply('''<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:#262b34;color:white}#slot{position:absolute;left:0;top:70px;border:0}</style></head><body><header>Controlled portal header and advertising area</header>
            <iframe id="slot" width="2400" height="740" allow="autoplay; fullscreen; clipboard-write" allowfullscreen src="http://127.0.0.1:'''+str(MIDDLE_PORT)+'''/portal-middle.html?'''+query+'''"></iframe><script>
            window.__portalSend=action=>document.getElementById('slot').contentWindow.postMessage({kind:'portal-sdk-control',action},'*');
            window.__portalSize=(width,height)=>{const frame=document.getElementById('slot');frame.style.width=width+'px';frame.style.height=(height+130)+'px';};
            </script></body></html>''','text/html')
        if path=='/portal-middle.html':
            query=urlsplit(self.path).query
            return self.reply('''<!doctype html><html><head><style>html,body{width:100%;height:100%;margin:0;overflow:hidden;background:#171c23}iframe{position:absolute;left:0;top:30px;width:100%;height:calc(100% - 130px);border:0}</style></head><body><div>Portal frame chrome</div>
            <iframe id="game" sandbox="allow-scripts allow-same-origin allow-forms allow-modals allow-popups allow-downloads allow-pointer-lock" allow="autoplay; fullscreen; clipboard-write" allowfullscreen src="'''+BASE+'''/index.html?lang=ru&'''+query+'''"></iframe><script>
            addEventListener('message',event=>{if(event.source===parent&&event.data?.kind==='portal-sdk-control')document.getElementById('game').contentWindow.postMessage(event.data,'*');});
            </script></body></html>''','text/html')
        return super().do_GET()
    def reply(self,text,kind):
        data=text.encode();self.send_response(200);self.send_header('Content-Type',kind+'; charset=utf-8');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)

async def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--browser',choices=['firefox','chromium'],default=os.getenv('QA_BROWSER','firefox'))
    parser.add_argument('--headless',action='store_true',default=os.getenv('QA_HEADLESS')=='1')
    parser.add_argument('--case',action='append',choices=list(CASES))
    args=parser.parse_args()
    (ROOT/'work').mkdir(exist_ok=True)
    js=STAGE/'mindustry.js';pristine=js.read_bytes()
    subprocess.run(['python3',str(ROOT/'scripts/prepare-qa-probe.py')],check=True)
    servers=[ThreadingHTTPServer(('127.0.0.1',port),partial(Handler,directory=str(STAGE))) for port in [MAIN_PORT,MIDDLE_PORT]]
    for server in servers:threading.Thread(target=server.serve_forever,daemon=True).start()
    results=[]
    try:
        async with async_playwright() as pw:
            for name in args.case or CASES:
                case=CASES[name];errors=[];console=[];background=[];game=None
                options={'headless':args.headless}
                if args.browser=='firefox':options['firefox_user_prefs']={'webgl.force-enabled':True,'gfx.webrender.software':True,'webgl.disable-webgl2':case['gl1']}
                else:
                    options['executable_path']=os.getenv('QA_CHROME') or shutil.which('google-chrome') or shutil.which('chromium')
                    options['args']=['--no-sandbox','--disable-dev-shm-usage']+(['--disable-webgl2'] if case['gl1'] else [])
                browser=await getattr(pw,args.browser).launch(**options)
                context_options={'viewport':{'width':2400,'height':1350},'has_touch':case['mobile']}
                if args.browser=='chromium':context_options['is_mobile']=case['mobile']
                context=await browser.new_context(**context_options)
                prefs=['pixelate','bloom','animatedwater','animatedshields','drawlight'] if case['high'] else []
                payload='MWS1|'+''.join('b'+str(len(key))+':'+key+'1:1' for key in prefs)
                await context.add_init_script("if(location.pathname==='/index.html')localStorage.setItem('mindustry.web.settings.v1',"+json.dumps(payload)+');')
                await context.add_init_script("globalThis.__portalContextLosses=0;document.addEventListener('webglcontextlost',()=>globalThis.__portalContextLosses++,true);")
                page=await context.new_page();page.on('pageerror',lambda error:errors.append(str(error)))
                def on_console(message):
                    if 'Mindustry error report:' in message.text or '[GL] Error:' in message.text:console.append(message.text)
                page.on('console',on_console)
                pause_scheduled=False
                async def command(action):await page.evaluate('action=>window.__portalSend(action)',action)
                async def click(request):
                    nonlocal pause_scheduled
                    box=await (await game.frame_element()).bounding_box()
                    assert box,'Nested game frame has no bounds'
                    if case['mobile']:await page.touchscreen.tap(box['x']+request['x'],box['y']+request['y'])
                    else:await page.mouse.click(box['x']+request['x'],box['y']+request['y'])
                    if request['prefix']=='planet-hit-launch' and not pause_scheduled:
                        pause_scheduled=True
                        async def around_go():
                            await asyncio.sleep(0.01);await command('pause');await asyncio.sleep(0.25);await command('resume')
                        background.append(asyncio.create_task(around_go()))
                await page.expose_function('__portalNativeClick',click)
                async def snapshot():return await game.evaluate('()=>globalThis.__mindustryQaSnapshot()')
                async def advancing(label):
                    before=await snapshot()
                    await game.wait_for_function('''before=>{const s=__mindustryQaSnapshot();return s.ready&&s.playing&&!s.paused&&s.campaignActive&&s.tick>before.tick+20&&BigInt(s.updateId)>BigInt(before.updateId)+10n;}''',arg=before,timeout=30000)
                    assert not await game.evaluate('()=>globalThis.__mindustryErrorReport || null'),label
                    assert not errors,(label,errors)
                    assert not console,(label,console)
                    assert not await game.evaluate('()=>globalThis.__portalContextLosses'),label+' lost WebGL context'
                    return await snapshot()
                async def freeze(label):
                    await game.wait_for_function("document.documentElement.getAttribute('data-yandex-game-state')==='paused'",timeout=10000)
                    await asyncio.sleep(0.2);before=await snapshot();await asyncio.sleep(0.45);after=await snapshot()
                    assert before['tick']==after['tick'] and before['updateId']==after['updateId'],(label,before,after)
                try:
                    query='savedPlanet='+case['planet']+'&portalDevice='+('mobile' if case['mobile'] else 'desktop')
                    await page.goto(BASE+'/portal.html?'+query)
                    for attempt in range(240):
                        game=next((frame for frame in page.frames if frame.url.startswith(BASE+'/index.html')),None)
                        if game:break
                        await asyncio.sleep(0.1)
                    assert game,'Nested game frame was not created'
                    await page.evaluate('(size)=>window.__portalSize(...size)',[390 if case['mobile'] else 2400,844 if case['mobile'] else 610])
                    await game.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=180000)
                    await game.wait_for_function('typeof __mindustryQaSnapshot==="function"&&__mindustryQaSnapshot().ready',timeout=30000)
                    assert await game.get_attribute('html','data-yandex-sdk')=='ready'
                    result=await game.evaluate((ROOT/'scripts/verify-portal-campaign-go.js').read_text())
                    await asyncio.gather(*background);await advancing('fresh nested Go')
                    for width,height in ([(390,371),(320,320),(390,844)] if case['mobile'] else [(780,437),(1280,720),(2400,610)]):
                        await page.evaluate('(size)=>window.__portalSize(...size)',[width,height])
                        await game.wait_for_function('size=>innerWidth===size[0]&&innerHeight===size[1]',arg=[width,height],timeout=10000)
                        await advancing('portal resize '+str((width,height)))
                        assert await game.get_attribute('html','data-mindustry-canvas-viewport-match')=='true'
                    await page.keyboard.down('d')
                    await game.wait_for_function('__mindustryQaKeyDown("d")',timeout=10000)
                    await command('pause');await freeze('SDK visibility pause')
                    assert await game.get_attribute('html','data-mindustry-input-reset')=='platform-pause'
                    assert not await game.evaluate('()=>__mindustryQaKeyDown("d")'),'SDK pause left the real Java D key held'
                    await command('resume');await page.keyboard.up('d');await advancing('SDK visibility resume')
                    for ordering in ['close-before-resume','resume-before-close','error-before-resume']:
                        assert await game.evaluate('()=>__mindustryYandex.showFullscreenAdv()')
                        await freeze(ordering)
                        if ordering=='resume-before-close':
                            await command('resume');await advancing(ordering);await command('ad-close')
                        else:
                            await command('ad-error' if ordering.startswith('error') else 'ad-close')
                            assert not await game.evaluate('()=>__mindustryYandex.gameplayActive')
                            await command('resume')
                        await game.wait_for_function('!__mindustryYandex.adInFlight&&__mindustryYandex.gameplayActive',timeout=10000)
                        await advancing(ordering+' complete')
                    await game.evaluate('()=>__mindustryStorage.flush()')
                    sdk=await game.evaluate('()=>__portalSdkSnapshot()')
                    assert sdk['ready']==1 and sdk['saves']>0,sdk
                    assert sdk['starts']>=4 and sdk['stops']>=3,sdk
                    results.append({'case':name,'browser':args.browser,'state':await snapshot(),'sdk':sdk,'launch':result})
                    print(name+': nested cross-origin native Go, combined graphics, real ticks, resize, SDK pause and three ad callback orders PASS',flush=True)
                except Exception:
                    if game:
                        detail=await game.evaluate('()=>({attrs:Object.fromEntries(Array.from(document.documentElement.attributes).filter(a=>/error|failure|phase|platform|yandex/.test(a.name)).map(a=>[a.name,a.value])),report:globalThis.__mindustryErrorReport,probe:globalThis.__mindustryQaSnapshot?.()})')
                        print('PORTAL_FAILURE '+json.dumps({'case':name,'detail':detail,'pageErrors':errors,'console':console}),flush=True)
                    raise
                finally:
                    if background:await asyncio.gather(*background,return_exceptions=True)
                    await page.screenshot(path=str(ROOT/f'work/portal-{args.browser}-{name}.png'))
                    if game:(ROOT/f'work/portal-{args.browser}-{name}.html').write_text(await game.content())
                    await context.close();await browser.close()
    finally:
        subprocess.run(['python3',str(ROOT/'scripts/prepare-qa-probe.py'),'--restore'],check=True)
        assert js.read_bytes()==pristine,'Test probe changed packaged runtime'
        for server in servers:server.shutdown()
    (ROOT/f'work/portal-{args.browser}-results.json').write_text(json.dumps(results,indent=2))

if __name__=='__main__':asyncio.run(main())

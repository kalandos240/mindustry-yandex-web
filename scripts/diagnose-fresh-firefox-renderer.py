import asyncio,json,threading,subprocess,sys
from pathlib import Path
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'

async def main():
    runtime=STAGE/'mindustry.js'
    owns_probe=b'/* Mindustry QA read-only state probe. Test builds only. */' not in runtime.read_bytes()
    if owns_probe:subprocess.run([sys.executable,str(ROOT/'scripts/prepare-qa-probe.py'),str(runtime)],check=True)
    server=None
    try:
        server=ThreadingHTTPServer(('127.0.0.1',8146),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
        threading.Thread(target=server.serve_forever,daemon=True).start()
        async with async_playwright() as pw:
            problems=[]
            for gl1 in [False,True]:
                for option in ['default','pixelate','animated','bloom']:
                    browser=await pw.firefox.launch(headless=False,firefox_user_prefs={
                        'webgl.force-enabled':True,'gfx.webrender.software':True,'webgl.disable-webgl2':gl1})
                    context=await browser.new_context(viewport={'width':2400,'height':610})
                    prefs={'pixelate':True} if option=='pixelate' else {'animatedwater':True,'animatedshields':True} if option=='animated' else {'bloom':True} if option=='bloom' else {}
                    payload='MWS1|'+''.join('b'+str(len(k))+':'+k+'1:1' for k in prefs)
                    await context.add_init_script('localStorage.setItem("mindustry.web.settings.v1",'+json.dumps(payload)+');')
                    await context.add_init_script('''
                    window.addEventListener('error',e=>{
                        const error=e.error, key=error && Object.getOwnPropertySymbols(error).find(s=>s.description==='javaException');
                        let value=key && error[key];const chain=[],seen=new Set();
                        while(value && !seen.has(value) && chain.length<12){
                            seen.add(value);
                            const message=Object.entries(value).find(([k,v])=>k.startsWith('$message'));
                            chain.push({type:value.constructor.name,message:message?.[1]?.$nativeString,
                                stack:value.$jsException?.stack});
                            value=Object.entries(value).find(([k,v])=>k.startsWith('$cause'))?.[1];
                        }
                        window.__javaErrorChain=chain;
                        console.log('JAVA_CAUSE_CHAIN '+JSON.stringify(chain));
                    });''')
                    page=await context.new_page()
                    page.on('console',lambda m:print(m.text,flush=True) if 'JAVA_CAUSE_CHAIN' in m.text else None)
                    async def click(q):
                        await page.mouse.click(q['x'],q['y'])
                        await page.evaluate("id=>document.documentElement.setAttribute('data-mindustry-cdp-click-ack',id)",q['id'])
                    await page.expose_function('__firefoxNativeClick',click)
                    label=('webgl1-' if gl1 else 'webgl2-')+option
                    try:
                        await page.goto('http://127.0.0.1:8146/index.html?lang=ru&savedPlanet=serpulo')
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=120000)
                        await page.evaluate("""()=>new MutationObserver(ms=>{for(const m of ms){if(m.attributeName==='data-mindustry-cdp-click-request')window.__firefoxNativeClick(JSON.parse(document.documentElement.getAttribute(m.attributeName)));}}).observe(document.documentElement,{attributes:true,attributeFilter:['data-mindustry-cdp-click-request']})""")
                        script=(ROOT/'scripts/browser-save-clock-go.js').read_text().split(' const lostExpected')[0]
                        script+=''' await click('planet-hit-launch');
                        await wait(()=>v('campaign-state')==='playing'&&v('campaign-save')==='valid','fresh world');
                        if(typeof globalThis.__mindustryQaSnapshot!=='function')throw Error('read-only QA probe missing');
                        await wait(()=>{const s=__mindustryQaSnapshot();return s.ready&&s.playing&&!s.paused&&s.campaign&&s.campaignActive;},'actual campaign playing, not paused');
                        const start=__mindustryQaSnapshot();
                        await wait(()=>{const s=__mindustryQaSnapshot();return s.playing&&!s.paused&&s.campaign&&s.campaignActive&&s.tick>start.tick&&BigInt(s.updateId)>BigInt(start.updateId)+90n;},'actual live production tick/updateId');return 'fresh native Go PASS';})()'''
                        await page.evaluate(script)
                        assert await page.get_attribute('html','data-mindustry-web')!='error'
                        print(label+' fresh native Go and actual live simulation PASS '+json.dumps(await page.evaluate('__mindustryQaSnapshot()')),flush=True)
                    except Exception as e:
                        problems.append(label)
                        detail=await page.evaluate('()=>({attrs:Object.fromEntries(Array.from(document.documentElement.attributes).filter(a=>/error|failure|phase/.test(a.name)).map(a=>[a.name,a.value])),chain:window.__javaErrorChain,state:typeof __mindustryQaSnapshot==="function"?__mindustryQaSnapshot():null})')
                        print('FAIL '+label+' '+str(e)+' '+json.dumps(detail),flush=True)
                    finally:
                        await page.screenshot(path=str(ROOT/f'work/fresh-{label}.png'))
                        (ROOT/f'work/fresh-{label}.html').write_text(await page.content())
                        await context.close();await browser.close()
            if problems:raise RuntimeError('Renderer cases failed: '+', '.join(problems))
    finally:
        if server is not None:server.shutdown()
        if owns_probe:subprocess.run([sys.executable,str(ROOT/'scripts/prepare-qa-probe.py'),str(runtime),'--restore'],check=True)

asyncio.run(main())

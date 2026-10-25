"""Exercise the production DOM text-dialog script with trusted browser input."""
import asyncio,json,os,re,shutil,threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]

async def main():
    source=(ROOT/'web-runtime/src/main/java/mindustry/web/BrowserTextInput.java').read_text()
    match=re.search(r'@JSBody\(params=\{([^}]+)\},script="""\n([\s\S]*?)\n\s*"""\)',source)
    assert match,'Production TextInput JSBody anchor changed'
    parameters=json.loads('['+match[1]+']');script=match[2]
    html='<!doctype html><meta charset="utf-8"><canvas id="mindustry-canvas" tabindex="0"></canvas>'
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8')
            if self.path.startswith('/denied'):self.send_header('Permissions-Policy','clipboard-write=()')
            self.end_headers();self.wfile.write(html.encode())
        def log_message(self,*args):pass
    server=ThreadingHTTPServer(('127.0.0.1',8168),Handler)
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        async with async_playwright() as pw:
            executable=os.environ.get('MINDUSTRY_CHROME_EXECUTABLE') or shutil.which('google-chrome')
            browser=await pw.chromium.launch(executable_path=executable,headless=True,args=['--no-sandbox'])
            try:
                for mobile in [False,True]:
                    mode='mobile' if mobile else 'desktop'
                    context=await browser.new_context(viewport={'width':390 if mobile else 1280,'height':844 if mobile else 720},is_mobile=mobile,has_touch=mobile)
                    page=await context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
                    async def setup(path='/allowed'):
                        await page.goto('http://127.0.0.1:8168'+path)
                        await page.evaluate('''q=>{
                            window.textResults=[];window.gameKeys=0;window.gamePointers=0;window.releases=[];
                            addEventListener('keydown',()=>window.gameKeys++);
                            addEventListener('pointerdown',()=>window.gamePointers++);
                            document.querySelector('canvas').__mindustryReleaseInput=reason=>window.releases.push(reason);
                            const fn=new Function(...q.parameters,q.script);
                            window.openNativeText=function(options){
                                const v=Object.assign({title:'Rename',message:'Value',value:'',multiline:false,numeric:false,allowEmpty:false,maxLength:64,copyMode:false,acceptLabel:'Accept',cancelLabel:'Cancel',copiedLabel:'Copied'},options);
                                const args=q.parameters.map(name=>name==='result'?(ok,text)=>textResults.push({ok,text}):v[name]);
                                fn(...args);
                            };
                        }''',{'parameters':parameters,'script':script})
                    async def open(options=None):
                        await page.evaluate('o=>openNativeText(o)',options or {})
                        await page.wait_for_selector('#mindustry-text-input')
                        return page.locator('#mindustry-text-input').locator('input,textarea')
                    await setup()
                    field=await open();assert await page.get_by_role('button',name='Accept',exact=True).is_disabled()
                    unicode='Схема 日本語 🛰️'
                    await field.fill(unicode)
                    await field.dispatch_event('keydown',{'key':'Enter','isComposing':True,'bubbles':True})
                    assert await page.locator('#mindustry-text-input').count()==1
                    await field.press('Enter')
                    assert await page.evaluate('textResults')==[{'ok':True,'text':unicode}]
                    assert await page.evaluate('gameKeys')==0
                    assert await page.evaluate('releases')==['text-input','text-input-close']
                    # Regression: an old setTimeout focus/select could fire
                    # after the first typed value and replace it on Enter.
                    field=await open({'value':'previous'})
                    await field.fill('fresh text')
                    await page.wait_for_timeout(100)
                    caret=await field.evaluate('(e)=>[e.selectionStart,e.selectionEnd]')
                    assert caret==[len('fresh text')]*2,(mode,'deferred selection erased input',caret)
                    await field.press('Escape')
                    assert (await page.evaluate('textResults')).pop()=={'ok':False,'text':'fresh text'}
                    field=await open({'numeric':True,'maxLength':4,'value':'bad'})
                    assert await field.input_value()==''
                    await field.fill('a12b345');assert await field.input_value()=='1234'
                    await page.get_by_role('button',name='Accept',exact=True).click()
                    assert (await page.evaluate('textResults')).pop()=={'ok':True,'text':'1234'}
                    field=await open({'multiline':True,'maxLength':12})
                    await field.fill('line one');await field.press('Enter')
                    # Wait for the browser's native textarea input event to
                    # finish before the Ctrl+Enter confirmation gesture.
                    await page.wait_for_timeout(150)
                    entered=await field.input_value()
                    assert entered=='line one\n',(mode,'mobile multiline Enter unexpected',repr(entered))
                    assert await page.locator('#mindustry-text-input').count()==1
                    await field.press('Control+Enter')
                    assert (await page.evaluate('textResults')).pop()=={'ok':True,'text':'line one\n'}
                    field=await open({'value':'unchanged'});await field.press('Escape')
                    assert (await page.evaluate('textResults')).pop()=={'ok':False,'text':'unchanged'}
                    assert await page.locator('#mindustry-text-input').count()==0
                    field=await open({'allowEmpty':True});await page.get_by_role('button',name='Cancel',exact=True).click()
                    assert (await page.evaluate('textResults')).pop()=={'ok':False,'text':''}
                    assert not errors,errors
                    assert await page.evaluate('gameKeys')==0
                    assert await page.evaluate('gamePointers')==0
                    await setup('/denied')
                    field=await open({'copyMode':True,'value':'bXNjaA Unicode copy','acceptLabel':'Copy'})
                    assert await field.get_attribute('readonly') is not None
                    await page.get_by_role('button',name='Copy',exact=True).click()
                    await page.wait_for_function("document.querySelector('#mindustry-text-input button').textContent==='Copy (Ctrl+C)'")
                    assert await field.input_value()=='bXNjaA Unicode copy'
                    assert await field.evaluate('(e)=>e.selectionEnd-e.selectionStart')==len('bXNjaA Unicode copy')
                    await page.get_by_role('button',name='Cancel',exact=True).click()
                    assert await page.locator('#mindustry-text-input').count()==0
                    print(mode+': native Unicode/numeric/maxLength/multiline Enter-CtrlEnter/Escape/cancel, game-input isolation and denied-clipboard selection PASS',flush=True)
                    await context.close()
            finally:await browser.close()
    finally:server.shutdown()

asyncio.run(main())

"""Real packaged SFX, a stalled HTTP body, browser abort and same-URL retry."""
import asyncio,shutil,threading
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
from playwright.async_api import async_playwright
ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'

async def main():
    sounds=list((STAGE/'assets/sounds').rglob('*.ogg'))
    assert sounds,'Packaged SFX are missing'
    sound=min(sounds,key=lambda p:(p.stem!='press',p.as_posix()))
    data=sound.read_bytes();release=threading.Event()
    class Handler(SimpleHTTPRequestHandler):
        stalled=False
        def do_GET(self):
            if urlsplit(self.path).path!='/test-stall.ogg': return super().do_GET()
            self.send_response(200);self.send_header('Content-Type','audio/ogg');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.flush()
            if not Handler.stalled:
                Handler.stalled=True;release.wait(30)
            try:self.wfile.write(data)
            except OSError:pass # Expected when AbortController closes the first request.
    fixture=STAGE/'test-native-sfx.html'
    fixture.write_text('''<!doctype html><html><body><button id="start" onclick="window.clicks=(window.clicks||0)+1">Audio</button>
    <script src="browser-audio.js"></script><script>
    document.documentElement.setAttribute('data-mindustry-input-mode',new URLSearchParams(location.search).get('mode'));
    __mindustryAudioApi.install();
    </script></body></html>''')
    server=ThreadingHTTPServer(('127.0.0.1',8155),partial(Handler,directory=str(STAGE)))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        async with async_playwright() as pw:
            browser=await pw.chromium.launch(executable_path=shutil.which('google-chrome'),headless=True,args=['--no-sandbox'])
            try:
                for mobile in [False,True]:
                    mode='mobile' if mobile else 'desktop'
                    context=await browser.new_context(viewport={'width':390 if mobile else 1280,'height':844 if mobile else 720},is_mobile=mobile,has_touch=mobile)
                    page=await context.new_page();await page.goto('http://127.0.0.1:8155/test-native-sfx.html?mode='+mode)
                    if mobile:await page.locator('#start').tap()
                    else:await page.locator('#start').click()
                    await page.wait_for_function('__mindustryAudio.unlocked && __mindustryAudio.ctx.state==="running"')
                    id=await page.evaluate('url=>__mindustryAudioApi.playSound(url,0.7,1,0,true)',sound.relative_to(STAGE).as_posix())
                    await page.wait_for_function('id=>__mindustryAudio.voices.get(id)?.started',arg=id)
                    await page.evaluate('''id=>{
                        const s=__mindustryAudio,v=s.voices.get(id),a=s.ctx.createAnalyser();a.fftSize=256;
                        v.gain.disconnect();v.gain.connect(a);a.connect(s.ctx.destination);s.sfxAnalyser=a;
                    }''',id)
                    await page.wait_for_function('''()=>{const a=new Float32Array(256);__mindustryAudio.sfxAnalyser.getFloatTimeDomainData(a);return a.some(v=>Math.abs(v)>0.0001);}''')
                    await page.evaluate('id=>__mindustryAudioApi.stopVoice(id)',id)
                    assert not await page.evaluate('id=>__mindustryAudioApi.voicePlaying(id)',id)
                    if not mobile:
                        failed=await page.evaluate('()=>__mindustryAudioApi.playSound("test-stall.ogg",1,1,0,true)')
                        await page.wait_for_function('__mindustryAudio.decodeRequests.has("test-stall.ogg")')
                        await page.locator('#start').click();assert await page.evaluate('window.clicks')>=2
                        await page.wait_for_function('id=>!__mindustryAudioApi.voicePlaying(id) && !__mindustryAudio.buffers.has("test-stall.ogg")',arg=failed,timeout=25000)
                        assert not await page.evaluate('__mindustryAudio.decodeRequests.has("test-stall.ogg")')
                        retry=await page.evaluate('()=>__mindustryAudioApi.playSound("test-stall.ogg",1,1,0,true)')
                        await page.wait_for_function('id=>__mindustryAudio.voices.get(id)?.started',arg=retry)
                        assert await page.evaluate('__mindustryAudioApi.soundLength("test-stall.ogg")')>0
                        await page.evaluate('()=>{__mindustryAudio.retryCache=__mindustryAudio.buffers.get("test-stall.ogg");}')
                        release.set();await page.wait_for_timeout(180)
                        assert await page.evaluate('__mindustryAudio.buffers.get("test-stall.ogg")===__mindustryAudio.retryCache')
                        await page.evaluate('id=>__mindustryAudioApi.stopVoice(id)',retry)
                        print('desktop: stalled real HTTP body, responsive input, 20s abort, channel release and same-URL retry PASS',flush=True)
                    print(mode+': real packaged SFX decode, nonzero audio samples and explicit stop PASS',flush=True)
                    await context.close()
            finally:await browser.close()
    finally:
        release.set();server.shutdown();fixture.unlink(missing_ok=True)
asyncio.run(main())

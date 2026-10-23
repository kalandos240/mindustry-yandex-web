"""Trusted input and real packaged OGG playback; no fake Audio/AudioContext."""
import asyncio,json,shutil,threading
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'

async def main():
    track=min(STAGE.glob('assets/music/*.ogg'),key=lambda p:p.stat().st_size).relative_to(STAGE).as_posix()
    fixture=STAGE/'test-native-audio.html'
    fixture.write_text('''<!doctype html><html><head><meta charset="utf-8"></head><body><button id="start">Start audio</button>
    <script src="browser-audio.js"></script><script>
    document.documentElement.setAttribute('data-mindustry-input-mode',new URLSearchParams(location.search).get('mode'));
    __mindustryAudioApi.install();
    </script></body></html>''')
    server=ThreadingHTTPServer(('127.0.0.1',8154),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    try:
        async with async_playwright() as pw:
            browser=await pw.chromium.launch(executable_path=shutil.which('google-chrome'),headless=True,args=['--no-sandbox'])
            try:
                for mobile in [False,True]:
                    mode='mobile' if mobile else 'desktop'
                    context=await browser.new_context(viewport={'width':390 if mobile else 1280,'height':844 if mobile else 720},is_mobile=mobile,has_touch=mobile)
                    page=await context.new_page()
                    await page.goto('http://127.0.0.1:8154/test-native-audio.html?mode='+mode)
                    async def gesture():
                        if mobile: await page.locator('#start').tap()
                        else: await page.locator('#start').click()
                        await page.wait_for_function('__mindustryAudio.unlocked && __mindustryAudio.ctx.state==="running"')
                    async def play(id):
                        await page.evaluate('q=>__mindustryAudioApi.musicPlay(q.id,q.url,0.6,1,0,true)',{'id':id,'url':track})
                    async def advancing(id):
                        await page.wait_for_function('id=>{const e=__mindustryAudio.music.get(id).element;return !e.paused && e.currentTime>0.05;}',arg=id,timeout=30000)
                    await gesture();await play(901);await advancing(901)
                    await page.evaluate('''()=>{
                        const s=__mindustryAudio,e=s.music.get(901).element;
                        const source=s.ctx.createMediaElementSource(e),a=s.ctx.createAnalyser();
                        a.fftSize=256;source.connect(a);a.connect(s.ctx.destination);s.testAnalyser=a;
                    }''')
                    await page.wait_for_function('''()=>{const s=__mindustryAudio,a=new Float32Array(256);s.testAnalyser.getFloatTimeDomainData(a);return a.some(v=>Math.abs(v)>0.0001);}''',timeout=30000)
                    await page.evaluate('()=>{__mindustryAudioApi.platformPause(true);__mindustryAudioApi.platformPause(true);}')
                    await page.wait_for_function('__mindustryAudio.ctx.state==="suspended"')
                    time=await page.evaluate('__mindustryAudio.music.get(901).element.currentTime')
                    await page.wait_for_timeout(180)
                    assert abs(await page.evaluate('__mindustryAudio.music.get(901).element.currentTime')-time)<0.04
                    await page.evaluate('()=>{__mindustryAudioApi.musicVolume(901,0.37);__mindustryAudioApi.platformPause(false);}')
                    await advancing(901)
                    assert await page.evaluate('__mindustryAudio.music.get(901).element.volume')==0.37
                    await page.wait_for_function('''()=>{const a=new Float32Array(256);__mindustryAudio.testAnalyser.getFloatTimeDomainData(a);return a.some(v=>Math.abs(v)>0.0001);}''')
                    await page.evaluate('()=>{__mindustryAudioApi.platformPause(true);__mindustryAudioApi.musicPause(901,true);__mindustryAudioApi.platformPause(false);}')
                    await page.wait_for_timeout(180)
                    assert await page.evaluate('__mindustryAudio.music.get(901).element.paused')
                    await page.evaluate('()=>__mindustryAudioApi.platformPause(true)')
                    await play(902)
                    assert await page.evaluate('__mindustryAudio.music.get(902).element.paused')
                    await page.evaluate('()=>__mindustryAudioApi.platformPause(false)');await advancing(902)
                    await page.evaluate('q=>{__mindustryAudioApi.musicPlay(903,q,0.5,1,0,true);__mindustryAudioApi.musicStop(903);}',track)
                    await page.wait_for_timeout(180)
                    await page.evaluate('()=>{__mindustryAudioApi.platformPause(true);__mindustryAudioApi.platformPause(false);}')
                    await page.wait_for_timeout(180)
                    assert await page.evaluate('__mindustryAudio.music.get(903).element.paused')
                    assert await page.evaluate('__mindustryAudio.music.get(903).element.currentTime')==0
                    await page.evaluate('()=>{__mindustryAudioApi.dispose();__mindustryAudioApi.install();}')
                    assert not await page.evaluate('__mindustryAudio.unlocked')
                    await gesture();await play(904);await advancing(904)
                    print(mode+': real OGG samples, trusted unlock, repeated pause/resume, explicit pause/stop, queued music, volume and reinstall PASS',flush=True)
                    await context.close()
            finally: await browser.close()
    finally:
        server.shutdown();fixture.unlink(missing_ok=True)

asyncio.run(main())

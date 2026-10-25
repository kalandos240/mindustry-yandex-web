"""Exercise the true packaged Mindustry game with a Playgama Bridge host stub.

Do not alter the compiled JavaScript. A page-level fake host supplies only
external Bridge callbacks, as the actual Playgama QA sandbox would.
"""
import asyncio
import json
import os
import shutil
import threading
import zipfile
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
ZIP=ROOT/'work/playgama/mindustry-playgama.zip'
STAGE=ROOT/'work/playgama/browser-root/game'
OUT=ROOT/'work/playgama/browser-evidence'
BRIDGE_MOCK=r"""
window.__playgamaTest={messages:[],callbacks:{},storage:{},adShows:0};
window.bridge={
 EVENT_NAME:{
  INTERSTITIAL_STATE_CHANGED:'interstitial_state_changed',
  PAUSE_STATE_CHANGED:'pause_state_changed',
  AUDIO_STATE_CHANGED:'audio_state_changed'
 },
 initialize:async()=>true,
 platform:{
  language:new URLSearchParams(location.search).get('lang')||'en',
  isAudioEnabled:true,
  on(type,callback){__playgamaTest.callbacks[type]=callback},
  sendMessage(message){__playgamaTest.messages.push(message);return Promise.resolve()}
 },
 device:{type:navigator.maxTouchPoints>0?'mobile':'desktop'},
 storage:{
  async get(keys){return keys.map(key=>__playgamaTest.storage[key]||null)},
  async set(keys,values){keys.forEach((key,i)=>{__playgamaTest.storage[key]=values[i]})}
 },
 advertisement:{
  isInterstitialSupported:true,isBannerSupported:false,
  on(type,callback){__playgamaTest.callbacks[type]=callback},
  showInterstitial(placement){
   if(!['menu_return','pause'].includes(placement))throw Error('Wrong ad placement: '+placement);
   __playgamaTest.adShows++;
   setTimeout(()=>__playgamaTest.callbacks.interstitial_state_changed('opened'),20);
   setTimeout(()=>__playgamaTest.callbacks.interstitial_state_changed('closed'),800);
  }
 }
};
"""

async def main():
 assert ZIP.is_file()
 STAGE.mkdir(parents=True,exist_ok=True)
 OUT.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(ZIP) as z:z.extractall(STAGE)
 server=ThreadingHTTPServer(('127.0.0.1',8158),
    partial(SimpleHTTPRequestHandler,directory=str(STAGE.parent)))
 threading.Thread(target=server.serve_forever,daemon=True).start()
 try:
  async with async_playwright() as p:
   browser=await p.chromium.launch(
    executable_path=shutil.which('google-chrome') or shutil.which('chromium'),
    headless=True,args=['--no-sandbox','--disable-dev-shm-usage','--use-angle=swiftshader',
                        '--enable-unsafe-swiftshader'])
   try:
    for mobile,sector in [(False,'groundZero'),(True,'onset')]:
     device='mobile' if mobile else 'desktop'
     context=await browser.new_context(
       viewport={'width':390 if mobile else 1280,'height':844 if mobile else 800},
       is_mobile=mobile,has_touch=mobile)
     page=await context.new_page()
     await page.add_init_script(BRIDGE_MOCK)
     errors=[]
     page.on('pageerror',lambda error:errors.append(str(error)))
     try:
      await page.goto('http://127.0.0.1:8158/game/index.html?lang=en&mindustryCampaignSmoke='+sector,
        timeout=180000)
      await page.wait_for_function(
        """() => {const root=document.documentElement;return (
           root.getAttribute('data-playgama-sdk')==='ready'
           &&root.getAttribute('data-mindustry-web')==='ready'
           &&root.getAttribute('data-mindustry-campaign-state')==='playing'
           &&root.getAttribute('data-playgama-game-ready')==='sent'
          );
        }""",timeout=30000)
      state=await page.evaluate("""() => ({
       playgama:document.documentElement.getAttribute('data-playgama-sdk'),
       ready:document.documentElement.getAttribute('data-playgama-game-ready'),
       sector:document.documentElement.getAttribute('data-mindustry-campaign-preset'),
       playing:document.documentElement.getAttribute('data-mindustry-campaign-state'),
       map:document.documentElement.getAttribute('data-mindustry-campaign-map-path'),
       bridgeMessages:__playgamaTest.messages,
       gameBundle:document.querySelector('script[src="mindustry.js"]')?.src
      })""")
      assert state['sector']==sector,state
      assert state['playing']=='playing',state
      assert 'game_ready' in state['bridgeMessages'],state
      assert not errors,errors
      # Test Bridge platform silence/host pause against real browser game frame.
      await page.evaluate("() => __playgamaTest.callbacks.audio_state_changed(false)")
      await page.wait_for_function(
        "() => document.documentElement.getAttribute('data-mindustry-platform-pause')==='paused'")
      await page.evaluate("() => __playgamaTest.callbacks.audio_state_changed(true)")
      await page.wait_for_function(
        "() => document.documentElement.getAttribute('data-mindustry-platform-pause')==='running'")
      assert not errors,errors
      # A full native-to-Bridge interstitial from the actual running campaign.
      # Use the original, shipped platform wrapper and real browser sound
      # state; the external host alone is stubbed for CI.
      # Wait-for-first-ad is a real runtime policy (60s). Emulate elapsed
      # session time ONLY in QA, never disable this delay in shipped files.
      await page.evaluate("() => __mindustryPlaygamaBridge.state.sessionStartedAt=Date.now()-62000")
      if not mobile:
       # A real trusted Escape opens Mindustry's STOCK Pause dialog.
       # No synthetic Arc callbacks or game-state mutation are permitted.
       await page.locator('#mindustry-canvas').click(position={'x':100,'y':100})
       await page.keyboard.press('Escape')
       await page.wait_for_function(
         "() => document.documentElement.getAttribute('data-mindustry-pause-dialog-visible')==='yes'",
         timeout=20000)
      else:
       # Test the original Java->JS fullscreen ad call on mobile; the
       # natural mobile pause is exercised by native UI regression.
       await page.evaluate("() => __mindustryYandex.showFullscreenAdv()")
      await page.wait_for_function(
        """() => __playgamaTest.adShows===1
          && document.documentElement.getAttribute('data-playgama-interstitial-state')==='opened'
          && document.documentElement.getAttribute('data-mindustry-platform-pause')==='paused'""",
        timeout=15000)
      await page.wait_for_function(
        """() => document.documentElement.getAttribute('data-playgama-interstitial-state')==='closed'
          && document.documentElement.getAttribute('data-mindustry-platform-pause')==='running'
          && document.documentElement.getAttribute('data-yandex-game-state')==='"""+('playing' if mobile else 'ready')+"""'""",
        timeout=15000)
      assert await page.evaluate("() => __playgamaTest.adShows") == 1
      assert not errors,errors
      state['ads'] = 'Playgama interstitial opened, paused, closed and resumed real gameplay'
      state['adTrigger'] = 'real native Escape Pause' if not mobile else 'native Java bridge'
      state['adShows'] = 1
      # Native Java music/sound remains governed by BrowserAudio; do not
      # accept a browser app that crashes during the platform pause.
      (OUT/(device+'.json')).write_text(json.dumps(state,indent=2))
      await page.screenshot(path=str(OUT/(device+'.png')))
      print(device+': nested-path Playgama SDK, game_ready, campaign sector, audio pause/resume PASS',flush=True)
     except Exception:
      diag=await page.evaluate("""() => ({
        attrs:Object.fromEntries([...document.documentElement.attributes].map(a=>[a.name,a.value])
          .filter(([n])=>/mindustry|playgama|yandex/.test(n))),
        bridge:window.__playgamaTest?{
          messages:__playgamaTest.messages,keys:Object.keys(__playgamaTest.storage),
          callbacks:Object.keys(__playgamaTest.callbacks)
        }:null,
        sdk:window.__mindustryPlaygamaBridge?{
          ready:__mindustryPlaygamaBridge.state.ready,
          paused:__mindustryPlaygamaBridge.state.paused,
          hasFacade:!!window.__mindustryPlaygamaSdk
        }:null
      })""")
      (OUT/(device+'-failure.json')).write_text(
        json.dumps({'errors':errors,'url':page.url,'diag':diag},indent=2))
      await page.screenshot(path=str(OUT/(device+'-failure.png')))
      raise
     finally:await context.close()
   finally:await browser.close()
 finally:server.shutdown()

if __name__=='__main__':asyncio.run(main())

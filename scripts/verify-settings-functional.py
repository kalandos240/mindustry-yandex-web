#!/usr/bin/env python3
"""Trusted native settings matrix with actual renderer, HUD, timing and reload checks.

Install prepare-qa-probe.py then prepare-ui-control-probe.py on a test-only stage.
This test changes preferences through native widgets, never through the probes.
The output distinguishes a stored-value check from a directly observed effect.
"""
import argparse,asyncio,json,math,shutil,threading,time
from pathlib import Path
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
ABSENT={'savecreate','backgroundpause','chatopacity','playerchat','console','showperformance','modcrashdisable',
        'communityservers','keyboard','vsync','fullscreen','landscape'}
RENDER_BOOL={'effects':('enableEffects',False),'animatedwater':('animateWater',False),
 'animatedshields':('animateShields',False),'drawlight':('drawLight',False),'blockstatus':('drawStatus',False),
 'hidedisplays':('drawDisplays',True),'pixelate':('pixelate',False),'drawhitboxes':('webDrawHitboxes',False)}
RENDER_NUMBER={'unitlaseropacity':('unitLaserOpacity',.01),'lasersopacity':('laserOpacity',.01),
 'bridgeopacity':('bridgeOpacity',.01),'bloomintensity':('webBloomIntensity',1),'bloomblur':('webBloomBlur',1)}
HUD={'minimap':'web-hud-minimap-root','coreitems':'web-hud-core-items-root','fps':'web-hud-fps',
     'mouseposition':'web-hud-mouse-position'}
EXPECTED={
 'Game':{'saveinterval','blockreplace','conveyorpathfinding','hints','doubletapmine','commandmodehold'},
 'Graphics':{'uiEdgePadding','uiscale','screenshake','bloomintensity','bloomblur','fpscap','lasersopacity',
  'unitlaseropacity','bridgeopacity','maxmagnificationmultiplierpercent','minmagnificationmultiplierpercent',
  'effects','atmosphere','drawlight','destroyedblocks','blockstatus','minimap','smoothcamera','position','fps',
  'playerindicators','showpings','showotherbuildplans','indicators','showweather','animatedwater','bloom',
  'pixelate','linear','skipcoreanimation','hidedisplays'},
 'Sound':{'alwaysmusic','musicvol','sfxvol','ambientvol'},'Developer':{'drawhitboxes'}}

async def main(args):
    out=ROOT/'work/settings-functional';out.mkdir(parents=True,exist_ok=True)
    server=ThreadingHTTPServer(('127.0.0.1',args.port),partial(SimpleHTTPRequestHandler,directory=str(ROOT/'web-runtime/build/web')))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    ledger=[];failures=[]
    try:
        async with async_playwright() as pw:
            options={'headless':args.browser!='firefox'}
            if args.browser=='chromium':
                options.update(executable_path=shutil.which('google-chrome') or shutil.which('chromium'),args=['--no-sandbox'])
            else:options['firefox_user_prefs']={'webgl.force-enabled':True,'gfx.webrender.software':True}
            browser=await getattr(pw,args.browser).launch(**options)
            context=await browser.new_context(viewport={'width':390 if args.mobile else 1280,'height':844 if args.mobile else 800},
                has_touch=args.mobile,**({'is_mobile':args.mobile} if args.browser=='chromium' else {}))
            page=await context.new_page();page.on('pageerror',lambda error:failures.append(str(error)))
            async def wait(fn,label,timeout=18):
                deadline=time.monotonic()+timeout
                while time.monotonic()<deadline:
                    if failures:raise AssertionError('JavaScript error at '+label+': '+failures[-1])
                    if await page.get_attribute('html','data-mindustry-web')=='error':
                        raise AssertionError('Game error at '+label+': '+str(await page.evaluate('window.__mindustryErrorReport')))
                    if await fn():return
                    await page.wait_for_timeout(80)
                raise AssertionError('Timeout: '+label)
            async def controls():
                data=await page.evaluate('()=>__mindustryUiControls({includeOffscreen:true,includeDisabled:true})')
                assert not data.get('truncated'),data
                return data['controls']
            async def locate(key=None,name=None,label=None):
                for attempt in range(38):
                    all_controls=await controls()
                    found=[c for c in all_controls if (c.get('setting') or {}).get('key')==key] if key else \
                        [c for c in all_controls if c.get('name')==name] if name else \
                        [c for c in all_controls if c['kind']=='button' and c.get('label','').strip()==label]
                    found=[c for c in found if c.get('enabled') and c['kind'] in ['button','slider']]
                    if found:
                        c=next((c for c in found if c['onScreen']),found[0])
                        if c['onScreen'] and c['visibleCssRect']['right']-c['visibleCssRect']['left']>5 and c['visibleCssRect']['bottom']-c['visibleCssRect']['top']>5:return c
                        panes=[p for p in all_controls if p['kind']=='scrollpane' and p['onScreen'] and p.get('maxY',0)>1]
                        ancestors=[p for p in panes if str(c.get('path','')).startswith(str(p.get('path',''))+'.')]
                        panes=ancestors or panes
                        if panes:
                            pane=max(panes,key=lambda p:len(str(p.get('path',''))));r=pane['visibleCssRect'];target=c['cssRect']
                            await page.mouse.move((r['left']+r['right'])/2,(r['top']+r['bottom'])/2)
                            await page.mouse.wheel(0,-220 if target['top']<r['top'] else 220)
                    await page.wait_for_timeout(120)
                raise AssertionError('Native control not reachable: '+str(key or name or label))
            async def click(**find):
                c=await locate(**find)
                # Stock ScrollPane has kinetic/smoothed positioning. A control
                # can become onScreen while its row is still moving under the
                # pointer. Wait for layout stabilization, then re-sample the
                # live actor instead of trusting a stale hit rectangle.
                await page.wait_for_timeout(420)
                c=await locate(**find)
                # The reported actor center may lie outside its ScrollPane's
                # clipping rectangle when only a strip is visible. Use the
                # actual visible hit rectangle, not the un-clipped center.
                r=c['visibleCssRect']
                x=(r['left']+r['right'])/2
                y=(r['top']+r['bottom'])/2
                if args.mobile:await page.touchscreen.tap(x,y)
                else:await page.mouse.click(x,y)
                await page.wait_for_timeout(100)
            async def settings_page(category):
                items=await controls()
                keys={(c.get('setting') or {}).get('key') for c in items}
                if category=='root':
                    labels={c.get('label','').strip() for c in items if c['kind']=='button'}
                    return not any(keys) and {'Game','Graphics','Sound'}<=labels
                return {'game':'saveinterval','graphics':'fpscap','sound':'musicvol','dev':'drawhitboxes'}[category] in keys
            async def value(key):return await page.evaluate('k=>__mindustryQaSetting(k)',key)
            async def read(target,path):return await page.evaluate('q=>__mindustryQaRead(q[0],q[1])',[target,path])
            async def state():return await page.evaluate('__mindustryQaSnapshot()')
            async def assert_paused(label):
                s=await state();assert s['paused'] and s['campaignActive'] and not s['menu'],(label,s)
                assert abs(s['tick']-frozen['tick'])<.001,(label,'paused simulation advanced',frozen,s)
            async def slider(key,target):
                c=await locate(key=key);fraction=(target-c['min'])/(c['max']-c['min']);low=0.;high=1.
                # Stock drawables reserve knob/background padding. Seek through the
                # actual pointer/value feedback rather than assigning a native value.
                for attempt in range(12):
                    c=await locate(key=key);r=c['cssRect'];width=r['right']-r['left']
                    x=r['left']+max(1,min(width-1,width*fraction));y=(r['top']+r['bottom'])/2
                    await page.mouse.move(c['x'],c['y']);await page.mouse.down();await page.mouse.move(x,y,steps=8);await page.mouse.up()
                    frame=float(await read('graphics','frameId'))
                    async def processed():return float(await read('graphics','frameId'))>frame
                    await wait(processed,'native slider input '+key)
                    actual=await value(key)
                    if abs(actual-target)<=max(.01,c.get('step',1)*.51):return
                    if actual<target:low=fraction
                    else:high=fraction
                    fraction=(low+high)/2
                raise AssertionError('Native slider could not reach '+key+'='+str(target)+' actual '+str(actual))
            async def semantic(key,expected):
                if key in RENDER_BOOL:
                    field,invert=RENDER_BOOL[key];wanted=not expected if invert else expected
                    await wait(lambda:compare_read('renderer',field,int(wanted)),'actual renderer '+key)
                    return 'renderer.'+field
                if key in RENDER_NUMBER:
                    field,scale=RENDER_NUMBER[key]
                    await wait(lambda:compare_read('renderer',field,expected*scale),'actual renderer '+key)
                    return 'renderer.'+field
                if key=='bloom':
                    async def bloom_state():return bool(await read('renderer','bloom'))==bool(expected)
                    await wait(bloom_state,'actual bloom allocation/disposal');return 'renderer.bloom allocation/disposal'
                if key in HUD:
                    async def visible():
                        actor=await page.evaluate('n=>__mindustryUiActor(n)',HUD[key]);return actor is not None and actor['visible']==bool(expected)
                    await wait(visible,'actual HUD '+key);return 'Scene actor '+HUD[key]
                if key=='position' and not args.mobile:
                    async def position():
                        a=await page.evaluate('__mindustryUiActor("web-hud-position")');return a and a['visible']==bool(expected)
                    await wait(position,'player coordinate visibility');return 'Scene player coordinates'
                if key=='sfxvol':
                    await wait(lambda:compare_read('audio','sfxVolume',expected/100),'actual SFX gain');return 'BrowserAudio.sfxVolume'
                if key=='musicvol':
                    async def music_gain():
                        volumes=await page.evaluate('()=>Array.from(__mindustryAudio.music.values()).filter(e=>!e.element.paused).map(e=>e.element.volume)')
                        # Original SoundControl is allowed to fade music all
                        # the way to silence while native Pause/Settings modal
                        # covers the game. A zero-volume PLAYING HTMLAudioElement
                        # is not proof the Music Volume slider is broken.
                        return (not volumes or max(volumes)<.001) if expected==0 else all(v<=expected/100+.02 for v in volumes)
                    try:
                        await wait(music_gain,'actual SoundControl music gain ceiling while paused')
                    except AssertionError:
                        details=await page.evaluate("""()=>({
                            music:Array.from(__mindustryAudio.music.values()).map(e=>({
                                id:e.id,paused:e.element.paused,volume:e.element.volume,readyState:e.element.readyState,
                                networkState:e.element.networkState,error:e.element.error?.message||null,
                                currentTime:e.element.currentTime,pendingPlay:e.pendingPlay,
                                resumeAfterPlatform:e.resumeAfterPlatform,playRequest:e.playRequest,
                                src:e.element.currentSrc.split('/').pop()
                            })),
                            platform:document.documentElement.getAttribute('data-mindustry-audio-platform'),
                            lifecycle:document.documentElement.getAttribute('data-mindustry-game-state'),
                            visible:document.visibilityState,
                            activation:navigator.userActivation?.hasBeenActive
                        })""")
                        print('MUSIC_GAIN_DIAGNOSTIC '+json.dumps({'key':key,'expected':expected,'setting':await value(key),'details':details}),flush=True)
                        await page.screenshot(path=str(out/f'{args.browser}-{"mobile" if args.mobile else "desktop"}-music-failed.png'))
                        raise
                    return 'active HTMLAudioElement gain capped by preference; stock fade to silence permitted during Pause'
                if key=='fpscap':
                    limit=0 if expected>240 else max(10,expected)
                    await wait(lambda:attr_equals('data-mindustry-frame-limit',str(limit)),'actual frame limiter');return 'scheduler limit'
                if key=='uiEdgePadding':
                    product=await page.evaluate('__mindustryQaUiScale()')
                    field='marginTop' if args.mobile else 'marginLeft'
                    await wait(lambda:compare_read('scene',field,int(product*expected)),'actual Scene padding');return 'Scene.'+field
                if key in ['maxmagnificationmultiplierpercent','minmagnificationmultiplierpercent']:
                    pref='maxzoomingamemultiplier' if key.startswith('max') else 'minzoomingamemultiplier'
                    async def zoom():return abs(await value(pref)-expected/100)<.001
                    await wait(zoom,'actual zoom multiplier');return 'renderer zoom multiplier preference'
                return 'stored + paused frame safety; context-specific consumer not directly exercised'
            async def compare_read(target,path,expected):return abs(float(await read(target,path))-float(expected))<.015
            async def attr_equals(name,expected):return await page.get_attribute('html',name)==expected
            async def open_settings():
                nonlocal frozen
                await page.keyboard.press('Escape');await wait(lambda:paused_state(),'native Pause')
                await click(name='web-pause-settings')
                await wait(lambda:settings_page('root'),'Settings root')
                frozen=await state()
            async def paused_state():return (await state())['paused']
            async def close_settings():
                await page.keyboard.press('Escape');await wait(lambda:settings_page('root'),'Settings Back')
                await page.keyboard.press('Escape');await page.wait_for_timeout(400)
                await click(name='web-pause-resume');await wait(lambda:playing_state(),'native Resume')
            async def playing_state():
                s=await state();return s['playing'] and s['campaignActive'] and not s['menu']
            async def settle_store():
                frame=float(await read('graphics','frameId'))
                async def later():return float(await read('graphics','frameId'))>=frame+2
                await wait(later,'settings autosave after released pointer')
                await page.evaluate('()=>__mindustryStorage.flush()')
            frozen={}
            url=f'http://127.0.0.1:{args.port}/index.html?lang=en&mindustryCampaignSmoke=groundZero'+('&mindustryMobile=1' if args.mobile else '')
            try:
                await page.goto(url)
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready' && typeof __mindustryQaRead==='function' && typeof __mindustryUiControls==='function'",timeout=180000)
                await wait(playing_state,'campaign starts');await page.wait_for_timeout(800)
                await open_settings()
                roster=set()
                for category in ['Game','Graphics','Sound','Developer']:
                    # Pinned Mindustry's actual English root caption is
                    # "Developer Options" (bundle key settings.dev), not
                    # "Developer". Keep category identity separate from label.
                    await click(label='Developer Options' if category=='Developer' else category)
                    await wait(lambda:settings_page({'Game':'game','Graphics':'graphics','Sound':'sound','Developer':'dev'}[category]),'category '+category)
                    entries=await controls();keys=[]
                    for c in entries:
                        key=(c.get('setting') or {}).get('key')
                        if key and c['kind'] in ['button','slider'] and key not in keys:keys.append(key)
                    assert keys,category+' has no inventoried native preferences'
                    assert not set(keys)&ABSENT,('unsupported controls exposed',set(keys)&ABSENT)
                    expected=EXPECTED[category].copy()
                    if category=='Game':expected.update({'autotarget'} if args.mobile else {'buildautopause','distinctcontrolgroups'})
                    if category=='Graphics' and not args.mobile:expected.update({'coreitems','detach-camera','mouseposition'})
                    optional={'animatedshields','macnotch'} if category=='Graphics' else set()
                    assert expected<=set(keys),(category,'missing settings in native inventory',expected-set(keys))
                    assert set(keys)<=expected|optional,(category,'new setting requires coverage review',set(keys)-expected-optional)
                    roster.update(keys)
                    music_original=None
                    if category=='Sound':
                        music_original=await value('alwaysmusic')
                        if not music_original:await click(key='alwaysmusic')
                        async def music_started():
                            return await page.evaluate('()=>Array.from(__mindustryAudio.music.values()).some(e=>!e.element.paused && e.element.volume>0)')
                        await wait(music_started,'actual background music for gain preview')
                    for key in keys:
                        before=await value(key);c=await locate(key=key);effects=[]
                        if isinstance(before,bool):
                            await click(key=key)
                            async def toggled():return await value(key)==(not before)
                            try:
                                await wait(toggled,'native checkbox '+key)
                            except AssertionError:
                                current=await locate(key=key)
                                print('CHECKBOX_DIAGNOSTIC '+json.dumps({'key':key,'before':before,'after':await value(key),'control':current},ensure_ascii=False),flush=True)
                                await page.screenshot(path=str(out/f'{args.browser}-{"mobile" if args.mobile else "desktop"}-checkbox-failed.png'))
                                raise
                            effects.append(await semantic(key,not before));await assert_paused(key)
                            await click(key=key)
                            async def restored():return await value(key)==before
                            await wait(restored,'restore '+key);await semantic(key,before)
                        elif c['kind']=='slider':
                            for target in [c['min'],c['max']]:
                                await slider(key,target);effects.append(await semantic(key,await value(key)));await assert_paused(key)
                            await slider(key,before);await semantic(key,await value(key))
                        else:raise AssertionError('Unsupported native preference type '+key+': '+repr(before))
                        ledger.append({'key':key,'category':category,'before':before,'after':await value(key),'effects':sorted(set(effects))})
                    if category=='Sound' and music_original is False:await click(key='alwaysmusic')
                    await page.keyboard.press('Escape')
                    await wait(lambda:settings_page('root'),'category Back '+category)
                assert {'fpscap','uiscale','musicvol','sfxvol','ambientvol','minimap','drawhitboxes'}<=roster,roster
                # Exercise the save-interval consumer, not only its stored number.
                await click(label='Game');await slider('saveinterval',10);await page.keyboard.press('Escape')
                await wait(lambda:settings_page('root'),'Game interval Back')
                await click(label='Graphics');await slider('fpscap',10)
                before_save=await page.evaluate("()=>Object.fromEntries(__mindustryStorage.paths().filter(p=>p.endsWith('.msav')).map(p=>[p,Array.from(__mindustryStorage.get(p))]))")
                assert before_save
                # Measure actual scheduler frames and real simulation time at the lowest cap.
                await close_settings();start=await state();f0=await read('graphics','frameId');t0=time.monotonic()
                await page.wait_for_timeout(4200);elapsed=time.monotonic()-t0;end=await state();f1=await read('graphics','frameId')
                fps=(float(f1)-float(f0))/elapsed;tick_rate=(end['tick']-start['tick'])/elapsed
                assert 7<=fps<=13,('actual cap',fps)
                assert 50<=tick_rate<=75,('low cap slowed simulation',tick_rate)
                ledger.append({'key':'fpscap','effect':'actual frames and simulation','fps':fps,'ticksPerSecond':tick_rate})
                async def autosaved():
                    return await page.evaluate("()=>Object.fromEntries(__mindustryStorage.paths().filter(p=>p.endsWith('.msav')).map(p=>[p,Array.from(__mindustryStorage.get(p))]))")!=before_save
                await wait(autosaved,'10-second automatic world checkpoint',timeout=18)
                await page.evaluate('()=>__mindustryStorage.flush()')
                ledger.append({'key':'saveinterval','effect':'automatic MSAV changed after native10-second setting'})
                await open_settings();await click(label='Game');await slider('saveinterval',60);await page.keyboard.press('Escape')
                await wait(lambda:settings_page('root'),'restore save interval Back')
                await click(label='Graphics');await slider('fpscap',240)
                # A real cold reload must apply the UI scale, and the unscaled Keep button
                # must remain usable even when native layout sizes change.
                desired=75 if args.mobile else 125
                await slider('uiscale',desired);await settle_store()
                await page.goto(url.replace('&mindustryCampaignSmoke=groundZero',''))
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready' && typeof __mindustryQaUiScale==='function'",timeout=180000)
                # An interrupted confirmation must remain pending on the next boot.
                await page.get_by_role('button',name='Keep',exact=True).wait_for(state='visible')
                await page.reload()
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=180000)
                await page.get_by_role('button',name='Keep',exact=True).click()
                assert abs(await page.evaluate('__mindustryQaUiScale()')-desired/100)<.001
                await page.evaluate('()=>__mindustryStorage.flush()');await page.reload()
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=180000)
                assert await page.locator('#mindustry-ui-scale-confirm').count()==0
                assert abs(await page.evaluate('__mindustryQaUiScale()')-desired/100)<.001
                ledger.append({'key':'uiscale','effect':'cold Scl product + trusted Keep + persisted second reload','percent':desired})
                # The browser-native rollback must also work when scaled Arc controls
                # would be too large to reach. It changes only UI preferences.
                await click(label='Settings')
                await wait(lambda:settings_page('root'),'menu Settings after scaled reload')
                await click(label='Graphics');await slider('uiscale',150);await settle_store()
                await page.reload();await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=180000)
                saved=await page.evaluate("()=>Object.fromEntries(__mindustryStorage.paths().filter(p=>p.endsWith('.msav')).map(p=>[p,Array.from(__mindustryStorage.get(p))]))")
                async with page.expect_navigation(wait_until='load'):
                    await page.get_by_role('button',name='Reset to 100%',exact=True).click()
                await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=180000)
                assert abs(await page.evaluate('__mindustryQaUiScale()')-1)<.001
                after=await page.evaluate("()=>Object.fromEntries(__mindustryStorage.paths().filter(p=>p.endsWith('.msav')).map(p=>[p,Array.from(__mindustryStorage.get(p))]))")
                assert saved==after,'Scale rollback changed the campaign save'
                ledger.append({'key':'uiscale','effect':'trusted Reset100 + durable reload + exact MSAV preservation'})
                assert not failures,failures
                await page.screenshot(path=str(out/f'{args.browser}-{"mobile" if args.mobile else "desktop"}.png'))
                result={'browser':args.browser,'mobile':args.mobile,'status':'passed','roster':sorted(roster),'checks':ledger,
                    'semanticLimitations':[x['key'] for x in ledger if any('not directly exercised' in e for e in x.get('effects',[]))]}
                (out/f'{args.browser}-{"mobile" if args.mobile else "desktop"}.json').write_text(json.dumps(result,indent=2))
                print(json.dumps(result),flush=True)
            finally:
                if failures:print('PAGEERRORS '+json.dumps(failures),flush=True)
                await context.close();await browser.close()
    finally:server.shutdown()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--browser',choices=['chromium','firefox'],default='chromium');parser.add_argument('--mobile',action='store_true');parser.add_argument('--port',type=int,default=8159)
    asyncio.run(main(parser.parse_args()))

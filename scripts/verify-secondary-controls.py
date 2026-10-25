#!/usr/bin/env python3
"""Native Database/rule/config actions with semantic postconditions and finite ledgers.

The probes only read state. All tested actions use browser pointer/keyboard input.
Config worlds are isolated, opt-in fixtures; no configure callback is invoked by setup.
"""
import argparse,asyncio,json,re,shutil,subprocess,threading,time
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
from playwright.async_api import async_playwright

ROOT=Path(__file__).resolve().parents[1]
STAGE=ROOT/'web-runtime/build/web'

def clean(text):return re.sub(r'\[[^\]]*\]','',str(text or '')).strip()

class Native:
    def __init__(self,page,mobile,ledger):
        self.page=page;self.mobile=mobile;self.ledger=ledger;self.errors=[]
        page.on('pageerror',lambda e:self.errors.append(str(e)))
    async def wait(self,fn,label,timeout=22):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            if self.errors:raise AssertionError(label+': '+self.errors[-1])
            if await self.page.get_attribute('html','data-mindustry-web')=='error':
                raise AssertionError(label+': game error '+str(await self.page.evaluate('window.__mindustryErrorReport')))
            if await fn():return
            await self.page.wait_for_timeout(80)
        raise AssertionError('Timed out: '+label)
    async def inventory(self):
        data=await self.page.evaluate('()=>__mindustryUiControls({includeOffscreen:true,includeDisabled:true})')
        assert not data.get('truncated'),data
        return data
    async def locate(self,predicate,label,required=True):
        for _ in range(44):
            data=await self.inventory();found=[c for c in data['controls'] if predicate(c)]
            if found:
                enabled=[c for c in found if c.get('enabled')]
                if not enabled:
                    if not required:return None
                    raise AssertionError('Disabled action: '+label)
                c=next((c for c in enabled if c['onScreen']),enabled[0])
                if c['onScreen']:return c
                panes=[p for p in data['controls'] if p['kind']=='scrollpane' and p['onScreen'] and p.get('maxY',0)>0]
                ancestors=[p for p in panes if c['path'].startswith(p['path']+'.')]
                if ancestors:
                    p=max(ancestors,key=lambda p:len(p['path']));r=p['visibleCssRect'];target=c['cssRect']
                    await self.page.mouse.move((r['left']+r['right'])/2,(r['top']+r['bottom'])/2)
                    await self.page.mouse.wheel(0,-220 if target['top']<r['top'] else 220)
            await self.page.wait_for_timeout(100)
        if not required:return None
        raise AssertionError('Unreachable action: '+label)
    async def pointer(self,c):
        if self.mobile:await self.page.touchscreen.tap(c['x'],c['y'])
        else:await self.page.mouse.click(c['x'],c['y'])
        await self.page.wait_for_timeout(100)
    async def click(self,predicate,label):
        # ScrollPane scrolling and modal fade transitions can leave a stale
        # actor center between inventory and pointer dispatch. Re-read the
        # live actor after the original Arc layout settles.
        c=await self.locate(predicate,label)
        await self.page.wait_for_timeout(380)
        c=await self.locate(predicate,label)
        await self.pointer(c)
        return c
    async def name(self,name):return await self.click(lambda c:c.get('name')==name,name)
    async def button(self,label):return await self.click(lambda c:c['kind']=='button' and clean(c['label'])==label,label)
    async def text(self,c,text):
        await self.pointer(c)
        native=self.page.locator('#mindustry-text-input')
        if self.mobile and await native.count():
            # On mobile the original Arc TextField invokes the browser-native
            # text editor. Typing alone edits only the transient DOM field:
            # OK is the trusted user action that commits to Rules.ruleSearch.
            await native.locator('input, textarea').first.fill(str(text))
            await native.get_by_role('button',name='OK',exact=True).click()
        else:
            await self.page.keyboard.press('Control+A')
            await self.page.keyboard.press('Backspace')
            if text:await self.page.keyboard.type(str(text),delay=2)
        await self.page.wait_for_timeout(120)
    async def state(self):return await self.page.evaluate('__mindustryQaSnapshot()')
    async def dialog(self,path):return await self.page.evaluate('p=>__mindustryUiDialogRead(p)',path)
    async def config(self,path):return await self.page.evaluate('p=>__mindustryUiConfigRead(p)',path)
    async def attr(self,key):return await self.page.get_attribute('html','data-mindustry-'+key)
    async def modal(self,suffix):
        return str(((await self.inventory()).get('modal') or {}).get('type','')).endswith(suffix)
    async def stable(self):
        before=(await self.state())['updateId'];await self.wait(lambda:self.advanced(before),'live simulation')
    async def advanced(self,before):
        after=await self.state();return after['playing'] and int(after['updateId'])>int(before)
    def record(self,group,action,result,**extra):
        self.ledger.append({'group':group,'action':action,'status':'passed','effect':result,**extra})

async def database(ui):
    # Original mobile home layout has a direct Database tile. Desktop uses
    # the grouped Database submenu; clicking the latter on mobile is a
    # nonexistent control rather than a game failure.
    if not ui.mobile:await ui.name('web-main-database-group')
    await ui.name('web-main-database')
    await ui.wait(lambda:ui.modal('DatabaseDialog'),'Database opens')
    initial=await ui.page.evaluate('__mindustryQaRead("ui","database.tab.name")')
    records=(await ui.inventory())['controls']
    tabs=[c for c in records if c['kind']=='button' and c.get('checked') is not None and not clean(c['label'])]
    visited=[]
    for tab in tabs:
        live=await ui.locate(lambda c:c['id']==tab['id'],'Database tab')
        await ui.pointer(live)
        value=await ui.page.evaluate('__mindustryQaRead("ui","database.tab.name")')
        visited.append(value);ui.record('database','tab '+value,'actual DatabaseDialog.tab='+value)
        # Rebuild replaces tab actors; continue by index in the new actual row.
        records=(await ui.inventory())['controls']
        fresh=[c for c in records if c['kind']=='button' and c.get('checked') is not None and not clean(c['label'])]
        for i,item in enumerate(tabs):
            if i<len(fresh):item.update(fresh[i])
    assert len(set(visited))==len(tabs) and initial in visited,(initial,visited)
    # All tab exposes each normal unlocked icon. Locked hover-only icons are excluded.
    all_tab=await ui.locate(lambda c:c['kind']=='button' and 'eyeSmall' in c.get('icons',[]),'All database content')
    await ui.pointer(all_tab)
    targets=[]
    for c in (await ui.inventory())['controls']:
        for content in c.get('content',[]):
            if c.get('enabled') and c['kind']=='clickable' and not c.get('lockedIndicator'):
                if (content['type'],content['name']) not in [(t['type'],t['name']) for t in targets]:targets.append(content)
    assert targets,'No actionable unlocked Database contents'
    for target in targets:
        predicate=lambda c,t=target:c.get('enabled') and c['kind']=='clickable' and any(q['type']==t['type'] and q['name']==t['name'] for q in c.get('content',[]))
        await ui.click(predicate,'Database '+target['name'])
        await ui.wait(lambda:ui.modal('ContentInfoDialog'),'Content detail '+target['name'])
        text=await ui.page.evaluate('__mindustryUiText().text')
        assert clean(target['localizedName']) in clean(text),(target,text)
        ui.record('database','detail '+target['type']+'/'+target['name'],'actual ContentInfoDialog title and stats text',title=target['localizedName'])
        await ui.button('Back');await ui.wait(lambda:ui.modal('DatabaseDialog'),'detail Back')
    search=await ui.locate(lambda c:c['kind']=='textfield','Database search')
    await ui.text(search,'qa-no-database-result-928413')
    await ui.wait(lambda:database_search_is(ui,'qa-no-database-result-928413'),'Database search callback')
    text=await ui.page.evaluate('__mindustryUiText().text')
    assert 'none found' in text.lower(),text
    await ui.text(search,'');await ui.wait(lambda:database_search_is(ui,''),'Database search clear')
    ui.record('database','search/clear','actual search string, empty results and restored contents')
    await ui.button('Back');await ui.wait(lambda:database_closed(ui),'Database Back')
    assert (await ui.state())['menu']

async def database_search_is(ui,value):return await ui.page.evaluate('__mindustryQaRead("ui","database.search.text")')==value
async def database_closed(ui):return not await ui.modal('DatabaseDialog')

async def open_custom(ui,map_name='Archipelago'):
    if not ui.mobile:await ui.name('web-main-play')
    await ui.name('web-main-custom')
    await ui.wait(lambda:ui.modal('CustomGameDialog'),'Custom catalog')
    await ui.click(lambda c:c['kind'] in ['button','clickable'] and map_name.lower() in clean(c['label']).lower(),'map '+map_name)
    await ui.wait(lambda:ui.modal('MapPlayDialog'),'Map Play dialog')

async def rules(ui):
    await open_custom(ui)
    for mode,expected in [('Sandbox',True),('Survival',False)]:
        await ui.button(mode)
        assert await ui.dialog('selectedGamemode.name')==mode.lower()
        assert bool(await ui.dialog('rules.infiniteResources'))==expected
        ui.record('custom-mode',mode,'actual selectedGamemode and infiniteResources')
    await ui.button('?');await ui.wait(lambda:help_open(ui),'mode help')
    text=await ui.page.evaluate('__mindustryUiText().text')
    assert 'Survival' in text and 'Sandbox' in text,text
    await ui.button('OK');await ui.wait(lambda:ui.modal('MapPlayDialog'),'close mode help')
    ui.record('custom-mode','help/OK','actual modes help text and return to map')
    # Stock v159.7's actual button reads "Customize Rules"; the old
    # exact label "Customize" incorrectly failed despite the action existing.
    await ui.click(lambda c:c['kind']=='button' and clean(c['label']).lower().startswith('customize'),'Customize Rules')
    await ui.wait(lambda:ui.modal('CustomRulesDialog'),'Customize rules')
    search=await ui.locate(lambda c:c['kind']=='textfield' and c['onScreen'],'Rules search')
    search_id=search['id']
    spec=json.loads((ROOT/'scripts/custom-rules-action-spec.json').read_text())
    for entry in spec:
        await ui.text(await ui.locate(lambda c:c['id']==search_id,'Rules search'),entry['label'])
        await ui.wait(lambda:rule_search_is(ui,entry['label']),'filtered rule '+entry['field'])
        before=await ui.dialog('rules.'+entry['field'])
        if entry['kind']=='boolean':
            find=lambda c:c['kind']=='button' and clean(c['label'])==clean(entry['label'])
            control=await ui.locate(find,entry['field'],required=False)
            if not control:
                ui.ledger.append({'group':'custom-rule','action':entry['field'],'status':'unavailable','reason':'not enabled/exposed under Survival prerequisites'})
                continue
            await ui.pointer(control);await ui.wait(lambda:rule_is(ui,entry['field'],not bool(before)),entry['field']+' changes')
            await ui.click(find,'restore '+entry['field']);await ui.wait(lambda:rule_is(ui,entry['field'],before),'restore '+entry['field'])
        else:
            find=lambda c:c['kind']=='textfield' and c['id']!=search_id and clean(entry['label']) in clean(c.get('nearbyLabels',''))
            control=await ui.locate(find,entry['field'],required=False)
            if not control:
                ui.ledger.append({'group':'custom-rule','action':entry['field'],'status':'unavailable','reason':'numeric field disabled/not exposed under Survival prerequisites'})
                continue
            scale=entry.get('scale',1);desired=.35 if entry['field']=='deconstructRefundMultiplier' else 2 if entry['kind']=='integer' or scale!=1 else 1.25
            await ui.text(control,str(desired));await ui.wait(lambda:rule_close(ui,entry['field'],desired*scale),entry['field']+' numeric change')
            restored=int(before/scale) if entry['kind']=='integer' else before/scale
            await ui.text(await ui.locate(find,entry['field']),str(restored));await ui.wait(lambda:rule_close(ui,entry['field'],before),'restore '+entry['field'])
        ui.record('custom-rule',entry['field'],'native '+entry['kind']+' changes actual temporary Rules field and restores it')
    # The X must reset the visible search widget as well as ruleSearch/results.
    await ui.text(await ui.locate(lambda c:c['id']==search_id,'Rules search'),'wave')
    await ui.click(lambda c:c['kind']=='button' and 'cancel' in c.get('icons',[]),'Rules search X')
    await ui.wait(lambda:rule_search_is(ui,''),'search X clears ruleSearch')
    search=await ui.locate(lambda c:c['id']==search_id,'Rules search')
    assert search.get('text')=='',search
    ui.record('custom-rule','search X','both visible text and actual filter are empty')
    # Persist a real edited rule into a running local map, not just the dialog.
    label=next(s['label'] for s in spec if s['field']=='waveTimer')
    await ui.text(search,label)
    if await ui.dialog('rules.waveTimer'):await ui.click(lambda c:c['kind']=='button' and clean(c['label'])==clean(label),'disable wave timer')
    await ui.button('Back');await ui.wait(lambda:ui.modal('MapPlayDialog'),'Rules Back')
    assert bool(await ui.dialog('rules.waveTimer')) is False
    await ui.button('Play');await ui.wait(lambda:local_playing(ui),'customized local map')
    assert bool(await ui.page.evaluate('__mindustryQaRead("state","rules.waveTimer")')) is False
    await ui.stable();ui.record('custom-rule','launch edited rules','actual playing Rules.waveTimer=false and live update counter')

async def rule_search_is(ui,value):return await ui.dialog('ruleSearch')==value.strip().lower()
async def rule_is(ui,key,value):return await ui.dialog('rules.'+key)==value
async def rule_close(ui,key,value):return abs(float(await ui.dialog('rules.'+key))-float(value))<max(.001,abs(float(value))*.00001)
async def help_open(ui):return ((await ui.inventory()).get('modal') or {}).get('title') not in ['',None,'Archipelago']
async def local_playing(ui):
    s=await ui.state();return s['playing'] and not s['campaign']

async def config_family(ui,family):
    await ui.wait(lambda:config_ready(ui,family),'fixture '+family)
    await ui.stable()
    async def tile():
        r=await ui.page.locator('#mindustry-canvas').bounding_box()
        x=float(await ui.attr('native-config-x'));y=float(await ui.attr('native-config-y'))
        if ui.mobile:await ui.page.touchscreen.tap(r['x']+x,r['y']+r['height']-y)
        else:await ui.page.mouse.click(r['x']+x,r['y']+r['height']-y)
        await ui.page.wait_for_timeout(120)
    before=await ui.attr('native-config-value')
    await tile()
    if family=='switch':
        await ui.wait(lambda:config_changed(ui,before),'native switch toggle')
        await tile();await ui.wait(lambda:config_value(ui,before),'native switch restored')
        ui.record('building-config',family,'world tap toggles actual Building.config Boolean and restores')
        return
    await ui.wait(lambda:config_open(ui),'native config table')
    if family=='micro-processor':
        old=await ui.config('code');links=await ui.config('links.size')
        await ui.click(lambda c:c['kind']=='button' and 'pencil' in c.get('icons',[]),'processor pencil')
        dialog=ui.page.locator('#mindustry-text-input');await dialog.wait_for()
        field=dialog.locator('textarea');await field.fill('set qa 7\nend')
        await dialog.get_by_role('button',name='Cancel',exact=True).click()
        assert await ui.config('code')==old
        await ui.click(lambda c:c['kind']=='button' and 'pencil' in c.get('icons',[]),'processor pencil apply')
        await dialog.wait_for();await dialog.locator('textarea').fill('set qa 7\nend')
        await dialog.get_by_role('button',name='OK',exact=True).click()
        await ui.wait(lambda:processor_code_is(ui,'set qa 7\nend'),'processor stores edited program')
        assert await ui.config('links.size')==links
        assert not await ui.page.evaluate('__mindustryQaRead("state","rules.editor")')
        ui.record('building-config',family,'native pencil multiline cancel/apply stores stock compressed program and preserves links/editor state')
        await ui.stable();return
    kind='liquid' if family=='liquid-source' else 'unit' if 'factory' in family or 'fabricator' in family else 'item'
    choices=[]
    for c in (await ui.inventory())['controls']:
        if c.get('enabled') and c['kind']=='button':
            for q in c.get('content',[]):
                if q['type']==kind and q['name'] not in [p['name'] for p in choices]:choices.append(q)
    assert choices,(family,await ui.inventory())
    plans=[]
    if kind=='unit':
        for i in range(20):
            try:plans.append(await ui.config('block.plans.'+str(i)+'.unit.name'))
            except Exception:break
        assert plans,(family,'factory plans unavailable')
    for choice in choices:
        if await ui.attr('native-config-open')!='true':await tile();await ui.wait(lambda:config_open(ui),'reopen '+family)
        find=lambda c,q=choice:c['kind']=='button' and any(v['type']==q['type'] and v['name']==q['name'] for v in c.get('content',[]))
        prior=await ui.attr('native-config-value')
        await ui.click(find,family+' '+choice['name'])
        # UnitFactory's stock selection button toggles an already-active plan
        # off (-1), unlike item/liquid selectors. Exercise both directions.
        unit_index=str(plans.index(choice['name'])) if kind=='unit' else None
        # Compiled Java String.valueOf(Item/Liquid) is its localizedName
        # ("Copper"/"Water"), not the registry ID ("copper"/"water").
        expected=('-1' if prior==unit_index else unit_index) if kind=='unit' else choice.get('localizedName',choice['name'])
        try:
            await ui.wait(lambda:config_value(ui,expected),family+' selected '+choice['name'])
        except AssertionError:
            print('CONFIG_CHOICE_DIAGNOSTIC '+json.dumps({
                'family':family,'choice':choice,'expected':expected,'before':before,
                'actual':await ui.attr('native-config-value'),
                'actualType':await ui.attr('native-config-type'),
                'configOpen':await ui.attr('native-config-open'),
                'game':await ui.state()
            },ensure_ascii=False),flush=True)
            raise
        ui.record('building-config',family+'/'+choice['name'],'actual Building.config='+expected)
        if kind=='unit':
            if expected=='-1':
                # On desktop stock config closes after toggling a plan off;
                # on touch it can remain open. Clicking the tile unconditionally
                # while already open toggles that dialog CLOSED, which was a
                # false test timeout. Reopen only if the UI actually closed.
                if await ui.attr('native-config-open')!='true':
                    # Touch-close animations can leave the previous config
                    # fragment consuming an immediate canvas tap. Repeat only
                    # genuine canvas clicks until the stock table opens.
                    for retry in range(5):
                        await ui.page.wait_for_timeout(550)
                        if await ui.attr('native-config-open')=='true':break
                        await tile()
                        await ui.page.wait_for_timeout(450)
                        if await ui.attr('native-config-open')=='true':break
                    else:
                        print('FACTORY_REOPEN_DIAGNOSTIC '+json.dumps({
                            'family':family,'choice':choice,'current':await ui.attr('native-config-value'),
                            'configOpen':await ui.attr('native-config-open'),
                            'selection':await ui.attr('native-config-name'),
                            'controls':(await ui.inventory())['controls'][-24:]
                        },ensure_ascii=False),flush=True)
                        raise AssertionError('Native '+family+' dialog cannot reopen after toggling plan off')
                await ui.click(find,'re-enable '+family+' '+choice['name'])
                await ui.wait(lambda:config_value(ui,unit_index),'native plan re-enabled '+choice['name'])
                ui.record('factory-plan',family+'/'+choice['name'],'stock selected plan off then on')
                expected=unit_index
            if await ui.attr('native-config-open')!='true':await tile();await ui.wait(lambda:config_open(ui),'factory command table')
            index=plans.index(choice['name'])
            commands=[]
            for i in range(20):
                prefix='block.plans.'+str(index)+'.unit.commands.'+str(i)
                try:commands.append((await ui.config(prefix+'.name'),await ui.config(prefix+'.icon')))
                except Exception:break
            for command,icon in commands:
                # On mobile the separate bottom HUD "Command" button can
                # share a UnitCommand icon (e.g. enterPayload/units) with a
                # selected factory. It changes player commands, NOT the
                # factory plan. Only controls inside the real native config
                # subtree are valid factory command selectors.
                find_command=lambda c,icon=icon:(c['kind']=='button' and icon in c.get('icons',[])
                    and not c.get('content') and 'web-native-config-root' in c.get('parentNames',[]))
                control=await ui.locate(find_command,'factory command '+command,required=False)
                if control is None:
                    ui.ledger.append({'group':'factory-command','action':family+'/'+choice['name']+'/'+command,'status':'unavailable','reason':'command not exposed under this UnitFactory capability'})
                    continue
                await ui.pointer(control)
                await ui.wait(lambda:factory_command_is(ui,command),'factory command '+command)
                assert await ui.attr('native-config-value')==expected
                ui.record('factory-command',family+'/'+choice['name']+'/'+command,'actual factory.command.name='+command)
    await ui.stable()

async def config_ready(ui,family):return await ui.attr('native-config-name')==family
async def config_open(ui):return await ui.attr('native-config-open')=='true'
async def config_value(ui,value):return await ui.attr('native-config-value')==value
async def config_changed(ui,value):return await ui.attr('native-config-value')!=value
async def processor_code_is(ui,value):return await ui.config('code')==value
async def factory_command_is(ui,value):
    # Stock UnitFactory.command may legitimately be null before a player
    # clicks a command. The read-only TeaVM inspector throws on null.
    # Treat that transient state as "not selected yet", not a game crash.
    try:return await ui.config('command.name')==value
    except Exception as exc:
        if 'UI read target is null: name' in str(exc):return False
        raise

async def main(args):
    output=ROOT/'work/secondary-controls';output.mkdir(parents=True,exist_ok=True)
    owned_qa=not (STAGE/'mindustry.js.qa-probe-original').exists()
    owned_ui=not (STAGE/'mindustry.js.ui-probe-original').exists()
    subprocess.run(['python3',str(ROOT/'scripts/prepare-qa-probe.py')],check=True)
    subprocess.run(['python3',str(ROOT/'scripts/prepare-ui-control-probe.py')],check=True)
    server=ThreadingHTTPServer(('127.0.0.1',args.port),partial(SimpleHTTPRequestHandler,directory=str(STAGE)))
    threading.Thread(target=server.serve_forever,daemon=True).start()
    ledger=[];failures=[]
    try:
        async with async_playwright() as pw:
            options={'headless':args.browser=='chromium'}
            if args.browser=='chromium':options.update(executable_path=shutil.which('google-chrome') or shutil.which('chromium'),args=['--no-sandbox'])
            else:options['firefox_user_prefs']={'webgl.force-enabled':True,'gfx.webrender.software':True}
            browser=await getattr(pw,args.browser).launch(**options)
            cases=[args.case] if args.case!='all' else ['database','rules']+['config:'+s for s in args.families.split(',')]
            try:
                for case in cases:
                    context=await browser.new_context(viewport={'width':390 if args.mobile else 1280,'height':844 if args.mobile else 800},has_touch=args.mobile,**({'is_mobile':args.mobile} if args.browser=='chromium' else {}))
                    page=await context.new_page();ui=Native(page,args.mobile,ledger)
                    query='lang=en&mindustryCustomSmoke=1'+('&mindustryMobile=1' if args.mobile else '')
                    if case.startswith('config:'):
                        family=case.split(':',1)[1];sector='onset' if family in ['tank-fabricator','ship-fabricator','mech-fabricator'] else 'groundZero'
                        query+='&mindustryCampaignSmoke='+sector+'&mindustryConfigFamily='+family
                    try:
                        await page.goto(f'http://127.0.0.1:{args.port}/index.html?'+query)
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'",timeout=150000)
                        if case=='database':await database(ui)
                        elif case=='rules':await rules(ui)
                        else:await config_family(ui,family)
                        print(case+' native semantic actions PASS',flush=True)
                    except Exception as error:
                        failures.append({'case':case,'error':str(error)})
                        print(case+' FAILED '+str(error),flush=True)
                        await page.screenshot(path=str(output/(case.replace(':','-')+'-failed.png')))
                        (output/(case.replace(':','-')+'-failed.json')).write_text(json.dumps(await ui.inventory(),ensure_ascii=False,indent=2))
                    finally:await context.close()
            finally:await browser.close()
    finally:
        server.shutdown()
        if owned_ui:subprocess.run(['python3',str(ROOT/'scripts/prepare-ui-control-probe.py'),'--restore'],check=True)
        if owned_qa:subprocess.run(['python3',str(ROOT/'scripts/prepare-qa-probe.py'),'--restore'],check=True)
        (output/'ledger.json').write_text(json.dumps({'actions':ledger,'failures':failures,'scope':'native contexts explicitly listed; unavailable controls are not passed'},ensure_ascii=False,indent=2))
    if failures:raise AssertionError(json.dumps(failures))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--browser',choices=['chromium','firefox'],default='chromium')
    parser.add_argument('--mobile',action='store_true');parser.add_argument('--port',type=int,default=8194)
    parser.add_argument('--case',default='all');parser.add_argument('--families',default='sorter,inverted-sorter,unloader,item-source,liquid-source,switch,micro-processor,ground-factory,air-factory,naval-factory,tank-fabricator,ship-fabricator,mech-fabricator')
    asyncio.run(main(parser.parse_args()))

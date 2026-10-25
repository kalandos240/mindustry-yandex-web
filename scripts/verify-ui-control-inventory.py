#!/usr/bin/env python3
"""Discover actual Arc controls using trusted browser navigation.

This is an inventory and navigation gate, not a claim that every discovered
control's business function was tested. Destructive controls and setting effects
are explicitly listed as discovered-only for dedicated isolated-profile suites.
"""
import asyncio
import hashlib
import json
import shutil
import subprocess
import threading
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[1]
STAGE = ROOT / 'web-runtime/build/web'
OUT = ROOT / 'work/ui-control-inventory'


async def main():
    OUT.mkdir(parents=True, exist_ok=True)
    runtime = STAGE / 'mindustry.js'
    pristine = runtime.read_bytes()
    installed = []
    server = None
    report = {'format': 'mindustry-ui-control-coverage-v1', 'profiles': [],
              'scope': 'Native navigation and live control discovery; effects are separate gates',
              'destructiveActionsExecuted': False}
    try:
        for script in ['prepare-qa-probe.py', 'prepare-ui-control-probe.py']:
            subprocess.run(['python3', str(ROOT / 'scripts' / script)], check=True)
            installed.append(script)
        server = ThreadingHTTPServer(('127.0.0.1', 8148), partial(SimpleHTTPRequestHandler, directory=str(STAGE)))
        threading.Thread(target=server.serve_forever, daemon=True).start()
        async with async_playwright() as pw:
            browser = await pw.chromium.launch(executable_path=shutil.which('google-chrome'), headless=True,
                args=['--no-sandbox', '--disable-dev-shm-usage', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'])
            try:
                for profile, width, height, mobile in [('desktop', 1280, 800, False), ('narrow', 780, 437, False), ('mobile', 390, 844, True)]:
                    context = await browser.new_context(viewport={'width': width, 'height': height},
                        is_mobile=mobile, has_touch=mobile)
                    page = await context.new_page()
                    touch = await context.new_cdp_session(page) if mobile else None
                    errors = []
                    page.on('pageerror', lambda error: errors.append(str(error)))
                    profile_report = {'profile': profile, 'viewport': [width, height], 'contexts': [],
                                      'nativeActions': [], 'settingKeys': [], 'discoveredOnly': []}
                    report['profiles'].append(profile_report)

                    async def snap(label):
                        await page.wait_for_timeout(300)
                        value = await page.evaluate('()=>__mindustryUiControls({includeOffscreen:true,includeDisabled:true})')
                        assert value['ready'] and not value['truncated'], value
                        assert not errors, errors
                        assert await page.get_attribute('html', 'data-mindustry-web') == 'ready'
                        # Opening a menu/dialog must not create a gameplay session.
                        assert value['game']['menu'] and not value['game']['playing'], value['game']
                        value['context'] = label
                        profile_report['contexts'].append(value)
                        return value

                    async def inventory():
                        return await page.evaluate('()=>__mindustryUiControls({includeOffscreen:true,includeDisabled:true})')

                    async def scroll_pane(pane, amount):
                        controls = (await inventory())['controls']
                        keys = sorted({item['setting']['key'] for item in controls if item.get('setting')})
                        before_values = await page.evaluate('keys=>Object.fromEntries(keys.map(key=>[key,__mindustryQaSetting(key)]))', keys)
                        if mobile:
                            area = pane['visibleCssRect']
                            lower, upper = area['bottom'] - 12, area['top'] + 12
                            # Start in pane padding/row gaps rather than on a
                            # slider track: native slider touchDown may change
                            # its value before the pane recognizes a swipe.
                            active = [item for item in controls if item['onScreen'] and item['enabled'] and
                                item['kind'] in ['button','slider','textfield','clickable','inputtarget']]
                            point = None
                            candidates = [area['left'] + 3, area['right'] - 3, (area['left'] + area['right']) / 2]
                            ys = list(range(int(lower), int(upper), -8)) if amount > 0 else list(range(int(upper), int(lower), 8))
                            for y in ys:
                                for x in candidates:
                                    if all(not (item['visibleCssRect']['left'] <= x <= item['visibleCssRect']['right'] and
                                                item['visibleCssRect']['top'] <= y <= item['visibleCssRect']['bottom']) for item in active):
                                        point = (x, y)
                                        break
                                if point:
                                    break
                            assert point, 'No non-control padding available for native pane swipe'
                            x, y0 = point
                            distance = min(abs(amount), max(0, y0 - upper if amount > 0 else lower - y0))
                            assert distance > 8, 'Scroll surface too clipped for native swipe: ' + str(area)
                            y1 = y0 - distance if amount > 0 else y0 + distance
                            await touch.send('Input.dispatchTouchEvent', {'type': 'touchStart', 'touchPoints': [{'x': x, 'y': y0}]})
                            for step in range(1, 11):
                                await touch.send('Input.dispatchTouchEvent', {'type': 'touchMove', 'touchPoints': [{'x': x, 'y': y0 + (y1 - y0) * step / 10}]})
                                await page.wait_for_timeout(20)
                            await touch.send('Input.dispatchTouchEvent', {'type': 'touchEnd', 'touchPoints': []})
                        else:
                            await page.mouse.move(pane['x'], pane['y'])
                            await page.mouse.wheel(0, amount)
                        await page.wait_for_timeout(350)
                        after_values = await page.evaluate('keys=>Object.fromEntries(keys.map(key=>[key,__mindustryQaSetting(key)]))', keys)
                        assert before_values == after_values, 'Inventory scrolling changed a preference'

                    async def wait_settings_root():
                        # These are actual visible Arc actors, not the optional
                        # Settings-smoke sampler's data-* page marker.
                        await page.wait_for_function("""()=>{
                            const ui=__mindustryUiControls({includeOffscreen:true,includeDisabled:true});
                            const clean=s=>String(s).replace(/\\[[^\\]]*\\]/g,'').trim();
                            const names=new Set(ui.controls.filter(c=>['button','clickable'].includes(c.kind)).map(c=>clean(c.label)));
                            return !!ui.modal && !ui.controls.some(c=>c.setting) &&
                                ['Игра','Графика','Звук'].every(name=>names.has(name));
                        }""")

                    async def escape():
                        await page.keyboard.down('Escape')
                        await page.wait_for_timeout(100)
                        await page.keyboard.up('Escape')
                        await page.wait_for_timeout(450)

                    async def choose(predicate, label):
                        for attempt in range(22):
                            value = await inventory()
                            candidates = [item for item in value['controls'] if predicate(item) and item['enabled'] and item['onScreen']]
                            if candidates:
                                target = candidates[0]
                                if mobile:
                                    await page.touchscreen.tap(target['x'], target['y'])
                                else:
                                    await page.mouse.click(target['x'], target['y'])
                                profile_report['nativeActions'].append({'label': label, 'control': target,
                                    'assertion': 'Expected destination is checked separately'})
                                await page.wait_for_timeout(450)
                                return
                            # Root category lists can be below the fold in narrow portals.
                            panes = [item for item in value['controls'] if item['kind'] == 'scrollpane' and item['onScreen'] and item.get('maxY', 0) > 0]
                            if panes:
                                pane = max(panes, key=lambda item: item.get('maxY', 0))
                                await scroll_pane(pane, 230 if attempt < 14 else -230)
                            else:
                                await page.wait_for_timeout(200)
                        raise AssertionError('Native inventory target missing: ' + label + ' / ' + json.dumps(value, ensure_ascii=False)[:12000])

                    async def close_all():
                        for _ in range(5):
                            value = await inventory()
                            if not value['modal']:
                                return
                            await escape()
                        raise AssertionError('Dialog did not close via trusted Escape: ' + str((await inventory())['modal']))

                    async def named(name):
                        await choose(lambda item: item['name'] == name, name)

                    async def open_settings():
                        await close_all()
                        await named('web-main-settings')
                        await wait_settings_root()

                    try:
                        query = '?lang=ru'
                        await page.goto('http://127.0.0.1:8148/index.html' + query)
                        await page.wait_for_function("document.documentElement.getAttribute('data-mindustry-web')==='ready'", timeout=150000)
                        assert await page.get_attribute('html', 'data-mindustry-input-mode') == ('mobile' if mobile else 'desktop')
                        initial = await snap('home')
                        assert any(item['name'] == 'web-main-settings' for item in initial['controls']), initial
                        await open_settings()
                        root_settings = await snap('settings/root')
                        tabs = [('game', 'Игра'), ('graphics', 'Графика'), ('sound', 'Звук')]
                        for tab, label in tabs:
                            await choose(lambda item, label=label: item['kind'] in ['button', 'clickable'] and item['label'].strip() == label, 'settings/' + tab)
                            expected = {'game': 'hints', 'graphics': 'animatedwater', 'sound': 'musicvol'}[tab]
                            await page.wait_for_function("key=>__mindustryUiControls({includeOffscreen:true,includeDisabled:true}).controls.some(c=>c.setting?.key===key)", arg=expected)
                            value = await snap('settings/' + tab)
                            keys = {item['setting']['key'] for item in value['controls'] if item.get('setting')}
                            assert keys, 'No native setting objects resolved in ' + tab
                            profile_report['settingKeys'].extend(sorted(keys))
                            # Exercise the real scroll surface and refresh coordinates.
                            panes = [item for item in value['controls'] if item['kind'] == 'scrollpane' and item['onScreen'] and item.get('maxY', 0) > 0]
                            if panes:
                                pane = max(panes, key=lambda item: item.get('maxY', 0))
                                before_scroll = pane['scrollY']
                                amount = -250 if before_scroll >= pane['maxY'] - 1 else max(250, pane['maxY'])
                                await scroll_pane(pane, amount)
                                scrolled = await snap('settings/' + tab + '/scrolled')
                                actual_pane = next(item for item in scrolled['controls'] if item['id'] == pane['id'])
                                assert abs(actual_pane['scrollY'] - before_scroll) > .5, 'Native scroll did not move ' + tab
                                profile_report['nativeActions'].append({'label': 'settings/' + tab + '/scroll',
                                    'input': 'touch-swipe' if mobile else 'mouse-wheel', 'before': before_scroll,
                                    'after': actual_pane['scrollY'], 'assertion': 'Actual native ScrollPane amount changed'})
                            await escape()
                            await wait_settings_root()
                            await snap('settings/root-return-' + tab)
                        # Data, languages and keybind controls are inventoried without
                        # destroying data, changing language, or rebinding keys here.
                        profile_report['discoveredOnly'].extend({'reason': 'Dedicated isolated mutation/semantic suite required',
                            'control': item} for item in root_settings['controls'] if item['kind'] in ['button', 'clickable'] and
                            item['label'].strip() not in ['Игра', 'Графика', 'Звук', 'Назад'])
                        await close_all()
                        actions = [('play', 'planets'), ('play', 'custom'), ('play', 'saves'),
                                   ('database-group', 'database'), ('database-group', 'research'),
                                   ('database-group', 'schematics'), ('database-group', 'controls')]
                        for group, action in actions:
                            await close_all()
                            if not mobile:
                                # A previous submenu may remain open; the desired
                                # target itself is the authoritative fresh actor.
                                value = await inventory()
                                if not any(item['name'] == 'web-main-' + action and item['onScreen'] and item['enabled'] for item in value['controls']):
                                    await named('web-main-' + group)
                                    await snap('submenu/' + group)
                            target = 'web-main-play' if mobile and action == 'planets' else 'web-main-' + action
                            await named(target)
                            destination = await snap('dialog/' + action)
                            assert destination['modal'], 'Native ' + action + ' did not open a Dialog'
                            await close_all()
                            await snap('home-after-' + action)
                        profile_report['settingKeys'] = sorted(set(profile_report['settingKeys']))
                        await page.screenshot(path=str(OUT / (profile + '.png')))
                        print(profile + ': actual Arc control inventory, setting key associations and trusted menu/category navigation PASS', flush=True)
                    finally:
                        (OUT / (profile + '.json')).write_text(json.dumps(profile_report, ensure_ascii=False, indent=2))
                        if errors:
                            (OUT / (profile + '-errors.json')).write_text(json.dumps(errors, ensure_ascii=False, indent=2))
                        await context.close()
            finally:
                await browser.close()
    finally:
        if server:
            server.shutdown()
        for script in reversed(installed):
            subprocess.run(['python3', str(ROOT / 'scripts' / script), '--restore'], check=True)
        assert hashlib.sha256(runtime.read_bytes()).digest() == hashlib.sha256(pristine).digest(), 'Read-only probes must restore exact runtime bytes'
        (OUT / 'coverage.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))


asyncio.run(main())

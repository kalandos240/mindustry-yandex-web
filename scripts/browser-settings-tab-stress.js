// CI-only real pointer test for ORIGINAL Mindustry SettingsMenuDialog.
// Uses only Arc Scene hitboxes published by the stock dialog observer.
(async () => {
  const root = document.documentElement;
  const canvas = document.getElementById('mindustry-canvas');
  const attr = key => root.getAttribute('data-mindustry-' + key);
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  const wait = async (fn, label) => {
    for (let i = 0; i < 140; i++) {
      if (attr('web') !== 'ready') throw Error('Mindustry frame error at ' + label);
      if (fn()) return;
      await sleep(100);
    }
    throw Error('Native settings button failed: ' + label +
      ' page=' + attr('settings-native-page') +
      ' modals=' + attr('vanilla-modal-visible'));
  };
  const alive = async label => {
    await wait(() => Number(attr('cpu-sample')) > 0, 'real frames ' + label);
    const previous = Number(attr('cpu-sample'));
    await wait(() => Number(attr('cpu-sample')) > previous,
      'TeaVM full scheduler halted after ' + label);
  };
  const position = async (prefix, name) => {
    // The original Settings category list is in a ScrollPane. A dev/data
    // category can be below the fold on a small Yandex iframe: scroll
    // through the REAL canvas and wait for Scene.hit to expose its actor.
    if (prefix.startsWith('settings-hit-')) {
      const k = 'data-mindustry-' + prefix;
      root.removeAttribute(k + '-x');
      root.removeAttribute(k + '-y');
      const rect = canvas.getBoundingClientRect();
      const px = rect.left + rect.width * .5;
      const py = rect.top + rect.height * .5;
      for (let n = 0; n < 20 && !root.hasAttribute(k + '-x'); n++) {
        if (n >= 2) {
          canvas.dispatchEvent(new WheelEvent('wheel', {
            bubbles:true, cancelable:true, clientX:px, clientY:py,
            deltaY:n < 10 ? 115 : -115
          }));
        }
        await sleep(375);
      }
    }
    await wait(() => root.hasAttribute('data-mindustry-' + prefix + '-x'),
      'Arc hitbox ' + name);
    const rect = canvas.getBoundingClientRect();
    const x = Number(attr(prefix + '-x')), y = Number(attr(prefix + '-y'));
    if (!(x > 0 && y > 0 && x < rect.width && y < rect.height))
      throw Error('Settings ' + name + ' outside Yandex game area: ' + x + ',' + y);
    const p = {x: rect.left + x, y: rect.bottom - y};
    if (document.elementFromPoint(p.x, p.y) !== canvas)
      throw Error('Settings ' + name + ' blocked by advertising DOM overlay');
    return p;
  };
  let count = 0;
  const click = async (prefix, name) => {
    const pos = await position(prefix,name);
    const id = 'native-settings-' + (++count);
    root.setAttribute('data-mindustry-cdp-click-ack', '');
    root.setAttribute('data-mindustry-cdp-click-request', JSON.stringify({id, ...pos}));
    await wait(() => {
      const ack = attr('cdp-click-ack') || '';
      return ack === id || ack.startsWith('ERROR:');
    }, 'native Chrome input acknowledgment for ' + name);
    if (attr('cdp-click-ack') !== id)
      throw Error('Rejected trusted Settings click ' + name);
  };
  const escape = async () => {
    window.dispatchEvent(new KeyboardEvent('keydown',
      {bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
    await sleep(250);
    window.dispatchEvent(new KeyboardEvent('keyup',
      {bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
  };
  const visible = name => (attr('vanilla-modal-visible') || '').split(',').includes(name);

  await click('main-menu-settings', 'Settings main menu');
  await wait(() => visible('settings') && attr('settings-native-page') === 'root',
    'original Settings menu opens');
  await alive('Settings root');
  const mobile = attr('input-mode') === 'mobile';
  const tabs = ['game','graphics','sound','dev'];
  for (const tab of tabs) {
    await click('settings-hit-' + tab, 'Settings/' + tab);
    await wait(() => attr('settings-native-page') === tab,
      'native ' + tab + ' SettingsTable attached');
    await alive('Settings/' + tab);
    if (tab === 'sound') {
      for (const key of ['musicvol','sfxvol','ambientvol']) {
        const previous = attr('settings-value-setting-' + key);
        await click('settings-hit-setting-' + key, 'Sound/' + key);
        await wait(() => attr('settings-value-setting-' + key) !== previous,
          'volume changes ' + key);
        await alive('volume changed ' + key);
      }
    }
    if (tab === 'game') {
      // The actual SettingsTable.CheckSetting for Hints is a stock Button,
      // not an HTML checkbox. Click twice and observe persisted Core.settings.
      const original = attr('settings-native-hints');
      if (original !== 'true' && original !== 'false')
        throw Error('No original Hints configuration found');
      await click('settings-hit-toggle-hints', 'Game/Hints toggle');
      await wait(() => attr('settings-native-hints') !== original,
        'real CheckSetting writes persistent Hints preference');
      await alive('Hints changed');
      await click('settings-hit-toggle-hints', 'Game/Hints restore');
      await wait(() => attr('settings-native-hints') === original,
        'Hints configuration restored using real button');
      await alive('Hints restored');
    }
    await escape();
    await wait(() => attr('settings-native-page') === 'root' && visible('settings'),
      tab + ' Escape returns to category selector');
    await alive(tab + ' Back');
  }
  await click('settings-hit-language', 'Settings/Language');
  await wait(() => visible('language') && visible('settings'),
    'native LanguageDialog opens from settings submenu');
  await alive('LanguageDialog');
  await escape();
  await wait(() => !visible('language') && visible('settings'),
    'LanguageDialog Escape closes only nested modal');
  await alive('LanguageDialog close');
  if (!mobile) {
    await click('settings-hit-controls', 'Settings/Controls');
    await wait(() => visible('controls') && visible('settings'),
      'native KeybindDialog opens from settings submenu');
    await alive('Settings/Controls');
    await escape();
    await wait(() => !visible('controls') && visible('settings'),
      'Controls Escape closes only KeybindDialog');
    await alive('Controls close');
  }
  await escape();
  await wait(() => !visible('settings'),
    'Settings final Escape returns to main menu');
  await alive('Settings final close');
  if (attr('main-menu-page') !== 'home')
    throw Error('Settings navigation changed main menu');
  root.setAttribute('data-mindustry-settings-nested-smoke','passed');
  root.setAttribute('data-mindustry-settings-nested-device',mobile?'mobile':'desktop');
  root.setAttribute('data-mindustry-settings-nested-clicks',String(count));
  return 'Native Settings categories, persisted Hints, Language and Controls passed';
})()

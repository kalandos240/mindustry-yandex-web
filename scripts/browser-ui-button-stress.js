// CI-only browser validation. Executed through chrome-wait-dom's CDP
// after-ready-eval, NOT shipped in the Yandex production game bundle.
// Every open action is a real mouse/touch CDP event through the WebGL canvas.
(async () => {
  const root = document.documentElement;
  const canvas = document.getElementById('mindustry-canvas');
  if (!canvas) throw Error('The actual game canvas is missing');
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  const value = name => root.getAttribute('data-mindustry-' + name);
  const checkAlive = () => {
    if (value('web') !== 'ready') {
      throw Error('TeaVM runtime not ready: ' + value('web') + ' / ' + value('web-error'));
    }
  };
  const wait = async (predicate, description) => {
    for (let i = 0; i < 120; i++) {
      checkAlive();
      if (predicate()) return;
      await sleep(100);
    }
    throw Error('UI callback or transition blocked: ' + description +
      ', modal=' + value('vanilla-modal-visible') +
      ', page=' + value('main-menu-page'));
  };
  // This marker is set by the real BrowserApplication.onAnimationFrame only
  // after 64 successful full Mindustry frames, not by browser requestAnimationFrame.
  const alive = async description => {
    checkAlive();
    await wait(() => Number(value('cpu-sample')) > 0, 'native frame sample: ' + description);
    const before = Number(value('cpu-sample'));
    await wait(() => Number(value('cpu-sample')) > before,
      'Mindustry frame loop stopped after ' + description + ' at ' + before);
    checkAlive();
  };

  await wait(() => value('main-menu-page') === 'home', 'stock home menu');
  const isMobile = value('main-menu-layout') === 'mobile-grid';
  const legalButtons = ['play', 'research', 'database', 'settings',
    'schematics', 'controls', 'saves', 'planets'];
  if (!isMobile) legalButtons.push('custom');
  // Probe the actual original Arc Scene button centers. This deliberately
  // fails if a control is clipped beneath a sticky banner or outside iframe.
  const point = async name => {
    await wait(() => root.hasAttribute('data-mindustry-main-menu-' + name + '-x'),
      name + ' native hitbox');
    const rect = canvas.getBoundingClientRect();
    const sx = Number(value('main-menu-' + name + '-x'));
    const sy = Number(value('main-menu-' + name + '-y'));
    if (!(sx > 0 && sy > 0 && sx < rect.width && sy < rect.height)) {
      throw Error('Native ' + name + ' button outside game area: ' +
        sx + ',' + sy + ' in ' + rect.width + 'x' + rect.height);
    }
    const x = rect.left + sx, y = rect.bottom - sy;
    if (document.elementFromPoint(x, y) !== canvas) {
      throw Error('Native ' + name + ' blocked by portal DOM overlay');
    }
    return { x, y };
  };
  for (const button of legalButtons) await point(button);
  root.setAttribute('data-mindustry-ui-controls-in-bounds', String(legalButtons.length));

  let nextId = 0;
  const click = async name => {
    const p = await point(name);
    const id = 'ui-button-' + (++nextId);
    root.setAttribute('data-mindustry-cdp-click-ack', '');
    root.setAttribute('data-mindustry-cdp-click-request', JSON.stringify({ id, ...p }));
    await wait(() => {
      const ack = value('cdp-click-ack');
      return ack === id || (ack || '').startsWith('ERROR:');
    }, 'real Chrome click on ' + name);
    if (value('cdp-click-ack') !== id) {
      throw Error('CDP rejected native click on ' + name + ': ' + value('cdp-click-ack'));
    }
  };
  const escape = async () => {
    window.dispatchEvent(new KeyboardEvent('keydown', {
      bubbles: true, cancelable: true, code: 'Escape', key: 'Escape'
    }));
    await sleep(250);
    window.dispatchEvent(new KeyboardEvent('keyup', {
      bubbles: true, cancelable: true, code: 'Escape', key: 'Escape'
    }));
  };
  const modal = kind => (value('vanilla-modal-visible') || '').split(',').includes(kind);
  const action = async (name, marker, kind) => {
    await click(name);
    await wait(() => value(marker) === 'yes', name + ' open callback');
    await wait(() => modal(kind), name + ' native dialog visible');
    await alive(name + ' open');
    await escape();
    await wait(() => !modal(kind), name + ' native dialog Escape close');
    await alive(name + ' Escape close');
  };
  const overlay = async (name, marker) => {
    await click(name);
    await wait(() => value(marker) === 'yes', name + ' overlay open');
    await alive(name + ' open');
    await escape();
    await wait(() => value(marker) === 'no', name + ' overlay Escape close');
    await alive(name + ' Escape close');
  };

  const play = async name => {
    await click(name);
    await wait(() => value('main-menu-page') === 'play', name + ' submenu');
    await alive(name + ' submenu');
    // The Back button lives on the second stock native Arc Scene table.
    await click('back');
    await wait(() => value('main-menu-page') === 'home', name + ' back to home');
    await alive(name + ' Back');
  };
  await alive('first menu frame');
  await play('play');
  if (!isMobile) await play('custom');
  await action('settings', 'vanilla-settings-open', 'settings');
  await action('saves', 'vanilla-saves-open', 'saves');
  await action('controls', 'vanilla-controls-open', 'controls');
  await action('schematics', 'vanilla-schematics-open', 'schematics');
  await overlay('research', 'research-open');
  await overlay('database', 'database-open');
  // A second click-after-close cycle detects stale Arc modal touch capture,
  // unhandled key-up states and freezes that initial startup smoke can miss.
  await action('settings', 'vanilla-settings-open', 'settings');
  await play('play');

  // Planet chooser, 3D globe switching and the native first-run confirmation
  // are tested by verify-browser-planet-dialog.sh on both devices; clicking
  // behind that intentional first-run modal here would be a false positive.
  root.setAttribute('data-mindustry-ui-button-stress', 'passed');
  root.setAttribute('data-mindustry-ui-button-stress-device', isMobile ? 'mobile' : 'desktop');
  root.setAttribute('data-mindustry-ui-button-stress-native-clicks', String(nextId));
  return 'All active main menu buttons/overlays tested with native input and real game loop';
})()

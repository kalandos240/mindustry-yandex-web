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
  await wait(() => value('menu-background') === 'original-MenuRenderer',
    'actual original terrain, shadows and flying menu units rendered');
  const firstMenuFrame = Number(value('menu-background-frames'));
  await wait(() => Number(value('menu-background-frames')) > firstMenuFrame,
    'original menu animation keeps rendering');
  if(value('menu-logo') !== 'original-MenuFragment') throw Error('Missing original top-centered menu logo');

  for(const id of ['mindustry-settings-toggle','mindustry-settings-overlay','mindustry-local-mode']){
    if(document.getElementById(id)) throw Error('Legacy DOM widget obscures original menu: '+id);
  }
  if(document.getElementById('mindustry-web-status').textContent.trim())
    throw Error('Debug status obscures the ready game menu');
  const isMobile = value('main-menu-layout') === 'mobile-grid';
  const legalButtons = ['play', 'research', 'database', 'settings',
    'schematics', 'controls', 'saves', 'planets'];
  if(isMobile) legalButtons.splice(legalButtons.indexOf('planets'),1);
  legalButtons.push('custom');
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
    for(const edge of ['left','bottom','right','top']){
      if(!root.hasAttribute('data-mindustry-main-menu-'+name+'-'+edge)) throw Error('Missing real '+name+' '+edge+' bound');
    }
    const left=Number(value('main-menu-'+name+'-left')), bottom=Number(value('main-menu-'+name+'-bottom'));
    const right=Number(value('main-menu-'+name+'-right')), top=Number(value('main-menu-'+name+'-top'));
    if(!(left>=-0.5 && bottom>=-0.5 && right<=rect.width+0.5 && top<=rect.height+0.5 && right>left && top>bottom)){
      throw Error('Native '+name+' rectangle clipped: '+[left,bottom,right,top]+' in '+rect.width+'x'+rect.height);
    }
    const x = rect.left + sx, y = rect.bottom - sy;
    if (document.elementFromPoint(x, y) !== canvas) {
      throw Error('Native ' + name + ' blocked by portal DOM overlay');
    }
    return { x, y };
  };

  let nextId = 0;
  const nativeClick = async name => {
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
  const grouped = ['research', 'database', 'schematics', 'controls'];
  const playChildren = ['planets', 'custom', 'saves'];
  const click = async name => {
    if(!isMobile && grouped.includes(name) && value('main-menu-database-submenu') !== 'open'){
      await nativeClick('database-group');
      await wait(() => value('main-menu-database-submenu') === 'open', 'original Database submenu');
    }
    if(!isMobile && playChildren.includes(name) && value('main-menu-play-submenu') !== 'open'){
      await nativeClick('play');
      await wait(() => value('main-menu-play-submenu') === 'open', 'original Play submenu');
    }
    await nativeClick(name);
  };
  for(const name of legalButtons.filter(name => isMobile || (!grouped.includes(name) && !playChildren.includes(name)))) await point(name);
  if(!isMobile){
    await nativeClick('play');
    await wait(() => value('main-menu-play-submenu') === 'open', 'Play group open');
    for(const name of playChildren) await point(name);
    await nativeClick('play');
    await wait(() => value('main-menu-page') === 'home', 'Play parent toggle close');
    await nativeClick('database-group');
    await wait(() => value('main-menu-database-submenu') === 'open', 'Database group open');
    for(const name of grouped) await point(name);
    await nativeClick('database-group');
    await wait(() => value('main-menu-database-submenu') === 'closed', 'Database toggle close');
    await wait(() => !root.hasAttribute('data-mindustry-main-menu-research-x'), 'hidden submenu hitboxes cleared');
  }
  root.setAttribute('data-mindustry-ui-controls-in-bounds', String(legalButtons.length));
  const escape = async () => {
    window.dispatchEvent(new KeyboardEvent('keydown', {
      bubbles: true, cancelable: true, code: 'Escape', key: 'Escape'
    }));
    await sleep(250);
    window.dispatchEvent(new KeyboardEvent('keyup', {
      bubbles: true, cancelable: true, code: 'Escape', key: 'Escape'
    }));
  };
  if(!isMobile){
    await nativeClick('database-group');
    await wait(() => value('main-menu-database-submenu') === 'open', 'Database reopen');
    await escape();
    await wait(() => value('main-menu-database-submenu') === 'closed', 'Escape closes Database submenu');
  }
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
    await click(!isMobile && name === 'play' ? 'play' : 'back');
    await wait(() => value('main-menu-page') === 'home', name + ' back to home');
    await alive(name + ' Back');
  };
  await alive('first menu frame');
  if(!isMobile) await play('play');
  await action('custom', 'vanilla-custom-open', 'custom');
  await action('custom', 'vanilla-custom-open', 'custom');
  await action('settings', 'vanilla-settings-open', 'settings');
  await action('saves', 'vanilla-saves-open', 'saves');
  await action('controls', 'vanilla-controls-open', 'controls');
  await action('schematics', 'vanilla-schematics-open', 'schematics');
  await overlay('research', 'research-open');
  await action('database', 'vanilla-database-open', 'database');
  // A second click-after-close cycle detects stale Arc modal touch capture,
  // unhandled key-up states and freezes that initial startup smoke can miss.
  await action('settings', 'vanilla-settings-open', 'settings');
  await action('saves', 'vanilla-saves-open', 'saves');
  if(!isMobile) await play('play');

  if(!isMobile){
  await click('play');
  await wait(() => value('main-menu-page') === 'play', 'Play before Escape');
  await escape();
  await wait(() => value('main-menu-page') === 'home', 'Escape closes Play submenu');
  await alive('native Play submenu Escape');
  }

  // Planet chooser, 3D globe switching and the native first-run confirmation
  // are tested by verify-browser-planet-dialog.sh on both devices; clicking
  // behind that intentional first-run modal here would be a false positive.
  root.setAttribute('data-mindustry-ui-button-stress', 'passed');
  root.setAttribute('data-mindustry-ui-button-stress-device', isMobile ? 'mobile' : 'desktop');
  root.setAttribute('data-mindustry-ui-button-stress-native-clicks', String(nextId));
  return 'All active main menu buttons/overlays tested with native input and real game loop';
})()

// CI-only script: browser iframe receives ordinary trusted Chrome clicks.
// Actual Mindustry / Arc callbacks and successful Java scheduler frames are
// authoritative; DOM dispatch or synthetic direct Java method calls do not pass.
(async () => {
  const root = document.documentElement;
  const canvas = document.getElementById('mindustry-canvas');
  const params = new URLSearchParams(location.search);
  const campaign = params.has('mindustryCampaignSmoke');
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  const attr = key => root.getAttribute('data-mindustry-' + key);
  const stateKey = campaign ? 'campaign-state' : 'local-map-state';
  const status = () => {
    if (attr('web') !== 'ready') throw Error('Web main loop crashed: ' + attr('web-error'));
  };
  const wait = async (predicate, label) => {
    for (let i = 0; i < 150; i++) {
      status();
      if (predicate()) return;
      await sleep(100);
    }
    throw Error('Gameplay button blocked: ' + label +
      ', scene=' + attr(stateKey) + ', modal=' + attr('vanilla-modal-visible') +
      ', research=' + attr('research-open'));
  };
  const alive = async label => {
    status();
    await wait(() => Number(attr('cpu-sample')) > 0, 'initial Java frame sample ' + label);
    const n = Number(attr('cpu-sample'));
    await wait(() => Number(attr('cpu-sample')) > n,
      'full TeaVM frame scheduler stalled after ' + label);
  };
  const position = async (prefix, name) => {
    await wait(() => root.hasAttribute('data-mindustry-' + prefix + '-x'), name + ' Arc hitbox');
    const rect = canvas.getBoundingClientRect();
    const x = Number(attr(prefix + '-x')), y = Number(attr(prefix + '-y'));
    if (!(x > 0 && y > 0 && x < rect.width && y < rect.height)) {
      throw Error(name + ' clipped outside Yandex game viewport ' + x + ',' + y +
        ' vs ' + rect.width + 'x' + rect.height);
    }
    const cx = rect.left + x, cy = rect.bottom - y;
    if (document.elementFromPoint(cx, cy) !== canvas) {
      throw Error('Yandex/banner overlay intercepts button ' + name);
    }
    return {x: cx, y: cy};
  };
  let clicks = 0;
  const click = async (prefix, name) => {
    const p = await position(prefix, name);
    const id = 'hud-button-' + (++clicks);
    root.setAttribute('data-mindustry-cdp-click-ack', '');
    root.setAttribute('data-mindustry-cdp-click-request', JSON.stringify({id, ...p}));
    await wait(() => {
      const result = attr('cdp-click-ack') || '';
      return result === id || result.startsWith('ERROR:');
    }, 'trusted Chrome click ' + name);
    if (attr('cdp-click-ack') !== id) throw Error('Trusted click failed: ' + name);
  };
  const key = (type,code,keyName) => window.dispatchEvent(new KeyboardEvent(type, {
    bubbles: true, cancelable: true, repeat: false, code, key: keyName
  }));
  const escape = async () => {
    key('keydown', 'Escape', 'Escape');
    await sleep(240);
    key('keyup', 'Escape', 'Escape');
  };
  const modal = name => (attr('vanilla-modal-visible') || '').split(',').includes(name);
  const pause = async () => {
    await escape();
    await wait(() => attr('space-transition') === 'paused' &&
      (campaign ? attr('campaign-state') === 'paused' :
        attr('local-map-pause-state') === 'paused'), 'Escape to pause');
    await wait(() => attr('pause-dialog-visible') === 'yes', 'pause Arc Scene table');
    await alive('paused scene');
  };
  const ensurePaused = () => {
    if (attr('space-transition') !== 'paused') {
      throw Error('Pause menu action unexpectedly resumed the game');
    }
  };
  const dialogAction = async (name, kind) => {
    await click('pause-button-' + name, 'pause/' + name);
    await wait(() => modal(kind), 'pause/' + name + ' opens stock modal');
    await alive(name + ' dialog opened');
    if (name === 'settings' && params.has('mindustrySettingsNestedSmoke')) {
      await click('settings-hit-sound', 'Settings Sound');
      await wait(() => attr('settings-native-page') === 'sound', 'Sound page');
      for (const key of ['musicvol','sfxvol','ambientvol']) {
        const prefix = 'settings-hit-setting-' + key;
        const p = await position(prefix,key);
        const previous = attr('settings-value-setting-' + key);
        const id = 'volume-drag-' + (++clicks);
        const write = Storage.prototype.setItem;
        let writes = 0;
        Storage.prototype.setItem = function(key,value) {
          if (this === localStorage && String(value).startsWith('MWS1|')) writes++;
          return write.call(this,key,value);
        };
        // Drag a real Arc Slider through Chrome input, including pointer-up.
        root.setAttribute('data-mindustry-cdp-drag-request', JSON.stringify({
          id,x0:p.x,y0:p.y,x1:p.x+(Number(previous)<50?120:60),y1:p.y,steps:18
        }));
        await wait(() => attr('cdp-drag-ack') === id, 'trusted drag ' + key);
        await wait(() => attr('settings-value-setting-' + key) !== previous, 'changed ' + key);
        await alive('paused drag ' + key);
        Storage.prototype.setItem = write;
        if (writes > 1) throw Error('Settings persisted during live drag: ' + key + ' writes=' + writes);
        if (writes !== 1) throw Error('Settings drag did not persist on release: ' + key + ' writes=' + writes);
        ensurePaused();
      }
      await click('settings-hit-back', 'Sound Back button');
      await wait(() => attr('settings-native-page') === 'root', 'Sound Back');
    }
    if (name === 'settings' && params.has('mindustrySettingsNestedSmoke')) {
      await click('settings-hit-back', 'Settings Back button');
    } else await escape();
    await wait(() => !modal(kind), 'pause/' + name + ' dialog closes');
    ensurePaused();
    await alive(name + ' dialog dismissed');
  };
  const researchAction = async (prefix,name) => {
    await click(prefix,name);
    await wait(() => attr('research-open') === 'yes', name + ' opened real research');
    await alive(name + ' open');
    await escape();
    await wait(() => attr('research-open') === 'no', name + ' research close');
    await alive(name + ' close');
  };
  await wait(() => attr(stateKey) === 'playing', 'actual map running');
  await alive('loaded playable sector');
  // The live HUD uses its original native PlacementFragment Research icon;
  // opening/closing may never steal the sector pause keyboard action.
  if (campaign) {
    await researchAction('hud-research', 'HUD Research');
    await wait(() => attr(stateKey) === 'playing', 'research keeps sector running');
  }
  await pause();
  const visibleButtons = ['resume', 'settings', 'save', 'schematics',
    'controls', 'saves', 'menu'];
  if (campaign) visibleButtons.push('research');
  for (const item of visibleButtons) {
    await position('pause-button-' + item, 'pause/' + item);
  }
  root.setAttribute('data-mindustry-pause-visible-controls', String(visibleButtons.length));

  // Exercise each native dialog from an actually paused world, including
  // new close/open cycles which expose stale modal grabs and stuck input.
  await dialogAction('settings', 'settings');
  await dialogAction('controls', 'controls');
  await dialogAction('schematics', 'schematics');
  await dialogAction('saves', 'saves');
  await dialogAction('settings', 'settings');
  if (campaign) {
    await researchAction('pause-button-research', 'Pause Research');
    ensurePaused();
  }

  const frozen = attr(campaign ? 'campaign-pause-update-id' : 'local-map-pause-update-id');
  await click('pause-button-save', 'Pause Save Game');
  await wait(() => campaign
      ? attr('campaign-manual-save') === 'ready'
      : attr('local-map-save') === 'ready',
    'native Save Game button actually commits a save');
  if (campaign) {
    const bytes = Number(attr('campaign-manual-save-bytes'));
    if (!(bytes > 128)) throw Error('Campaign save had no data: ' + bytes);
    if (attr('campaign-manual-save-update-id') !== frozen) {
      throw Error('Save Game advanced frozen campaign simulation');
    }
  }
  ensurePaused();
  await alive('paused native save');

  await click('pause-button-resume', 'Pause Resume');
  // Clicking native Resume goes directly through the game's state machine;
  // the separate space-transition marker records keyboard actions only.
  await wait(() => attr(stateKey) === 'playing', 'real Resume action');
  await alive('resume gameplay');
  await pause();
  await click('pause-button-menu', 'Back to menu');
  await wait(() => attr(stateKey) === 'menu' &&
    attr('main-menu-page') === 'home', 'return to actual main menu');
  await alive('return to menu');
  root.setAttribute('data-mindustry-gameplay-button-stress', 'passed');
  root.setAttribute('data-mindustry-gameplay-button-clicks', String(clicks));
  root.setAttribute('data-mindustry-gameplay-button-scenario', campaign ? 'campaign' : 'local');
  return 'Live native gameplay HUD and pause controls, save/resume/quit stable';
})()

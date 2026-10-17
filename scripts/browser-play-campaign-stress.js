// Native Chromium end-to-end: HOME > Play > Campaign - Planets.
// The original 3D PlanetDialog must open through its real Arc TextButton,
// never a JavaScript shortcut, with custom local maps still reachable.
(async () => {
  const r = document.documentElement;
  const canvas = document.getElementById('mindustry-canvas');
  const get = key => r.getAttribute('data-mindustry-' + key);
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  const wait = async (predicate, message) => {
    for(let i=0; i<160; i++){
      if(get('web') !== 'ready') throw Error('Game runtime crashed while ' + message +
        '; exception=' + get('error') + '; planet-frame=' +
        get('planet-frame-phase') + '; planet-failure=' +
        get('planet-frame-failure') + '; menu-module=' + get('module-phase'));
      if(predicate()) return;
      await sleep(100);
    }
    throw Error('Play > Campaign navigation failed: ' + message +
      ', page=' + get('main-menu-page') +
      ', planet=' + get('vanilla-planet-stage'));
  };
  const frame = async label => {
    await wait(() => Number(get('cpu-sample')) > 0, label + ' first Java heartbeat');
    const start = Number(get('cpu-sample'));
    await wait(() => Number(get('cpu-sample')) > start,
      label + ' Java/TeaVM scheduler continues');
  };
  let clickId=0;
  const click = async (key, label) => {
    await wait(() => r.hasAttribute('data-mindustry-' + key + '-x'), label + ' stock hitbox');
    const rect = canvas.getBoundingClientRect();
    const x = Number(get(key + '-x')), y = Number(get(key + '-y'));
    if (!(x > 0 && x < rect.width && y > 0 && y < rect.height)){
      throw Error(label + ' clipped outside game canvas: ' + x + ',' + y +
        ' vs ' + rect.width + 'x' + rect.height);
    }
    const p = {x: rect.left + x, y: rect.bottom - y};
    if(document.elementFromPoint(p.x, p.y) !== canvas){
      throw Error(label + ' obstructed by portal/advertisement DOM');
    }
    const id = 'play-campaign-' + (++clickId);
    r.setAttribute('data-mindustry-cdp-click-ack', '');
    r.setAttribute('data-mindustry-cdp-click-request', JSON.stringify({id, ...p}));
    await wait(() => {
      const result=get('cdp-click-ack') || '';
      return result===id || result.startsWith('ERROR:');
    }, label + ' trusted Chrome click');
    if(get('cdp-click-ack') !== id) throw Error(label + ' CDP pointer failure');
  };
  await wait(() => get('main-menu-page')==='home', 'Home Arc menu');
  await frame('Home');
  await click('main-menu-play', 'Home Play');
  await wait(() => get('main-menu-page')==='play', 'Play native panel');
  await frame('Play submenu');
  await click('play-campaign', 'Campaign - Planets native TextButton');
  await wait(() => get('vanilla-planet-open')==='yes', 'original 3D PlanetDialog callback');
  await wait(() => get('vanilla-planet-frame')==='rendered', 'real 3D planet mesh render');
  await wait(() => get('planet-first-chooser')==='closed',
    'No first-run chooser may intercept native planet buttons');
  await wait(() => get('planet-viewed')==='serpulo'
      || get('planet-viewed')==='erekir',
    'original Serpulo/Erekir 3D globe visible');
  await frame('3D planet visible after native Campaign action');
  // Real Yandex report: game showed the Play menu for 132 frames, then
  // Sector.icon() threw while painting a sector projection. Do NOT certify
  // a dialogue merely because its first opening frame was visible.
  const firstStableFrame=Number(get('planet-render-frames'));
  await sleep(2200);
  // cpu-sample is published only every 64 full Java frames. A busy software
  // renderer can stay alive without crossing that boundary in 2.2 seconds.
  // Require another completed original 3D draw, then the scheduler watchdog.
  await wait(() => Number(get('planet-render-frames')) > firstStableFrame,
    'completed original 3D frames continue beyond initial opening');
  await frame('3D sector projections stay alive beyond initial render');
  if(get('main-menu-page') !== (get('input-mode')==='mobile'?'play':'home')){
    throw Error('Planet dialog left native navigation in an unexpected state');
  }
  r.setAttribute('data-mindustry-play-campaign-smoke','passed');
  r.setAttribute('data-mindustry-play-campaign-device',
    get('input-mode')==='mobile'?'mobile':'desktop');
  r.setAttribute('data-mindustry-play-campaign-clicks', String(clickId));
  return 'Native Play/Campaign opens real Serpulo and Erekir 3D planets';
})()

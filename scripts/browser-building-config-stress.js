// CI-only. Real CDP browser pointer input drives stock SorterBuild and
// original BlockConfigFragment/ItemSelection; no direct Java configure calls.
(async () => {
  const root = document.documentElement;
  const canvas = document.getElementById('mindustry-canvas');
  const value = name => root.getAttribute('data-mindustry-' + name);
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  const wait = async (f, step) => {
    for (let n = 0; n < 140; n++) {
      if (value('web') !== 'ready') throw Error('Mindustry frame died: ' + step +
        '; web-error=' + value('error') +
        '; campaign=' + value('campaign-state') +
        '; selected=' + value('building-config-selected') +
        '; expected-choice=' + value('building-config-choice'));
      if (f()) return;
      await sleep(100);
    }
    throw Error('Native Sorter UI timed out: ' + step +
      ' config=' + value('building-config-choice') +
      ' opened=' + value('building-config-opened') +
      ' result=' + value('building-config-selected'));
  };
  const alive = async step => {
    await wait(() => Number(value('cpu-sample')) > 0, 'initial TeaVM frames');
    const before = Number(value('cpu-sample'));
    await wait(() => Number(value('cpu-sample')) > before, 'Java frame loop after ' + step);
  };
  let count = 0;
  const click = async (which, label) => {
    await wait(() => root.hasAttribute('data-mindustry-building-config-' + which + '-x'),
      'stock ' + label + ' pointer coordinates');
    const rect = canvas.getBoundingClientRect();
    const x = Number(value('building-config-' + which + '-x'));
    const y = Number(value('building-config-' + which + '-y'));
    if (!(x > 0 && y > 0 && x < rect.width && y < rect.height)) {
      throw Error('Native ' + label + ' outside Yandex game viewport: ' + x + ',' + y);
    }
    const p = {x: rect.left + x, y: rect.bottom - y};
    if (document.elementFromPoint(p.x, p.y) !== canvas)
      throw Error('Stock ' + label + ' button covered by portal/ad DOM');
    const id = 'stock-config-' + (++count);
    root.setAttribute('data-mindustry-cdp-click-ack', '');
    root.setAttribute('data-mindustry-cdp-click-request', JSON.stringify({id, ...p}));
    await wait(() => {
      const ack = value('cdp-click-ack') || '';
      return ack === id || ack.startsWith('ERROR:');
    }, 'real click ' + label);
    if (value('cdp-click-ack') !== id) throw Error('Chrome rejected ' + label);
  };
  await wait(() => value('campaign-state') === 'playing' &&
    value('building-config-fixture') === 'sorter', 'actual stock Sorter fixture');
  await alive('world creation');
  await click('tile', 'world Sorter tile');
  await wait(() => value('building-config-opened') === 'first', 'stock BlockConfigFragment open');
  await wait(() => value('building-config-choice') === 'set', 'original copper choice');
  await alive('open building configuration');
  await click('copper', 'ItemSelection Copper choice');
  await wait(() => value('building-config-selected') === 'copper',
    'sorter configured with copper via original stock callback');
  await alive('stock Sorter copper configured');
  // Native ItemSelection closes after selection; the original world tile
  // must be tappable again and offer the selected copper toggle.
  await click('tile', 'world Sorter reopen');
  await wait(() => value('building-config-opened') === 'reopen', 'reopened original config');
  await wait(() => value('building-config-choice') === 'clear', 'selected copper toggle');
  await alive('config reopen');
  await click('copper', 'ItemSelection Copper clear');
  await wait(() => value('building-config-cleared') === 'yes' &&
    value('building-config-smoke') === 'passed', 'original config clear removes copper');
  await alive('stock Sorter configuration clear');
  if (value('campaign-state') !== 'playing') throw Error('World paused by building config');
  root.setAttribute('data-mindustry-building-config-native-clicks', String(count));
  root.setAttribute('data-mindustry-building-config-device', value('input-mode'));
  return 'Original copper Sorter config and clear UI works through real Chrome input';
})()

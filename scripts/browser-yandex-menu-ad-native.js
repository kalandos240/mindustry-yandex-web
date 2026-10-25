// Trusted native Pause -> Back -> Cancel -> Back -> Confirm in the packaged
// Yandex SDK fixture. No direct Java callbacks, fake UI or forced game state.
(async () => {
  const root = document.documentElement;
  const canvas = document.getElementById('mindustry-canvas');
  const get = name => root.getAttribute('data-mindustry-' + name);
  const getYandex = name => root.getAttribute('data-yandex-' + name);
  const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
  const wait = async (condition, label) => {
    for(let attempt=0; attempt<750; attempt++){
      if(get('web')==='error') throw Error('Java game error at '+label);
      if(condition()) return;
      await sleep(100);
    }
    throw Error('Native menu-ad transition timed out: '+label);
  };
  let sequence=0;
  async function click(point){
    await wait(()=>get(point+'-x')!==null && get(point+'-y')!==null,point+' hitpoint');
    const bounds=canvas.getBoundingClientRect();
    const x=bounds.left+Number(get(point+'-x'));
    const y=bounds.bottom-Number(get(point+'-y'));
    if(!(x>bounds.left&&x<bounds.right&&y>bounds.top&&y<bounds.bottom))
      throw Error('Native '+point+' was clipped by the Yandex game viewport');
    if(document.elementFromPoint(x,y)!==canvas)
      throw Error('Native '+point+' was obscured');
    const id='yandex-menu-'+(++sequence);
    root.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x,y}));
    await wait(()=>get('cdp-click-ack')===id,'trusted '+point+' input');
  }
  async function escape(){
    const id='yandex-menu-escape-'+(++sequence);
    root.setAttribute('data-mindustry-cdp-key-request',JSON.stringify({id,key:'Escape'}));
    await wait(()=>get('cdp-key-ack')===id,'trusted Escape key');
  }
  await wait(()=>get('campaign-ui-back-smoke')==='triggered' && get('campaign-state')==='playing',
             'live campaign readiness');
  if(getYandex('device-type')==='mobile') await click('mobile-hud-menu');
  else await escape();
  await wait(()=>get('pause-dialog-visible')==='yes','stock Pause opened');
  await click('pause-button-menu');
  await wait(()=>get('pause-button-exit-cancel-x')!==null &&
                get('pause-button-exit-confirm-x')!==null,'native Back confirmation');
  if(get('campaign-state')==='menu')throw Error('Back skipped the native confirmation');
  await click('pause-button-exit-cancel');
  await wait(()=>get('pause-dialog-visible')==='yes','Cancel keeps world paused');
  if(get('campaign-state')==='menu')throw Error('Cancel incorrectly exited the sector');
  await click('pause-button-menu');
  await click('pause-button-exit-confirm');
  await wait(()=>get('campaign-state')==='menu' &&
                getYandex('menu-ad-state')==='closed-shown','saved return and SDK ad completion');
  if(getYandex('test-menu-ad-gameplay-before')!=='stopped')
    throw Error('Yandex interstitial started before GameplayAPI stopped');
  root.setAttribute('data-mindustry-native-menu-ad-input','passed');
  return 'Real Pause/Back/Cancel/Confirm pointer and keyboard path PASS';
})()

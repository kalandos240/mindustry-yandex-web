// Reproduce user-reported crash: actual stock Icon.map in the live campaign
// HUD, after real Chrome click, must open original 3D Serpulo/Erekir globe.
(async () => {
  const root=document.documentElement;
  const canvas=document.getElementById('mindustry-canvas');
  const v=k=>root.getAttribute('data-mindustry-'+k);
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const wait=async(fn,label)=>{
    for(let i=0;i<180;i++){
      if(v('web')==='error')throw Error('Mindustry froze after '+label+
        ': '+document.body.innerText.slice(-350));
      if(fn())return;
      await sleep(100);
    }
    throw Error('Native campaign planet HUD failed: '+label+
      ', hud='+v('hud-planet-ready')+', planet='+v('hud-planet-open')+
      ', frame='+v('vanilla-planet-frame')+', game='+v('campaign-state'));
  };
  await wait(()=>v('campaign-state')==='playing','live campaign');
  await wait(()=>v('hud-planet-ready')==='yes','native map button exposed');
  const rect=canvas.getBoundingClientRect();
  const x=Number(v('hud-planet-x')),y=Number(v('hud-planet-y'));
  if(!(x>0&&x<rect.width&&y>0&&y<rect.height))
    throw Error('Native Icon.map outside Yandex canvas: '+x+','+y);
  const px=rect.left+x,py=rect.bottom-y;
  if(document.elementFromPoint(px,py)!==canvas)
    throw Error('Campaign Icon.map hitbox blocked by portal DOM');
  const id='hud-planet-live-1';
  root.setAttribute('data-mindustry-cdp-click-ack','');
  root.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x:px,y:py}));
  await wait(()=>v('cdp-click-ack')===id || (v('cdp-click-ack')||'').startsWith('ERROR:'),
    'trusted canvas click receipt');
  if(v('cdp-click-ack')!==id)throw Error('Chrome rejected stock planet click');
  await wait(()=>v('hud-planet-open')==='yes','original 3D planet dialog');
  await wait(()=>v('vanilla-planet-frame')==='rendered','real 3D rendering');
  await wait(()=>v('planet-first-chooser')==='closed','no first-run modal blocking globe');
  const prior=Number(v('cpu-sample'));
  window.dispatchEvent(new Event('resize'));
  await wait(()=>Number(v('cpu-sample'))>prior,
    'TeaVM lives after resize while original 3D planet is open');

  // User-facing campaign-switch path: while Onset is already playing, use
  // the real planet-selector and real stock Sector Launch to move to
  // Ground Zero. BrowserPlanetBridge must checkpoint Erekir BEFORE resetting
  // world, then load the Serpulo sector with a live GameState.
  await wait(()=>v('planet-hit-serpulo-exposed')==='yes',
    'original Serpulo planet selector receives clicks in active campaign');
  const selectX=Number(v('planet-hit-serpulo-x'));
  const selectY=Number(v('planet-hit-serpulo-y'));
  if(!(selectX>0&&selectX<rect.width&&selectY>0&&selectY<rect.height))
    throw Error('Original Serpulo globe button out of game canvas');
  const clickStock=async(x,y,name)=>{
    const req='hud-switch-'+name;
    root.setAttribute('data-mindustry-cdp-click-ack','');
    root.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({
      id:req,x:rect.left+x,y:rect.bottom-y
    }));
    await wait(()=>v('cdp-click-ack')===req ||
      (v('cdp-click-ack')||'').startsWith('ERROR:'),
      'trusted Chrome click '+name);
    if(v('cdp-click-ack')!==req)throw Error(name+' mouse click rejected');
  };
  await clickStock(selectX,selectY,'serpulo-planet');
  await wait(()=>v('planet-start-sector-planet')==='serpulo',
    'start-sector panel changes to real Serpulo');
  await wait(()=>v('planet-hit-launch-exposed')==='yes' &&
    v('planet-hit-launch-disabled')==='no',
    'original selected-sector Launch button');
  const launchX=Number(v('planet-hit-launch-x'));
  const launchY=Number(v('planet-hit-launch-y'));
  if(!(launchX>0&&launchX<rect.width&&launchY>0&&launchY<rect.height))
    throw Error('Original Serpulo Launch button outside canvas');
  await clickStock(launchX,launchY,'launch-groundzero');
  await wait(()=>v('campaign-state')==='playing' &&
    v('campaign-preset')==='groundZero' && v('campaign-planet')==='serpulo',
    'live Erekir -> autosaved -> real Serpulo campaign world');
  const stable=Number(v('cpu-sample'));
  await wait(()=>Number(v('cpu-sample'))>stable,
    'TeaVM lives after native cross-planet campaign transition');
  root.setAttribute('data-mindustry-hud-planet-cross-campaign','passed');
  root.setAttribute('data-mindustry-hud-planet-browser','passed');
  return 'Live HUD Icon.map opened 3D globe, switched Onset to GroundZero via real Launch and survived resize';
})()
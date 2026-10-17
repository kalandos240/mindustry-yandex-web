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
  root.setAttribute('data-mindustry-hud-planet-browser','passed');
  return 'Live campaign HUD Icon.map opened real 3D globe and survived resize';
})()
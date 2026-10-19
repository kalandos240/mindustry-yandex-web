// Browser acceptance: real Chrome click on vanilla HudFragment "skip"
// button, and the original GameState.wave must advance. No JS wave cheating.
(async () => {
  const root=document.documentElement;
  const canvas=document.getElementById('mindustry-canvas');
  const val=k=>root.getAttribute('data-mindustry-'+k);
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const wait=async(fn,name)=>{
    for(let i=0;i<190;i++){
      if(val('web')==='error') throw Error('Mindustry froze during '+name+
        ': '+document.body.innerText.slice(-280));
      if(fn()) return;
      await sleep(100);
    }
    throw Error('Vanilla wave progression failed: '+name+
      '; wave='+val('campaign-wave')+
      '; ready='+val('wave-hud-ready')+
      '; timer='+val('wave-hud-timer')+
      '; sending='+val('wave-hud-sending'));
  };
  await wait(()=>val('campaign-state')==='playing','real campaign live');
  await wait(()=>val('wave-hud-ready')==='yes','stock visible Wave skip button');
  const rect=canvas.getBoundingClientRect();
  const x=Number(val('wave-hud-x')), y=Number(val('wave-hud-y'));
  if(!(x>0 && x<rect.width && y>0 && y<rect.height))
    throw Error('Stock Wave skip button is outside Yandex game canvas: '+x+','+y);
  const clickX=rect.left+x, clickY=rect.bottom-y;
  if(document.elementFromPoint(clickX,clickY)!==canvas)
    throw Error('Original wave button covered by portal/advertisement');
  const id='vanilla-wave-button-1';
  root.setAttribute('data-mindustry-cdp-click-ack','');
  root.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({
    id,x:clickX,y:clickY
  }));
  await wait(()=>val('cdp-click-ack')===id || (val('cdp-click-ack')||'').startsWith('ERROR:'),
    'trusted native Chrome Wave click acknowledged');
  if(val('cdp-click-ack')!==id)
    throw Error('Trusted native skip-wave click rejected: '+val('cdp-click-ack'));
  await wait(()=>val('wave-hud-result')==='passed','original Logic.runWave');
  const oldWave=Number(val('wave-hud-start')), nextWave=Number(val('wave-hud-end'));
  if(nextWave<=oldWave)throw Error('Stock HUD did not increment wave number');
  const sample=Number(val('cpu-sample'));
  await wait(()=>Number(val('cpu-sample'))>sample,'game loop lives after native wave');
  root.setAttribute('data-mindustry-wave-hud-browser','passed');
  root.setAttribute('data-mindustry-wave-hud-native-clicks','1');
  return 'Actual HudFragment skip button advanced wave and TeaVM remained alive';
})()

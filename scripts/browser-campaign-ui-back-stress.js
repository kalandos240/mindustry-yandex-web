// Native mobile toolbar -> Pause -> Back -> Cancel -> Back -> Confirm.
// Uses the same production callbacks as the player and a read-only state probe.
(async()=>{
  const r=document.documentElement,c=document.getElementById('mindustry-canvas');
  const v=k=>r.getAttribute('data-mindustry-'+k),sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  if(typeof globalThis.__mindustryQaSnapshot!=='function')throw Error('Read-only QA probe missing');
  const snapshot=()=>globalThis.__mindustryQaSnapshot();
  const wait=async(fn,label)=>{for(let i=0;i<180;i++){
    if(v('web')!=='ready')throw Error('Campaign Back crashed: '+v('error'));
    if(fn())return;await sleep(100);
  }throw Error('Campaign Back blocked: '+label);};
  const settled=async label=>{const n=Number(v('cpu-sample'));await wait(()=>Number(v('cpu-sample'))>n,label+' native layout');};
  let sequence=0;
  const click=async prefix=>{
    await wait(()=>v(prefix+'-x')!==null,prefix+' actual hitpoint');
    const bounds=c.getBoundingClientRect(),x=Number(v(prefix+'-x')),y=Number(v(prefix+'-y'));
    if(!(x>0&&y>0&&x<bounds.width&&y<bounds.height))throw Error('Clipped native '+prefix);
    if(document.elementFromPoint(bounds.left+x,bounds.bottom-y)!==c)throw Error('Obscured native '+prefix);
    const id='campaign-back-'+(++sequence);
    r.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x:bounds.left+x,y:bounds.bottom-y}));
    await wait(()=>v('cdp-click-ack')===id,prefix+' trusted input');
  };
  await wait(()=>v('campaign-ui-back-smoke')==='triggered'&&snapshot().playing,'live campaign and Back readiness');
  const live=snapshot();await wait(()=>snapshot().tick>live.tick&&BigInt(snapshot().updateId)>BigInt(live.updateId),'actual campaign advances');
  await click('mobile-hud-menu');
  await wait(()=>snapshot().paused&&v('pause-dialog-visible')==='yes','native Pause menu');
  const frozen=snapshot();await settled('paused menu');
  if(snapshot().tick!==frozen.tick||snapshot().updateId!==frozen.updateId)throw Error('Pause menu advanced simulation');
  await click('pause-button-menu');await settled('exit confirmation');
  if(snapshot().menu||!snapshot().paused)throw Error('Back bypassed confirmation');
  await click('pause-button-exit-cancel');
  await wait(()=>v('pause-dialog-visible')==='yes','Cancel retains paused world');await settled('cancel');
  if(snapshot().menu||snapshot().tick!==frozen.tick||snapshot().updateId!==frozen.updateId)throw Error('Cancel changed paused world');
  await click('pause-button-menu');await settled('second exit confirmation');
  await click('pause-button-exit-confirm');
  await wait(()=>snapshot().menu&&!snapshot().campaignActive&&v('campaign-state')==='menu','Confirm returns to menu');
  await wait(()=>v('campaign-back-autosave')==='ready'&&v('campaign-save-flush')==='ready','Back checkpoint is durable');
  r.setAttribute('data-mindustry-campaign-ui-back-native','passed');
  return 'Native Pause/Back confirmation, cancel, confirm and durable checkpoint PASS';
})()

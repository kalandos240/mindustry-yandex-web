(async () => {
  const r=document.documentElement, a=k=>r.getAttribute('data-mindustry-'+k);
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const wait=async(fn,label)=>{for(let i=0;i<200;i++){
    if(a('web')==='error')throw Error('Frame failed: '+a('web-error'));
    if(fn())return;await sleep(100);
  }throw Error('Defeat action blocked: '+label);};
  await wait(()=>a('defeat-visible')==='yes','visible loss screen');
  const before=Number(a('cpu-sample'));
  await wait(()=>Number(a('cpu-sample'))>before,'live result frame loop');
  const rect=document.getElementById('mindustry-canvas').getBoundingClientRect();
  const x=rect.left+Number(a('defeat-back-x')), y=rect.bottom-Number(a('defeat-back-y'));
  if(!(x>rect.left&&x<rect.right&&y>rect.top&&y<rect.bottom))throw Error('Defeat exit clipped');
  r.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id:'defeat-exit',x,y}));
  await wait(()=>a('cdp-click-ack')==='defeat-exit','trusted exit click');
  const campaign=new URLSearchParams(location.search).has('mindustryCampaignSmoke');
  await wait(()=>a(campaign?'campaign-state':'local-map-state')==='menu','return to menu');
  const after=Number(a('cpu-sample'));
  await wait(()=>Number(a('cpu-sample'))>after,'menu remains alive');
  r.setAttribute('data-mindustry-defeat-exit-test','passed');
})().catch(e=>{document.documentElement.setAttribute('data-mindustry-defeat-exit-test','failed:'+e.message);throw e;});

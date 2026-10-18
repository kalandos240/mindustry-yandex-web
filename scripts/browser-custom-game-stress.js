(async()=>{
  const r=document.documentElement,c=document.getElementById('mindustry-canvas');
  const get=n=>r.getAttribute('data-mindustry-'+n);
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const wait=async(fn,label)=>{for(let i=0;i<180;i++){if(get('web')==='failed')throw Error(get('web-error'));if(fn())return;await sleep(100);}throw Error(label+' / page='+get('custom-page'));};
  let seq=0;
  const click=async prefix=>{
    await wait(()=>r.hasAttribute('data-mindustry-'+prefix+'-x'),prefix+' coordinates');
    const b=c.getBoundingClientRect(),x=Number(get(prefix+'-x')),y=Number(get(prefix+'-y'));
    if(!(x>0&&y>0&&x<b.width&&y<b.height))throw Error(prefix+' outside canvas '+x+','+y);
    if(prefix!=='custom-native' && prefix.startsWith('custom-')){
      const bounds=['left','bottom','right','top'].map(edge=>Number(get(prefix+'-'+edge)));
      if(!(bounds[0]>=-0.5&&bounds[1]>=-0.5&&bounds[2]<=b.width+0.5&&bounds[3]<=b.height+0.5))throw Error(prefix+' clipped '+bounds+' in '+b.width+'x'+b.height);
    }
    const id='custom-'+(++seq);r.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x:b.left+x,y:b.bottom-y}));
    await wait(()=>get('cdp-click-ack')===id,'trusted click '+prefix);
  };
  await click('main-menu-play');
  if(get('main-menu-layout')!=='mobile-grid')await click('main-menu-custom');
  await click('custom-native');
  await wait(()=>get('custom-page')==='catalog','stock catalog');
  if(get('custom-previews')!=='18')throw Error('Expected 18 real map previews');
  await click('custom-first-map');
  await wait(()=>get('custom-page')==='map','stock map mode dialog');
  await click('custom-survival');
  await click('custom-customize');
  await wait(()=>get('custom-page')==='rules','stock custom rules');
  await click('custom-wave-timer');
  await click('custom-back');
  await wait(()=>get('custom-page')==='map','custom rules return');
  await click('custom-launch');
  await wait(()=>get('local-map-state')==='playing','real selected map playing');
  await wait(()=>get('local-map-loop')==='live','real selected map frames');
  if(get('local-map-slug')!=='archipelago'||get('local-map-wave-timer')!=='false'||get('local-map-infinite-resources')!=='false')throw Error('Selected map or custom rules lost');
  const before=Number(get('cpu-sample'));await wait(()=>Number(get('cpu-sample'))>before,'game heartbeat after custom launch');
  r.setAttribute('data-mindustry-custom-game-stress','passed');
  return 'Stock catalog, 18 previews, mode, customized rules and real launch passed';
})()

(async()=>{
  const r=document.documentElement,c=document.getElementById('mindustry-canvas');
  const get=n=>r.getAttribute('data-mindustry-'+n);
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const wait=async(fn,label)=>{for(let i=0;i<180;i++){if(get('web')==='error')throw Error('Custom map frame crashed: '+JSON.stringify(globalThis.__mindustryErrorReport));if(fn())return;await sleep(100);}throw Error(label+' / page='+get('custom-page'));};
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
  if(new URLSearchParams(location.search).get('mindustryCustomNarrow')==='1'){
    const surface=document.getElementById('mindustry-game-surface'),before=Number(get('resize-count'));
    surface.style.marginLeft='7%';surface.style.width='50%';
    await wait(()=>Number(get('resize-count'))>before&&get('main-menu-size')===surface.clientWidth+'x'+surface.clientHeight&&get('container-match')==='true','native narrow scene resize');
    if(!(c.clientWidth>=300&&c.clientWidth<=400)||get('main-menu-layout')!=='desktop-sidebar')throw Error('Narrow fixture did not preserve desktop input');
  }
  if(get('main-menu-layout')!=='mobile-grid')await click('main-menu-play');
  await click('main-menu-custom');
  await wait(()=>get('custom-page')==='catalog','stock catalog');
  if(get('custom-previews')!=='18')throw Error('Expected 18 real map previews');
  // Native catalog sorting uses display names: Ancient Caldera precedes
  // Archipelago. In the one-column portal, scroll through the actual pane.
  const mapVisible=()=>{
    if(!r.hasAttribute('data-mindustry-custom-first-map-x'))return false;
    const b=c.getBoundingClientRect(),v=['left','bottom','right','top'].map(e=>Number(get('custom-first-map-'+e)));
    return v[0]>=-0.5&&v[1]>=-0.5&&v[2]<=b.width+0.5&&v[3]<=b.height+0.5;
  };
  if(new URLSearchParams(location.search).get('mindustryCustomNarrow')==='1'){
    for(let i=0;i<16&&!mapVisible();i++){
      const b=c.getBoundingClientRect(),id='catalog-wheel-'+(++seq);
      r.setAttribute('data-mindustry-cdp-scroll-request',JSON.stringify({id,x:b.left+b.width/2,y:b.top+b.height/2,deltaY:100}));
      await wait(()=>get('cdp-scroll-ack')===id||String(get('cdp-scroll-ack')).startsWith('ERROR:'),'native catalog wheel');
      if(get('cdp-scroll-ack')!==id)throw Error(get('cdp-scroll-ack'));
      await sleep(600);
    }
  }
  await wait(mapVisible,'whole selected card after native catalog scrolling');
  await click('custom-first-map');
  await wait(()=>get('custom-page')==='map','stock map mode dialog');
  await click('custom-sandbox');
  await click('custom-survival');
  await click('custom-customize');
  await wait(()=>get('custom-page')==='rules','stock custom rules');
  await click('custom-wave-timer');
  // Resize while the rules modal covers MapPlayDialog. Its parent must refit
  // on return, preserving the edited rule and actual Back/Play hit rectangles.
  const surface=document.getElementById('mindustry-game-surface'),resizeBefore=Number(get('resize-count'));
  const oldHeight=surface.clientHeight;
  surface.style.height='85%';
  await wait(()=>Number(get('resize-count'))>resizeBefore&&surface.clientHeight<oldHeight&&get('container-match')==='true','resize under rules modal');
  const resizedFrame=Number(get('cpu-sample'));
  await wait(()=>Number(get('cpu-sample'))>resizedFrame,'rule modal and parent refit');
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

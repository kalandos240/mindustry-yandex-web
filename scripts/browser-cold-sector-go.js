(async()=>{
 const r=document.documentElement,c=document.getElementById('mindustry-canvas'),v=k=>r.getAttribute('data-mindustry-'+k);
 const sleep=ms=>new Promise(f=>setTimeout(f,ms));
 const wait=async(f,label)=>{for(let i=0;i<240;i++){if(v('campaign-launch-failure'))throw Error(v('campaign-launch-failure'));if(v('web')==='error')throw Error(v('error'));if(f())return;await sleep(100);}throw Error(label);};
 let seq=0;
 const click=async(prefix)=>{await wait(()=>v(prefix+'-x')&&v(prefix+'-y'),prefix+' coordinates');const b=c.getBoundingClientRect(),id='cold-sector-'+(++seq);r.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x:b.left+Number(v(prefix+'-x')),y:b.bottom-Number(v(prefix+'-y'))}));await wait(()=>v('cdp-click-ack')===id,'click '+prefix);};
 if(v('main-menu-layout')!=='mobile-grid'){await click('main-menu-play');await wait(()=>v('main-menu-play-submenu')==='open','Play submenu');}
 await click(v('main-menu-layout')==='mobile-grid'?'main-menu-play':'main-menu-planets');
 await wait(()=>v('vanilla-planet-open')==='yes','original planet dialog');
 let sample=Number(v('cpu-sample'));await wait(()=>Number(v('cpu-sample'))>=sample+2,'planet layout');
 const planet=new URLSearchParams(location.search).get('savedPlanet')||'erekir';
 await click('planet-hit-'+planet);
 await wait(()=>v('planet-start-sector-planet')===planet&&v('planet-hit-launch-exposed')==='yes','saved-sector Go');
 sample=Number(v('cpu-sample'));await wait(()=>Number(v('cpu-sample'))>=sample+2,'Go layout');
 const lostExpected=new URLSearchParams(location.search).has('expectLostCore');
 const storage=globalThis.__mindustryStorage;
 const savedPath=lostExpected?storage.paths().find(p=>p.endsWith('.msav')&&!p.includes('backup')):null;
 const savedBytes=savedPath?new Uint8Array(storage.get(savedPath)):null;
 if(lostExpected&&(!savedBytes||savedBytes.length<128))throw Error('Lost-sector source checkpoint missing');
 globalThis.__mindustryThrowCount=0;
 await click('planet-hit-launch');
 const lost=new URLSearchParams(location.search).has('expectLostCore');
 await wait(()=>v('campaign-state')==='playing'&&(lost?v('campaign-lost-sector-relaunch')==='onset':v('campaign-resume')==='ready'),'saved sector loaded');
 if(lost){
  const backup=new Uint8Array(storage.get(savedPath+'.lost-backup')||[]);
  if(backup.length!==savedBytes.length||backup.some((byte,i)=>byte!==savedBytes[i]))throw Error('Lost-sector backup differs from original MSAV');
  await storage.flush();
  r.setAttribute('data-mindustry-lost-backup-exact','passed');
 }
 const frame=Number(v('campaign-update-id'));await wait(()=>Number(v('campaign-update-id'))>frame,'world simulation continues');
 await sleep(4500);
 if(v('input-mode')!=='mobile'){
  window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,code:'KeyD',key:'d'}));
  await sleep(700);
  window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,code:'KeyD',key:'d'}));
 }else{
  const b=c.getBoundingClientRect(),id='lost-sector-movement';
  r.setAttribute('data-mindustry-cdp-drag-request',JSON.stringify({id,x0:b.left+b.width*.45,y0:b.top+b.height*.50,x1:b.left+b.width*.6,y1:b.top+b.height*.50,steps:12}));
  await wait(()=>v('cdp-drag-ack')===id,'mobile movement gesture');
 }
 await sleep(1000);
 if(v('web')!=='ready')throw Error('Game stopped after player movement');
 r.setAttribute('data-mindustry-cold-sector-go','passed');return 'Native saved-sector Go resumes after a cold browser restart';
})()
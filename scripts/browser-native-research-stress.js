(async()=>{
 const r=document.documentElement,c=document.getElementById('mindustry-canvas');
 const get=k=>r.getAttribute('data-mindustry-'+k),sleep=ms=>new Promise(f=>setTimeout(f,ms));
 const wait=async(f,label)=>{for(let i=0;i<260;i++){if(get('web')==='error')throw Error(get('error'));if(f())return;await sleep(100);}throw Error('Native ResearchDialog: '+label);};
 let seq=0;
 const tap=async(x,y)=>{const id='native-research-'+(++seq);r.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x,y}));await wait(()=>get('cdp-click-ack')===id,'trusted click');};
 const click=async key=>{await wait(()=>get('native-research-'+key+'-exposed')==='yes',key+' exposed');const a=JSON.parse(get('native-research-'+key)),b=c.getBoundingClientRect();if(a[0]<-.5||a[1]<-.5||a[2]>b.width+.5||a[3]>b.height+.5)throw Error('Native research clipped '+key+': '+a);await tap(b.left+(a[0]+a[2])/2,b.bottom-(a[1]+a[3])/2);};
 const campaign=new URLSearchParams(location.search).has('mindustryCampaignSmoke');
 const headers=()=>{
  const b=c.getBoundingClientRect(),a=JSON.parse(get('native-research-title')),d=JSON.parse(get('native-research-items'));
  for(const [key,box] of [['title',a],['items',d]])if(!box||box[0]<-.5||box[1]<-.5||box[2]>b.width+.5||box[3]>b.height+.5)throw Error('Native research header clipped '+key+': '+box);
  if(Math.min(a[2],d[2])-Math.max(a[0],d[0])>1&&Math.min(a[3],d[3])-Math.max(a[1],d[1])>1)throw Error('Native research resource header overlaps tree title');
  for(const key of ['back','database']){
   const button=JSON.parse(get('native-research-'+key)),label=JSON.parse(get('native-research-'+key+'-label'));
   if(!button||!label||label[0]<button[0]-.5||label[1]<button[1]-.5||label[2]>button[2]+.5||label[3]>button[3]+.5)throw Error('Translated native footer label clipped '+key);
  }
 };
 await wait(()=>get('research-implementation')==='original-ResearchDialog','original class installed');
 if(campaign)await wait(()=>get('campaign-state')==='playing'&&get('campaign-save')==='valid','actual campaign and sector save');
 if(!campaign&&get('main-menu-layout')!=='mobile-grid'){
  await wait(()=>get('main-menu-database-group-x'),'Database group');const b=c.getBoundingClientRect();await tap(b.left+Number(get('main-menu-database-group-x')),b.bottom-Number(get('main-menu-database-group-y')));
 }
 const prefix=campaign?'hud-research':'main-menu-research';
 const open=async()=>{
  const settled=Number(get('cpu-sample'));await wait(()=>Number(get('cpu-sample'))>=settled+2,'native entry after modal/layout settles');
  if(!campaign&&get('main-menu-layout')!=='mobile-grid'&&get('main-menu-database-submenu')!=='open'){
   await wait(()=>get('main-menu-database-group-x'),'Database parent after resize');const b=c.getBoundingClientRect();await tap(b.left+Number(get('main-menu-database-group-x')),b.bottom-Number(get('main-menu-database-group-y')));
  }
  await wait(()=>get(prefix+'-x')&&get(prefix+'-y'),'native research entry');const b=c.getBoundingClientRect();await tap(b.left+Number(get(prefix+'-x')),b.bottom-Number(get(prefix+'-y')));await wait(()=>get('research-open')==='yes'&&get('native-research-view'),'original dialog visible');
 };
 const narrow=new URLSearchParams(location.search).has('nativeResearchNarrow');
 if(narrow){const surface=document.getElementById('mindustry-game-surface'),old=Number(get('resize-count'));surface.style.width='50%';surface.style.marginLeft='7%';await wait(()=>Number(get('resize-count'))>old&&get('container-match')==='true','initial narrow surface');}
 await open();
 await wait(()=>Number(get('research-tree-visible-nodes'))>2,'native visible nodes');
 await wait(()=>get('native-research-items'),'native resource header');headers();
 if(campaign&&get('native-research-pause-menu-visible')!=='no')throw Error('Web pause menu visible beneath original research modal');
 if(campaign){
  const transitions=Number(get('space-transition-count')||0);
  window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,code:'Space',key:' '}));await sleep(150);window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,code:'Space',key:' '}));
  if(Number(get('space-transition-count')||0)!==transitions)throw Error('Space leaked through native research modal');
 }
 if(!campaign){
  await click('title');await click('root-erekir');await wait(()=>get('research-tree-root')==='core-bastion','Erekir tree');
  await click('title');await click('root-serpulo');await wait(()=>get('research-tree-root')==='core-shard','Serpulo tree');
 }
 if(campaign){
  // Native layout reserves the full technology graph, including hidden
  // descendants. Find the real node by actual view gestures, not by changing
  // camera/selection fields or unlock flags in Java.
  for(let attempt=0;attempt<14;attempt++){
   await wait(()=>get('native-research-conveyor'),'original conveyor node bounds');
   const a=JSON.parse(get('native-research-conveyor')),b=c.getBoundingClientRect(),x=(a[0]+a[2])/2,y=(a[1]+a[3])/2;
   if(a[0]>10&&a[1]>85&&a[2]<b.width-10&&a[3]<b.height-70)break;
   const dx=Math.max(-b.width*.35,Math.min(b.width*.35,b.width*.5-x)),dy=Math.max(-b.height*.2,Math.min(b.height*.2,b.height*.5-y)),id='native-find-node-'+attempt;
   const frame=Number(get('cpu-sample'));
   r.setAttribute('data-mindustry-cdp-drag-request',JSON.stringify({id,x0:b.left+b.width*.5,y0:b.top+b.height*.5,x1:b.left+b.width*.5+dx,y1:b.top+b.height*.5-dy,steps:10}));
   await wait(()=>get('cdp-drag-ack')===id,'pan to native conveyor');await wait(()=>Number(get('cpu-sample'))>=frame+2,'node bounds after pan');
  }
  await wait(()=>get('native-research-copper')==='500','real core inventory fixture');
  if(get('native-research-conveyor-unlocked')!=='no')throw Error('Conveyor must start locked');
  const before=Number(get('native-research-copper'));
  await click('conveyor');
  if(get('input-mode')==='mobile'){
   const card=Number(get('cpu-sample'));await wait(()=>Number(get('cpu-sample'))>=card+2,'native mobile information-card pan completes');
   await click('invest');
  }
  await wait(()=>get('native-research-conveyor-unlocked')==='yes','native spend unlocks Conveyor');
  const copper=Number(get('native-research-copper')),progress=Number(get('native-research-progress'));
  if(!(copper<before&&before-copper===progress&&progress>0))throw Error('Original research cost mismatch '+before+' -> '+copper+' progress '+progress);
 }
 const b=c.getBoundingClientRect();
 if(get('input-mode')!=='mobile'){
  const scale=JSON.parse(get('research-native-pan'))[2],wheel='native-research-zoom';
  r.setAttribute('data-mindustry-cdp-scroll-request',JSON.stringify({id:wheel,x:b.left+b.width*.5,y:b.top+b.height*.4,deltaY:120}));
  await wait(()=>get('cdp-scroll-ack')===wheel,'trusted native wheel');
  await wait(()=>JSON.parse(get('research-native-pan'))[2]<scale,'original wheel zoom');
 }
 const before=JSON.parse(get('research-native-pan')),id='native-research-drag';
 r.setAttribute('data-mindustry-cdp-drag-request',JSON.stringify({id,x0:b.left+b.width*.65,y0:b.top+b.height*.45,x1:b.left+b.width*.4,y1:b.top+b.height*.65,steps:12}));
 await wait(()=>get('cdp-drag-ack')===id,'trusted native graph drag');
 await wait(()=>{const a=JSON.parse(get('research-native-pan')||'null');return a&&Math.abs(a[0]-before[0])>2&&Math.abs(a[1]-before[1])>2;},'native graph pans both axes');
 const surface=document.getElementById('mindustry-game-surface'),resize=Number(get('resize-count'));
 surface.style.width=narrow?'41%':'82%';surface.style.marginLeft='7%';
 await wait(()=>Number(get('resize-count'))>resize&&get('container-match')==='true','dialog follows portal resize');
 const frame=Number(get('cpu-sample'));await wait(()=>Number(get('cpu-sample'))>=frame+2,'live modal after resize');
 headers();
 await click('back');await wait(()=>get('research-open')==='no','original Back');
 await open();
 window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,code:'Escape',key:'Escape'}));await sleep(150);window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,code:'Escape',key:'Escape'}));
 await wait(()=>get('research-open')==='no','native Escape');
 if(campaign){const old=Number(get('cpu-sample'));await wait(()=>Number(get('cpu-sample'))>=old+2,'live game after research');if(get('campaign-state')!=='playing')throw Error('Campaign did not remain playing');}
 // Leave the original dialog visible in the evidence screenshot.
 await open();
 r.setAttribute('data-mindustry-native-research-test','passed');
 return 'Original research roots, native spend, graph input, Back/Escape/reopen and portal resize passed';
})()

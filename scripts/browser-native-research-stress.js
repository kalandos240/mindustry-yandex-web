(async()=>{
 const r=document.documentElement,c=document.getElementById('mindustry-canvas');
 const get=k=>r.getAttribute('data-mindustry-'+k),sleep=ms=>new Promise(f=>setTimeout(f,ms));
 const wait=async(f,label)=>{for(let i=0;i<260;i++){if(get('web')==='error')throw Error(get('error'));if(f())return;await sleep(100);}throw Error('Native ResearchDialog: '+label);};
 let seq=0;
 const tap=async(x,y)=>{const id='native-research-'+(++seq);r.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x,y}));await wait(()=>get('cdp-click-ack')===id,'trusted click');};
 const click=async key=>{await wait(()=>get('native-research-'+key+'-exposed')==='yes',key+' exposed');const a=JSON.parse(get('native-research-'+key)),b=c.getBoundingClientRect();if(a[0]<-.5||a[1]<-.5||a[2]>b.width+.5||a[3]>b.height+.5)throw Error('Native research clipped '+key+': '+a);await tap(b.left+(a[0]+a[2])/2,b.bottom-(a[1]+a[3])/2);};
 const campaign=new URLSearchParams(location.search).has('mindustryCampaignSmoke');
 await wait(()=>get('research-implementation')==='original-ResearchDialog','original class installed');
 if(campaign)await wait(()=>get('campaign-state')==='playing'&&get('campaign-save')==='valid','actual campaign and sector save');
 if(!campaign&&get('main-menu-layout')!=='mobile-grid'){
  await wait(()=>get('main-menu-database-group-x'),'Database group');const b=c.getBoundingClientRect();await tap(b.left+Number(get('main-menu-database-group-x')),b.bottom-Number(get('main-menu-database-group-y')));
 }
 const prefix=campaign?'hud-research':'main-menu-research';
 const open=async()=>{await wait(()=>get(prefix+'-x')&&get(prefix+'-y'),'native research entry');const b=c.getBoundingClientRect();await tap(b.left+Number(get(prefix+'-x')),b.bottom-Number(get(prefix+'-y')));await wait(()=>get('research-open')==='yes'&&get('native-research-view'),'original dialog visible');};
 const narrow=new URLSearchParams(location.search).has('nativeResearchNarrow');
 if(narrow){const surface=document.getElementById('mindustry-game-surface'),old=Number(get('resize-count'));surface.style.width='50%';surface.style.marginLeft='7%';await wait(()=>Number(get('resize-count'))>old&&get('container-match')==='true','initial narrow surface');}
 await open();
 await wait(()=>Number(get('research-tree-visible-nodes'))>2,'native visible nodes');
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
  await wait(()=>get('native-research-copper')==='500','real core inventory fixture');
  if(get('native-research-conveyor-unlocked')!=='no')throw Error('Conveyor must start locked');
  const before=Number(get('native-research-copper'));
  await click('conveyor');
  if(get('input-mode')==='mobile')await click('invest');
  await wait(()=>get('native-research-conveyor-unlocked')==='yes','native spend unlocks Conveyor');
  const copper=Number(get('native-research-copper')),progress=Number(get('native-research-progress'));
  if(!(copper<before&&before-copper===progress&&progress>0))throw Error('Original research cost mismatch '+before+' -> '+copper+' progress '+progress);
 }
 const before=JSON.parse(get('research-native-pan')),b=c.getBoundingClientRect(),id='native-research-drag';
 r.setAttribute('data-mindustry-cdp-drag-request',JSON.stringify({id,x0:b.left+b.width*.65,y0:b.top+b.height*.45,x1:b.left+b.width*.4,y1:b.top+b.height*.65,steps:12}));
 await wait(()=>get('cdp-drag-ack')===id,'trusted native graph drag');
 await wait(()=>{const a=JSON.parse(get('research-native-pan')||'null');return a&&Math.abs(a[0]-before[0])>2&&Math.abs(a[1]-before[1])>2;},'native graph pans both axes');
 const surface=document.getElementById('mindustry-game-surface'),resize=Number(get('resize-count'));
 surface.style.width=narrow?'41%':'82%';surface.style.marginLeft='7%';
 await wait(()=>Number(get('resize-count'))>resize&&get('container-match')==='true','dialog follows portal resize');
 const frame=Number(get('cpu-sample'));await wait(()=>Number(get('cpu-sample'))>frame,'live modal after resize');
 await click('back');await wait(()=>get('research-open')==='no','original Back');
 await open();
 window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,code:'Escape',key:'Escape'}));await sleep(150);window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,code:'Escape',key:'Escape'}));
 await wait(()=>get('research-open')==='no','native Escape');
 if(campaign){const old=Number(get('cpu-sample'));await wait(()=>Number(get('cpu-sample'))>old,'live game after research');if(get('campaign-state')!=='playing')throw Error('Campaign did not remain playing');}
 // Leave the original dialog visible in the evidence screenshot.
 await open();
 r.setAttribute('data-mindustry-native-research-test','passed');
 return 'Original research roots, native spend, graph input, Back/Escape/reopen and portal resize passed';
})()

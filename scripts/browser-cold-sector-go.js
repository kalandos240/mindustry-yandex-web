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
 await click('planet-hit-launch');
 await wait(()=>v('campaign-resume')==='ready'&&v('campaign-state')==='playing','saved sector loaded');
 const frame=Number(v('campaign-update-id'));await wait(()=>Number(v('campaign-update-id'))>frame,'world simulation continues');
 r.setAttribute('data-mindustry-cold-sector-go','passed');return 'Native saved-sector Go resumes after a cold browser restart';
})()
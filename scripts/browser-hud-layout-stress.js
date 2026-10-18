(async()=>{
  const r=document.documentElement,c=document.getElementById('mindustry-canvas');
  const get=k=>r.getAttribute('data-mindustry-'+k), sleep=ms=>new Promise(f=>setTimeout(f,ms));
  const wait=async(f,label)=>{for(let i=0;i<240;i++){if(get('web')==='error')throw Error(get('error'));if(f())return;await sleep(100);}throw Error('HUD layout: '+label);};
  const alive=async()=>{const before=Number(get('cpu-sample')),world=Number(get('local-map-frames'));await wait(()=>Number(get('cpu-sample'))>before&&Number(get('local-map-frames'))>world,'live game frames and simulation');};
  const rect=k=>JSON.parse(get('hud-layout-'+k)||'null');
  const check=()=>{
    const b=c.getBoundingClientRect(),keys=['minimap','waves','health','items','placement'];
    if(get('hud-layout-guardian-exposed')==='yes')keys.push('guardian');
    for(const k of keys){const a=rect(k);if(!a||!(a[0]>=-0.5&&a[1]>=-0.5&&a[2]<=b.width+0.5&&a[3]<=b.height+0.5&&a[2]>a[0]&&a[3]>a[1]))throw Error('HUD clipped '+k+': '+a+' in '+b.width+'x'+b.height);}
    const itemBounds=rect('items');
    for(const k of ['resource-icon','resource-count']){
      const a=rect(k);
      if(!a||a[0]<itemBounds[0]-0.5||a[1]<itemBounds[1]-0.5||a[2]>itemBounds[2]+0.5||a[3]>itemBounds[3]+0.5)
        throw Error('First native resource clipped inside item pane '+k+': '+a+' / '+itemBounds);
    }
    for(let i=0;i<keys.length;i++)for(let j=i+1;j<keys.length;j++){
      const a=rect(keys[i]),d=rect(keys[j]);
      if(Math.min(a[2],d[2])-Math.max(a[0],d[0])>1&&Math.min(a[3],d[3])-Math.max(a[1],d[1])>1)throw Error('HUD overlap '+keys[i]+'/'+keys[j]+': '+a+' / '+d);
    }
  };
  let seq=0;
  const click=async key=>{
    await wait(()=>get('hud-layout-'+key+'-exposed')==='yes',key+' native hit receiver');
    const a=rect(key),b=c.getBoundingClientRect();
    if(!a||a[0]<-0.5||a[1]<-0.5||a[2]>b.width+0.5||a[3]>b.height+0.5)throw Error('HUD action clipped '+key+': '+a);
    const id='hud-layout-'+(++seq);r.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x:b.left+(a[0]+a[2])/2,y:b.bottom-(a[1]+a[3])/2}));
    await wait(()=>get('cdp-click-ack')===id,'native click '+key);
  };
  await wait(()=>get('local-map-state')==='playing'&&get('hud-layout-placement'),'real campaign HUD');
  await wait(()=>get('hud-layout-fixture')==='ready','real resource and boss fixture');
  const surface=document.getElementById('mindustry-game-surface');
  if(new URLSearchParams(location.search).get('hudNarrow')==='1'){
    const old=Number(get('resize-count'));surface.style.width='50%';surface.style.marginLeft='7%';
    await wait(()=>Number(get('resize-count'))>old&&get('container-match')==='true','narrow Scene resize');
  }
  await alive();check();
  await click('category');await alive();
  const categoryBefore=rect('category');
  await click('conveyor');await wait(()=>get('hud-layout-block')==='conveyor','original conveyor selection');
  await alive();check();
  const categoryAfter=rect('category');
  if(categoryBefore.some((v,i)=>Math.abs(v-categoryAfter[i])>1))throw Error('Native category moved under the pointer after block selection');
  // The stock info/cost panel expands after selection; fit the complete actor
  // graph again after a genuine container resize and verify transformed input.
  const old=Number(get('resize-count'));surface.style.height=get('input-mode')==='mobile'?'45%':'85%';
  if(new URLSearchParams(location.search).get('hudNarrow')==='1')surface.style.width='41%';
  await wait(()=>Number(get('resize-count'))>old&&get('container-match')==='true','short portal resize');
  await alive();check();
  await click('category');await alive();check();
  await click('minimap');await wait(()=>get('hud-layout-map-open')==='yes','original full-screen minimap');
  window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,code:'Escape',key:'Escape'}));await sleep(150);
  window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,code:'Escape',key:'Escape'}));
  await wait(()=>get('hud-layout-map-open')==='no','native minimap Escape');
  await alive();check();
  r.setAttribute('data-mindustry-hud-layout-test','passed');
  return 'Native HUD non-overlap, stock block selection, scaled input, resize and full minimap passed';
})()

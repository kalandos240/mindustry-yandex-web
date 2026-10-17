// Real Chrome desktop mouse gesture on the original Mindustry TechTree
// graph. The Scene handles pointerDown/move/up; no Java scroll setters here.
(async () => {
  const root=document.documentElement;
  const canvas=document.getElementById('mindustry-canvas');
  const val=k=>root.getAttribute('data-mindustry-'+k);
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const wait=async(fn,msg)=>{
    for(let i=0;i<160;i++){
      if(val('web')==='error')throw Error('Research runtime failed: '+msg);
      if(fn())return;
      await sleep(100);
    }
    throw Error('Real mouse TechTree drag failed: '+msg+
      '; pane='+val('research-mouse-pan')+
      ', scroll='+val('research-mouse-pan-x')+','+val('research-mouse-pan-y'));
  };
  await wait(()=>val('main-menu-page')==='home','native main menu');
  // The Arc menu reports hitboxes on its 16-frame pointer cadence. The
  // earlier test read Number(null) === 0 immediately after DOM-ready and
  // incorrectly called the still-unsampled Research button "clipped".
  await wait(()=>root.hasAttribute('data-mindustry-main-menu-research-x') &&
    root.hasAttribute('data-mindustry-main-menu-research-y'),
    'native desktop Research Arc button sampled');
  const rect=canvas.getBoundingClientRect();
  const mx=Number(val('main-menu-research-x')), my=Number(val('main-menu-research-y'));
  if(!(mx>0&&my>0&&mx<rect.width&&my<rect.height))
    throw Error('Stock research control outside portal canvas: button '+
      mx+','+my+' vs canvas '+rect.width+'x'+rect.height);
  const clickId='research-mouse-open';
  root.setAttribute('data-mindustry-cdp-click-ack','');
  root.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({
    id:clickId,x:rect.left+mx,y:rect.bottom-my
  }));
  await wait(()=>val('cdp-click-ack')===clickId,'native Research click receipt');
  await wait(()=>val('research-open')==='yes','stock TechTree opened');
  // Drag up-left over the canvas itself; this must pan both axes, including
  // if the mouse initially pressed atop a tech-node image.
  const x0=rect.left+rect.width*.69, y0=rect.top+rect.height*.69;
  const x1=rect.left+rect.width*.42, y1=rect.top+rect.height*.39;
  for(const [x,y] of [[x0,y0],[x1,y1]]){
    if(document.elementFromPoint(x,y)!==canvas)
      throw Error('Research drag blocked by external portal DOM');
  }
  const dragId='research-real-left-drag';
  root.setAttribute('data-mindustry-cdp-drag-ack','');
  root.setAttribute('data-mindustry-cdp-drag-request',JSON.stringify({
    id:dragId,x0,y0,x1,y1,steps:12
  }));
  await wait(()=>val('cdp-drag-ack')===dragId ||
    (val('cdp-drag-ack')||'').startsWith('ERROR:'),
    'Chrome trusted left-mouse drag receipt');
  if(val('cdp-drag-ack')!==dragId)throw Error('Native research drag rejected: '+val('cdp-drag-ack'));
  await wait(()=>val('research-mouse-pan')==='menu' &&
    Number(val('research-mouse-pan-x'))>5 &&
    Number(val('research-mouse-pan-y'))>5,'both-axis graph panning');
  const previous=Number(val('cpu-sample'));
  await wait(()=>Number(val('cpu-sample'))>previous,'TeaVM frame after dragging');
  root.setAttribute('data-mindustry-research-mouse-drag','passed');
  return 'Original Research tree moves with real pressed left mouse';
})()

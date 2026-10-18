// FULL user path, not ?mindustryCampaignSmoke or direct Java launch:
// native HOME > Play > Serpulo/Erekir, or original 3D PlanetDialog > Launch,
// then the real .msav World must load, simulation must tick, and Save must
// resume through another real user click.
(async () => {
  const root=document.documentElement;
  const canvas=document.getElementById('mindustry-canvas');
  const params=new URLSearchParams(location.search);
  const scenario=params.get('mindustryUiCampaignStart');
  if(!['direct-serpulo','direct-erekir','planet-serpulo','planet-erekir'].includes(scenario))
    throw Error('Missing native full-campaign scenario');
  const planet=scenario.endsWith('erekir')?'erekir':'serpulo';
  const preset=planet==='erekir'?'onset':'groundZero';
  const get=k=>root.getAttribute('data-mindustry-'+k);
  const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const wait=async(fn,label)=>{
    for(let i=0;i<240;i++){
      if(get('web')==='error')
        throw Error('Mindustry web crashed at '+label+': '+(get('error')||'unknown')+
          '; planet-frame='+get('planet-frame-phase')+
          '; planet-failure='+get('planet-frame-failure')+
          '; module='+get('module-phase'));
      if(fn())return;
      await sleep(100);
    }
    throw Error('Real campaign button failed '+scenario+' at '+label+
      '; page='+get('main-menu-page')+'; sector='+get('campaign-preset')+
      '; launch='+get('planet-launch-sector')+
      '; launch-error='+get('planet-launch-error')+
      '; map='+get('planet-start-sector-planet'));
  };
  const heartbeat=async(label)=>{
    await wait(()=>Number(get('cpu-sample'))>0,label+' initial TeaVM sample');
    const previous=Number(get('cpu-sample'));
    await wait(()=>Number(get('cpu-sample'))>previous,label+' live scheduler');
  };
  let clicks=0;
  const click=async(prefix,label)=>{
    await wait(()=>root.hasAttribute('data-mindustry-'+prefix+'-x'),label+' native Arc position');
    const box=canvas.getBoundingClientRect();
    const x=Number(get(prefix+'-x')),y=Number(get(prefix+'-y'));
    if(!(x>0&&y>0&&x<box.width&&y<box.height))
      throw Error(label+' outside game canvas: '+x+','+y+' vs '+box.width+'x'+box.height);
    const p={x:box.left+x,y:box.bottom-y};
    if(document.elementFromPoint(p.x,p.y)!==canvas)
      throw Error(label+' intercepted outside game canvas');
    const id='real-campaign-'+(++clicks);
    root.setAttribute('data-mindustry-cdp-click-ack','');
    root.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,...p}));
    await wait(()=>{
      const ack=get('cdp-click-ack')||'';
      return ack===id || ack.startsWith('ERROR:');
    },label+' native Chrome mouse/touch');
    if(get('cdp-click-ack')!==id)throw Error(label+' Chrome input bridge failed');
  };
  await wait(()=>get('main-menu-page')==='home','stock Home');
  await heartbeat('Home');
  await click('main-menu-play','Home > Play');
  if(get('input-mode')!=='mobile'){
    await wait(()=>get('main-menu-page')==='play','actual Play submenu');
    await click('main-menu-planets','Original Campaign entry');
  }
  {
    if(scenario.startsWith('direct-')){
      // The combined recovery selector was removed. Both original sector
      // launch paths must still work after unrelated deferred-map failure.
      root.setAttribute('data-mindustry-campaign-assets','error');
      await heartbeat('unrelated campaign warmup failure');
    }
    await wait(()=>get('vanilla-planet-open')==='yes','actual 3D globe opened');
    await wait(()=>get('vanilla-planet-frame')==='rendered','3D globe rendered');
    await wait(()=>get('planet-start-sector-ready')==='yes',
      'stock sector panel initialized on 3D first open');
    await wait(()=>get('planet-first-chooser')==='closed','no obscuring first-run popup');
    if(planet==='erekir'){
      await click('planet-hit-erekir','native Erekir planet switch');
      await wait(()=>get('planet-start-sector-planet')==='erekir',
        'original Erekir sector panel retargeted');
    }else{
      await wait(()=>get('planet-start-sector-planet')==='serpulo',
        'original Serpulo sector panel');
    }
    await wait(()=>get('planet-hit-launch-disabled')==='no',
      'stock launch/resume sector button enabled');
    await wait(()=>get('planet-hit-launch-exposed')==='yes',
      'stock launch/resume button receives actual Arc Scene input');
    await click('planet-hit-launch','original Sector Launch/Resume');
  }
  await wait(()=>get('campaign-state')==='playing','genuine World started');
  await wait(()=>get('campaign-planet')===planet&&get('campaign-preset')===preset,
    'selected campaign start sector world loaded');
  const size=get('campaign-world')||'';
  const m=/^(\d+)x(\d+)$/.exec(size);
  if(!m||Number(m[1])<16||Number(m[2])<16)
    throw Error('Campaign has no real map, world='+size);
  const initial=Number(get('cpu-sample'));
  await wait(()=>Number(get('cpu-sample'))>initial+1,
    'live original World advances after real native menu launch');
  root.setAttribute('data-mindustry-ui-campaign-start','passed');
  root.setAttribute('data-mindustry-ui-campaign-scenario',scenario);
  root.setAttribute('data-mindustry-ui-campaign-clicks',String(clicks));
  return 'REAL Play > '+scenario+' -> original '+size+' campaign World + ticking TeaVM';
})()

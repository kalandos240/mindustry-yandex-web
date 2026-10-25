/* Real UI actions only. The read-only Java probe is installed separately. */
(async()=>{
    const root=document.documentElement,canvas=document.getElementById('mindustry-canvas');
    const value=name=>root.getAttribute('data-mindustry-'+name);
    const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
    const wait=async(predicate,label)=>{
        for(let attempt=0;attempt<900;attempt++){
            if(value('web')==='error'||value('campaign-launch-failure'))throw Error(label+': '+(value('error')||value('campaign-launch-failure')));
            if(globalThis.__mindustryErrorReport)throw Error(label+': '+JSON.stringify(globalThis.__mindustryErrorReport));
            if(predicate())return;
            await sleep(100);
        }
        throw Error('Timed out: '+label);
    };
    let sequence=0;
    const click=async prefix=>{
        await wait(()=>value(prefix+'-x')&&value(prefix+'-y'),prefix+' hitbox');
        const rect=canvas.getBoundingClientRect(),id='portal-'+(++sequence);
        await globalThis.__portalNativeClick({id,prefix,x:rect.left+Number(value(prefix+'-x')),y:rect.bottom-Number(value(prefix+'-y'))});
    };
    if(value('main-menu-layout')!=='mobile-grid'){
        await click('main-menu-play');await wait(()=>value('main-menu-play-submenu')==='open','Play submenu');
    }
    await click(value('main-menu-layout')==='mobile-grid'?'main-menu-play':'main-menu-planets');
    await wait(()=>value('vanilla-planet-open')==='yes','original globe');
    let sample=Number(value('cpu-sample'));await wait(()=>Number(value('cpu-sample'))>=sample+2,'globe layout');
    const planet=new URLSearchParams(location.search).get('savedPlanet')||'serpulo';
    await click('planet-hit-'+planet);
    await wait(()=>value('planet-start-sector-planet')===planet&&value('planet-hit-launch-exposed')==='yes','selected planet action');
    sample=Number(value('cpu-sample'));await wait(()=>Number(value('cpu-sample'))>=sample+2,'selected action layout');
    await click('planet-hit-launch');
    await wait(()=>globalThis.__mindustryQaSnapshot?.().playing===true&&globalThis.__mindustryQaSnapshot().campaignActive===true,'actual playing campaign');
    const before=globalThis.__mindustryQaSnapshot();
    await wait(()=>{
        const now=globalThis.__mindustryQaSnapshot();
        return now.playing&&!now.paused&&now.tick>before.tick+30&&BigInt(now.updateId)>BigInt(before.updateId)+20n;
    },'actual simulation after native Go');
    return {before,after:globalThis.__mindustryQaSnapshot()};
})()

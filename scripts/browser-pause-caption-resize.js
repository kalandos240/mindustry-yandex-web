(async()=>{
 const r=document.documentElement,c=document.getElementById('mindustry-canvas'),surface=document.getElementById('mindustry-game-surface');
 const get=k=>r.getAttribute('data-mindustry-'+k),sleep=ms=>new Promise(f=>setTimeout(f,ms));
 const wait=async(f,label)=>{for(let i=0;i<180;i++){if(get('web')!=='ready')throw Error(get('web-error'));if(f())return;await sleep(100);}throw Error('Pause caption resize: '+label);};
 const alive=async()=>{const n=Number(get('cpu-sample'));await wait(()=>Number(get('cpu-sample'))>n,'live Java frames');};
 const escape=async()=>{window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,code:'Escape',key:'Escape'}));await sleep(180);window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,code:'Escape',key:'Escape'}));};
 const modal=k=>(get('vanilla-modal-visible')||'').split(',').includes(k);
 let seq=0;
 const click=async action=>{await wait(()=>get('pause-button-'+action+'-x')!==null,'native '+action+' bounds');const b=c.getBoundingClientRect(),x=Number(get('pause-button-'+action+'-x')),y=Number(get('pause-button-'+action+'-y'));if(!(x>0&&y>0&&x<b.width&&y<b.height))throw Error('Caption action clipped '+action);const id='caption-'+(++seq);r.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x:b.left+x,y:b.bottom-y}));await wait(()=>get('cdp-click-ack')===id,'trusted '+action);};
 const captions=async()=>{for(const k of ['resume','settings','save','schematics','controls','saves','menu']){await wait(()=>get('pause-caption-'+k)!==null,'rendered '+k);const a=JSON.parse(get('pause-caption-'+k));if(!a.contained||a.lines<1||a.lines>2||(!/\s/.test(a.text)&&a.lines!==1))throw Error('Broken caption '+k+': '+JSON.stringify(a));}};
 const resize=async(width,height)=>{const old=Number(get('resize-count'));surface.style.width=width;surface.style.height=height;await wait(()=>Number(get('resize-count'))>old&&get('container-match')==='true','portal '+width+'x'+height);await alive();await alive();await captions();};
 await wait(()=>get('local-map-state')==='playing','loaded world');await alive();
 await escape();await wait(()=>get('pause-dialog-visible')==='yes','native Pause');await alive();await captions();
 await resize('320px','320px');
 await click('settings');await wait(()=>modal('settings'),'stacked Settings listener');await alive();
 await escape();await wait(()=>!modal('settings'),'close Settings');await alive();
 await resize('100%','100%');
 await click('controls');await wait(()=>modal('controls'),'restored horizontal Controls listener');await alive();
 await escape();await wait(()=>!modal('controls'),'close Controls');await alive();await captions();
 await click('resume');await wait(()=>get('local-map-pause-state')==='resumed','Resume listener after both reflows');await alive();
 await escape();await wait(()=>get('pause-dialog-visible')==='yes','reopen after successful Resume');await alive();await captions();
 r.setAttribute('data-mindustry-caption-resize-test','passed');return 'English captions and native callbacks survive wide/320/wide reflow';
})()

(function installBrowserErrorReport(){
    'use strict';
    const root=document.documentElement;
    let panel;
    function javaValue(error){
        if(!error || (typeof error!=='object' && typeof error!=='function')) return null;
        const key=Object.getOwnPropertySymbols(error).find(s=>s.description==='javaException');
        return key ? error[key] : null;
    }
    function describe(error){
        const chain=[],seen=new Set();
        let value=javaValue(error);
        while(value && !seen.has(value) && chain.length<12){
            seen.add(value);
            const fields=Object.entries(value);
            const message=fields.find(([name])=>name.startsWith('$message'))?.[1];
            chain.push({type:value.constructor?.name || 'JavaException',
                message:typeof message==='string'?message:message?.$nativeString || '',
                stack:value.$jsException?.stack || ''});
            value=fields.find(([name])=>name.startsWith('$cause'))?.[1];
        }
        if(!chain.length) chain.push({type:error?.name || 'Error',message:String(error?.message || error),stack:error?.stack || ''});
        return {format:'mindustry-web-error-v1',phase:root.getAttribute('data-mindustry-campaign-phase'),
            framePhase:root.getAttribute('data-mindustry-frame-stage'),
            sector:root.getAttribute('data-mindustry-campaign-preset'),
            viewport:{width:innerWidth,height:innerHeight},causes:chain};
    }
    function capture(error){
        try{
            const report=describe(error);
            globalThis.__mindustryErrorReport=report;
            root.setAttribute('data-mindustry-error-causes',JSON.stringify(report.causes.map(c=>({type:c.type,message:c.message}))));
            console.error('Mindustry error report:',JSON.stringify(report));
            if(panel) return;
            const ru=new URLSearchParams(location.search).get('lang')==='ru' || /^ru\b/i.test(navigator.language || '');
            panel=document.createElement('div');panel.id='mindustry-error-actions';
            panel.style.cssText='position:fixed;z-index:2147483647;left:12px;right:12px;bottom:12px;display:flex;justify-content:center;gap:8px;flex-wrap:wrap;padding:12px;background:#20242cf2;color:white;font:16px sans-serif';
            const label=document.createElement('span');label.textContent=ru?'Ошибка игры. Можно скопировать отчёт и перезагрузить страницу.':'Game error. Copy the report or reload the page.';panel.appendChild(label);
            const copy=document.createElement('button');copy.textContent=ru?'Скопировать отчёт':'Copy report';
            copy.onclick=async function(){
                const text=JSON.stringify(globalThis.__mindustryErrorReport,null,2);
                try{
                    if(!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
                    await navigator.clipboard.writeText(text);
                    copy.textContent=ru?'Скопировано':'Copied';
                }catch(ignored){
                    const box=document.createElement('textarea');box.value=text;box.setAttribute('readonly','');
                    box.style.cssText='width:100%;height:110px';panel.appendChild(box);box.focus();box.select();
                    try{document.execCommand('copy');}catch(ignored){}
                    copy.textContent=ru?'Отчёт выделен':'Report selected';
                }
            };
            const reload=document.createElement('button');reload.textContent=ru?'Перезагрузить':'Reload';reload.onclick=()=>location.reload();
            panel.appendChild(copy);panel.appendChild(reload);document.body.appendChild(panel);
        }catch(ignored){}
    }
    globalThis.__mindustryCaptureError=capture;
    addEventListener('error',event=>{
        if(javaValue(event.error) || root.getAttribute('data-mindustry-web')==='error') capture(event.error || event.message);
    },true);
    addEventListener('unhandledrejection',event=>{
        if(javaValue(event.reason)) capture(event.reason);
    });
})();

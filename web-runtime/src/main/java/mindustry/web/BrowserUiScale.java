package mindustry.web;

import arc.Core;
import arc.scene.ui.layout.Scl;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Apply the saved UI scale before actors/fonts are built, with an unscaled recovery UI. */
public final class BrowserUiScale{
    @JSFunctor
    private interface Action extends JSObject{ void run(); }

    private BrowserUiScale(){}

    public static void install(){
        int percent = Math.max(25, Math.min(300, Core.settings.getInt("uiscale", 100)));
        if(percent != Core.settings.getInt("uiscale", 100)) Core.settings.put("uiscale", percent);
        Scl.setProduct(percent / 100f);
        boolean needsConfirmation = Core.settings.getBool("uiscalechanged", false)
            || Core.settings.getInt("web-uiscale-confirmed", 100) != percent;
        if(needsConfirmation){
            showConfirmation(percent, () -> persist(percent), () -> {
                persist(100);
                reloadAfterFlush();
            });
        }
    }

    private static void persist(int percent){
        Core.settings.put("uiscale", percent);
        Core.settings.put("web-uiscale-confirmed", percent);
        Core.settings.put("uiscalechanged", false);
        Core.settings.forceSave();
    }

    @JSBody(params={"percent", "keep", "reset"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-ui-scale',String(percent));
        let shown=false;
        const begin=function(){
            const state=root.getAttribute('data-mindustry-web');
            if(shown || state!=='ready' && state!=='error') return;
            shown=true;observer.disconnect();
            const ru=new URLSearchParams(location.search).get('lang')==='ru' || /^ru\\b/i.test(navigator.language || '');
            const panel=document.createElement('div');panel.id='mindustry-ui-scale-confirm';
            panel.style.cssText='position:fixed;z-index:2147483646;top:12px;left:12px;right:12px;padding:12px;display:flex;gap:8px;flex-wrap:wrap;justify-content:center;align-items:center;background:#20242cf5;color:white;font:16px sans-serif';
            const text=document.createElement('span');
            text.textContent=ru?'Оставить масштаб '+percent+'%? Через 15 секунд вернётся 100%.':'Keep UI scale '+percent+'%? It will reset to 100% in 15 seconds.';
            panel.appendChild(text);
            let settled=false;
            const finish=function(action){if(settled)return;settled=true;clearTimeout(timer);panel.remove();action();};
            const yes=document.createElement('button');yes.textContent=ru?'Оставить':'Keep';yes.style.cssText='min-height:44px;padding:8px';yes.onclick=function(){finish(keep);};
            const no=document.createElement('button');no.textContent=ru?'Вернуть 100%':'Reset to 100%';no.style.cssText='min-height:44px;padding:8px';no.onclick=function(){finish(reset);};
            panel.appendChild(yes);panel.appendChild(no);document.body.appendChild(panel);
            const timer=setTimeout(function(){finish(reset);},15000);
        };
        const observer=new MutationObserver(begin);observer.observe(root,{attributes:true,attributeFilter:['data-mindustry-web']});begin();
        """)
    private static native void showConfirmation(int percent, Action keep, Action reset);

    @JSBody(script="""
        Promise.resolve(globalThis.__mindustryStorage.flush()).then(function(){location.reload();},function(error){
            console.error('UI scale reset could not be saved:',error);
            const status=document.getElementById('mindustry-web-status');
            if(status){status.textContent='UI scale reset could not be saved; reload when storage is available.';status.style.opacity='1';}
        });
        """)
    private static native void reloadAfterFlush();
}

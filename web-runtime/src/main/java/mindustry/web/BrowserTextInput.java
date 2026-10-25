package mindustry.web;

import arc.*;
import arc.backend.web.WebInput;
import org.teavm.jso.*;

/** Browser-native keyboard, paste and accessible text dialog for stock Arc UI. */
public final class BrowserTextInput{
    @JSFunctor
    private interface Result extends JSObject{
        void finish(boolean accepted, String text);
    }

    private BrowserTextInput(){}

    public static void install(WebInput input){
        input.setTextInputHandler(request -> {
            if(input.isShowingTextInput()) return;
            input.setShowingTextInput(true);
            show(request.title, request.message, request.text, request.multiline,
                request.numeric, request.allowEmpty, request.maxLength, false,
                Core.bundle.get("ok"), Core.bundle.get("cancel"), Core.bundle.get("copied"),
                (accepted, text) -> Core.app.post(() -> {
                    input.setShowingTextInput(false);
                    if(accepted) request.accepted.get(text);
                    else request.canceled.run();
                }));
        });
    }

    public static void copy(String text){
        WebInput input = (WebInput)Core.input;
        if(input.isShowingTextInput()) return;
        input.setShowingTextInput(true);
        Core.app.setClipboardText(text);
        show(Core.bundle.get("copy.clipboard"), "", text, true, false, true, -1, true,
            Core.bundle.get("copy.clipboard"), Core.bundle.get("back"), Core.bundle.get("copied"),
            (accepted, value) -> Core.app.post(() -> input.setShowingTextInput(false)));
    }

    @JSBody(params={"title","message","value","multiline","numeric","allowEmpty","maxLength","copyMode","acceptLabel","cancelLabel","copiedLabel","result"},script="""
        var previous=document.getElementById('mindustry-text-input');
        if(previous) return;
        var canvas=document.getElementById('mindustry-canvas');
        if(canvas && canvas.__mindustryReleaseInput) canvas.__mindustryReleaseInput('text-input');
        var overlay=document.createElement('div');overlay.id='mindustry-text-input';
        overlay.setAttribute('role','dialog');overlay.setAttribute('aria-modal','true');
        overlay.setAttribute('aria-label',title||'');
        overlay.style.cssText='position:fixed;inset:0;z-index:2147483646;background:#000a;display:flex;align-items:center;justify-content:center;padding:12px;box-sizing:border-box';
        var panel=document.createElement('div');
        panel.style.cssText='width:500px;max-width:100%;max-height:90%;overflow:auto;background:#20242c;color:white;padding:18px;font:16px sans-serif;box-sizing:border-box';
        var label=document.createElement('div');label.textContent=title||'';label.style.marginBottom='12px';panel.appendChild(label);
        var hint=document.createElement('div');hint.textContent=message||'';panel.appendChild(hint);
        var field=document.createElement(multiline?'textarea':'input');
        field.value=value||'';field.readOnly=copyMode;
        field.setAttribute('aria-label',message||title||'');
        field.style.cssText='width:100%;box-sizing:border-box;font:16px monospace;margin:10px 0;min-height:42px';
        if(multiline) field.style.height='160px';
        // Numeric input is sanitized *after* the input event. Native maxlength\n        // would discard valid trailing digits before non-digits can be removed.\n        if(maxLength>=0 && !numeric) field.maxLength=maxLength;
        if(numeric) field.inputMode='numeric';
        panel.appendChild(field);
        var accept=document.createElement('button');accept.textContent=acceptLabel;
        var cancel=document.createElement('button');cancel.textContent=cancelLabel;
        accept.style.cssText=cancel.style.cssText='min-height:44px;margin:4px;padding:8px 14px;font:16px sans-serif';
        var done=false;
        var finish=function(ok){
            if(done) return;
            if(ok && !allowEmpty && !field.value.length) return;
            done=true;overlay.remove();
            if(canvas && canvas.__mindustryReleaseInput) canvas.__mindustryReleaseInput('text-input-close');
            if(canvas) canvas.focus();
            result(ok,field.value);
        };
        var refresh=function(){
            if(numeric) field.value=field.value.replace(/[^0-9]/g,'');
            if(maxLength>=0 && field.value.length>maxLength) field.value=field.value.slice(0,maxLength);
            accept.disabled=!copyMode && !allowEmpty && !field.value.length;
        };
        field.addEventListener('input',refresh);refresh();
        accept.onclick=function(){
            if(!copyMode){finish(true);return;}
            field.focus();field.select();
            if(navigator.clipboard && navigator.clipboard.writeText){
                navigator.clipboard.writeText(field.value).then(function(){accept.textContent=copiedLabel;},function(){accept.textContent=acceptLabel+' (Ctrl+C)';});
            }else{
                var copied=false;try{copied=document.execCommand('copy');}catch(ignored){}
                accept.textContent=copied?copiedLabel:acceptLabel+' (Ctrl+C)';
            }
        };
        cancel.onclick=function(){finish(false);};
        overlay.addEventListener('keydown',function(event){
            event.stopPropagation();
            if(event.isComposing) return;
            if(event.key==='Escape'){event.preventDefault();finish(false);}
            else if(event.key==='Enter' && !copyMode && (!multiline || event.ctrlKey || event.metaKey)){event.preventDefault();finish(true);}
        });
        ['keyup','pointerdown','pointerup','click','wheel'].forEach(function(name){overlay.addEventListener(name,function(event){event.stopPropagation();});});
        panel.appendChild(accept);panel.appendChild(cancel);overlay.appendChild(panel);document.body.appendChild(overlay);
        setTimeout(function(){field.focus();field.select();},0);
        """)
    private static native void show(String title, String message, String value, boolean multiline,
        boolean numeric, boolean allowEmpty, int maxLength, boolean copyMode,
        String acceptLabel, String cancelLabel, String copiedLabel, Result result);
}

package mindustry.web;

import arc.*;
import arc.files.*;
import arc.util.*;
import mindustry.ui.FileChooser.FileChooserParams;
import org.teavm.jso.*;

import java.util.ArrayList;

/**
 * Browser file import/export adapter for upstream Mindustry FileChooser.
 * The file bytes are bridged to ordinary local Fi handles: original
 * Schematics.read/write, map import and other editor consumers are unchanged.
 */
public final class BrowserFileChooser{
    @JSFunctor
    private interface ImportedFile extends JSObject{
        void accept(String name, byte[] bytes, boolean last);
    }

    private static int uploadIndex;

    private BrowserFileChooser(){}

    public static void show(FileChooserParams params){
        if(params.open){
            ArrayList<Fi> selected = new ArrayList<>();
            StringBuilder accept = new StringBuilder();
            for(String extension : params.extensions){
                if(accept.length() > 0) accept.append(",");
                accept.append(".").append(extension);
            }
            chooseFiles(accept.toString(), params.allowMultiple, (name, bytes, last) -> {
                // Telemetry is deliberately name- and content-free. This lets
                // real Chrome QA tell whether a browser FileReader delivery
                // crossed TeaVM's JS byte[] bridge, and whether stock FileChooser
                // actually dispatched the result to native Schematics/SaveIO.
                markImportPhase("received", bytes == null ? -1 : bytes.length);
                // Browser input is an untrusted file boundary. The extension and size
                // are checked before any parsed .msch/.msav data reaches Mindustry.
                boolean supported = false;
                for(String extension : params.extensions){
                    if(name.toLowerCase(java.util.Locale.ROOT).endsWith("." + extension.toLowerCase(java.util.Locale.ROOT))){
                        supported = true;
                        break;
                    }
                }
                if(supported && bytes != null && bytes.length <= 16 * 1024 * 1024){
                    Fi staged = Core.files.local("tmp/import-" + (++uploadIndex) + "/" + Strings.sanitizeFilename(name));
                    staged.parent().mkdirs();
                    staged.writeBytes(bytes);
                    selected.add(staged);
                    markImportPhase("staged", bytes.length);
                }
                if(last){
                    if(selected.isEmpty()){
                        markImportPhase("rejected", 0);
                        if(mindustry.Vars.ui != null) mindustry.Vars.ui.showInfo("@none.found");
                    }else{
                        try{
                            markImportPhase("dispatch", selected.size());
                            params.handleChooseResult(selected.toArray(new Fi[0]));
                            markImportPhase("handled", selected.size());
                        }finally{
                            // Original Mindustry import callbacks fully read each Fi before
                            // returning. Do not accumulate duplicate uploaded files in
                            // browser-persistent temporary storage.
                            for(Fi file : selected){
                                file.delete();
                                file.parent().delete();
                            }
                        }
                    }
                }
            });
        }else{
            String name = Strings.sanitizeFilename(params.fileName);
            // Export paths are recognized by BrowserFiles and downloaded when
            // the vanilla writer closes the Fi. No server or external URL used.
            Fi staged = Core.files.local("web-exports/" + name);
            staged.parent().mkdirs();
            params.handleChooseResult(staged);
        }
    }

    @JSBody(params={"phase","count"}, script="var r=document.documentElement; r.setAttribute('data-mindustry-browser-native-import-phase',phase); r.setAttribute('data-mindustry-browser-native-import-count',String(count));")
    private static native void markImportPhase(String phase, int count);

    // TeaVM 0.15 JSBody uses an ES5 parser: no arrow functions/async/await.
    // FileReader callbacks serially deliver each validated browser-local upload.
    @JSBody(params={"extensions","multiple","callback"}, script="""
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = extensions;
        input.multiple = multiple;
        input.style.display = 'none';
        document.body.appendChild(input);
        input.addEventListener('cancel',function(){
            if(input.parentNode) input.parentNode.removeChild(input);
            document.documentElement.setAttribute('data-mindustry-browser-file-state','cancelled');
        });
        input.addEventListener('change', function(){
            var files = input.files || [];
            var allowed = extensions.toLowerCase().split(',');
            var filtered = [];
            for(var i = 0; i < files.length; i++){
                var name = files[i].name.toLowerCase();
                for(var j = 0; j < allowed.length; j++){
                    if(name.substr(name.length - allowed[j].length) === allowed[j] && files[i].size <= 16 * 1024 * 1024){
                        filtered.push(files[i]);
                        break;
                    }
                }
            }
            if(!filtered.length){
                if(input.parentNode) input.parentNode.removeChild(input);
                if(files.length) callback('',null,true);
                document.documentElement.setAttribute('data-mindustry-browser-file-state',files.length?'rejected':'cancelled');
                return;
            }
            var next = 0;
            function loadNext(){
                if(next >= filtered.length){
                    if(input.parentNode) input.parentNode.removeChild(input);
                    return;
                }
                var file = filtered[next];
                var last = next === filtered.length - 1;
                var reader = new FileReader();
                reader.onload = function(){
                    try{
                        callback(file.name, new Uint8Array(reader.result), last);
                    }catch(e){
                        document.documentElement.setAttribute('data-mindustry-browser-file-error', String(e));
                    }
                    next++;
                    loadNext();
                };
                reader.onerror = function(){
                    document.documentElement.setAttribute('data-mindustry-browser-file-error', 'FileReader failed: ' + file.name);
                    try{
                        callback(file.name, null, last);
                    }catch(e){
                        document.documentElement.setAttribute('data-mindustry-browser-file-error', String(e));
                    }
                    next++;
                    loadNext();
                };
                reader.readAsArrayBuffer(file);
            }
            loadNext();
        }, false);
        input.click();
        """)
    private static native void chooseFiles(String extensions, boolean multiple, ImportedFile callback);
}

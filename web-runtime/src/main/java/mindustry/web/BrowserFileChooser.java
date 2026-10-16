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
                    String extension = name.substring(name.lastIndexOf('.') + 1);
                    Fi staged = Core.files.local("tmp/import-" + (++uploadIndex) + "." + extension);
                    staged.parent().mkdirs();
                    staged.writeBytes(bytes);
                    selected.add(staged);
                }
                if(last){
                    if(selected.isEmpty()){
                        if(mindustry.Vars.ui != null) mindustry.Vars.ui.showInfo("@none.found");
                    }else{
                        try{
                            params.handleChooseResult(selected.toArray(new Fi[0]));
                        }finally{
                            // Original Mindustry import callbacks fully read each Fi before
                            // returning. Do not accumulate duplicate uploaded files in
                            // browser-persistent temporary storage.
                            for(Fi file : selected) file.delete();
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

    // TeaVM 0.15 JSBody uses an ES5 parser: no arrow functions/async/await.
    // FileReader callbacks serially deliver each validated browser-local upload.
    @JSBody(params={"extensions","multiple","callback"}, script="""
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = extensions;
        input.multiple = multiple;
        input.style.display = 'none';
        document.body.appendChild(input);
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

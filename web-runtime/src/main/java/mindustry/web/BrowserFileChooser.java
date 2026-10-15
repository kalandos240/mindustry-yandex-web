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
                        params.handleChooseResult(selected.toArray(new Fi[0]));
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

    @JSBody(params={"extensions","multiple","callback"}, script="""
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = extensions;
        input.multiple = multiple;
        input.style.display = 'none';
        document.body.appendChild(input);
        input.addEventListener('change', async () => {
            try{
                const files = Array.from(input.files || []);
                const allowed = extensions.toLowerCase().split(',');
                const filtered = files.filter(f => allowed.some(ext => f.name.toLowerCase().endsWith(ext)));
                for(let i=0;i<filtered.length;i++){
                    const f = filtered[i];
                    // JSBody bridges Uint8Array directly to TeaVM byte[].
                    callback(f.name, new Uint8Array(await f.arrayBuffer()), i===filtered.length-1);
                }
            }catch(e){
                document.documentElement.setAttribute('data-mindustry-browser-file-error', String(e));
            }finally{
                input.remove();
            }
        }, {once:true});
        input.click();
        """)
    private static native void chooseFiles(String extensions, boolean multiple, ImportedFile callback);
}

package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.assets.loaders.*;
import arc.files.*;
import arc.graphics.*;

/** Save preview loader that resolves preview files from IndexedDB-backed FileType.local. */
public final class BrowserSavePreviewLoader extends TextureLoader{
    public BrowserSavePreviewLoader(){
        super(Core.files::local);
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, Fi file, TextureParameter parameter){
        try{
            super.loadAsync(manager, fileName, file.sibling(file.nameWithoutExtension()), parameter);
        }catch(Exception error){
            file.sibling(file.nameWithoutExtension()).delete();
            throw error;
        }
    }
}

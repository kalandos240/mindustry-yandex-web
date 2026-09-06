package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.assets.loaders.*;
import arc.files.*;
import mindustry.ctype.*;

/**
 * Reinstalls the tiny AssetManager contract that stock Maps expects from the desktop
 * ClientLauncher without re-entering the desktop asset-loading lifecycle.
 *
 * The normal launcher registers ContentLoader through AssetManager.loadRun(); Maps then
 * attaches its preview callback to that CustomLoader. Web initializes content directly,
 * so only the loader identity is required. It is deliberately never queued: builtin map
 * metadata/world loading is synchronous through BrowserFi, and preview generation remains
 * outside this milestone.
 */
public final class BrowserContentLoaderBridge{
    private static boolean installed;

    private BrowserContentLoaderBridge(){}

    public static void install(){
        if(installed) return;
        if(Core.assets == null){
            throw new IllegalStateException("Browser Maps bridge requires AssetManager initialization");
        }

        AssetLoader existing = Core.assets.getLoader(ContentLoader.class);
        if(existing == null){
            Core.assets.setLoader(ContentLoader.class, new CustomLoader(){
                @Override
                public void loadAsync(AssetManager manager, String fileName, Fi file, AssetLoaderParameters parameter){
                    // Web content is initialized directly by WebClientLauncher.
                }
            });
        }else if(!(existing instanceof CustomLoader)){
            throw new IllegalStateException("Mindustry Maps expected a CustomLoader for ContentLoader on Web");
        }

        installed = true;
    }
}

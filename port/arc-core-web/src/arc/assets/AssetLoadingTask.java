package arc.assets;

import arc.assets.loaders.AssetLoader;
import arc.assets.loaders.AsynchronousAssetLoader;
import arc.assets.loaders.SynchronousAssetLoader;
import arc.struct.Seq;
import arc.files.Fi;
import arc.util.*;

/**
 * TeaVM/Web variant of Arc's asset task.
 *
 * Browser JavaScript runs the game lifecycle on one event-loop thread, so the
 * desktop ExecutorService/Future split is both unavailable and unnecessary.
 * Async loaders still keep their two-phase API: loadAsync is executed first,
 * followed by loadSync on the same browser frame once dependencies are ready.
 */
@SuppressWarnings("unchecked")
class AssetLoadingTask{
    final AssetDescriptor assetDesc;
    final AssetLoader loader;
    final long startTime;
    AssetManager manager;
    boolean asyncDone;
    boolean dependenciesLoaded;
    Seq<AssetDescriptor> dependencies;
    Object asset;
    int ticks;
    boolean cancel;

    AssetLoadingTask(AssetManager manager, AssetDescriptor assetDesc, AssetLoader loader){
        this.manager = manager;
        this.assetDesc = assetDesc;
        this.loader = loader;
        startTime = Time.nanos();
    }

    public boolean update(){
        ticks++;
        if(loader instanceof SynchronousAssetLoader){
            handleSyncLoader();
        }else{
            handleAsyncLoader();
        }
        return asset != null;
    }

    private void handleSyncLoader(){
        SynchronousAssetLoader syncLoader = (SynchronousAssetLoader)loader;
        if(!dependenciesLoaded){
            dependenciesLoaded = true;
            dependencies = syncLoader.getDependencies(assetDesc.fileName, resolve(loader, assetDesc), assetDesc.params);
            if(dependencies == null){
                asset = syncLoader.load(manager, assetDesc.fileName, resolve(loader, assetDesc), assetDesc.params);
                return;
            }
            removeDuplicates(dependencies);
            manager.injectDependencies(assetDesc.fileName, dependencies);
        }else{
            asset = syncLoader.load(manager, assetDesc.fileName, resolve(loader, assetDesc), assetDesc.params);
        }
    }

    private void handleAsyncLoader(){
        AsynchronousAssetLoader asyncLoader = (AsynchronousAssetLoader)loader;
        if(!dependenciesLoaded){
            dependenciesLoaded = true;
            dependencies = asyncLoader.getDependencies(assetDesc.fileName, resolve(loader, assetDesc), assetDesc.params);
            if(dependencies != null){
                removeDuplicates(dependencies);
                manager.injectDependencies(assetDesc.fileName, dependencies);
                return;
            }
        }

        if(!asyncDone){
            asyncLoader.loadAsync(manager, assetDesc.fileName, resolve(loader, assetDesc), assetDesc.params);
            asyncDone = true;
        }
        asset = asyncLoader.loadSync(manager, assetDesc.fileName, resolve(loader, assetDesc), assetDesc.params);
    }

    private Fi resolve(AssetLoader loader, AssetDescriptor assetDesc){
        if(assetDesc.file == null) assetDesc.file = loader.resolve(assetDesc.fileName);
        return assetDesc.file;
    }

    Object getAsset(){
        return asset;
    }

    private void removeDuplicates(Seq<AssetDescriptor> array){
        boolean ordered = array.ordered;
        array.ordered = true;
        for(int i = 0; i < array.size; ++i){
            String fn = array.get(i).fileName;
            Class type = array.get(i).type;
            for(int j = array.size - 1; j > i; --j){
                if(type == array.get(j).type && fn.equals(array.get(j).fileName)) array.remove(j);
            }
        }
        array.ordered = ordered;
    }
}

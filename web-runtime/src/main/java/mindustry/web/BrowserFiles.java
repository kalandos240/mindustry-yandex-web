package mindustry.web;

import arc.Files;
import arc.files.Fi;
import org.teavm.jso.JSBody;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Browser asset filesystem. The HTML bootstrap asynchronously fetches every staged
 * asset before TeaVM main() starts. BrowserFiles then exposes that in-memory cache
 * synchronously through Arc's normal Fi API, with no runtime network dependency.
 */
public final class BrowserFiles implements Files{
    private final String assetRoot;
    private final Map<String, String> textAssets = new LinkedHashMap<>();
    private final Map<String, byte[]> binaryAssets = new LinkedHashMap<>();

    public BrowserFiles(String assetRoot){
        this.assetRoot = trimSlashes(assetRoot == null ? "" : assetRoot);
    }

    /** Materialize one already-preloaded UTF-8 text asset into the Java-side cache. */
    public void preloadText(String path){
        String normalized = normalize(path);
        byte[] bytes = requestPreloadedBytes(assetUrl(normalized));
        if(bytes == null){
            throw new IllegalStateException("Browser text asset was not preloaded by HTML bootstrap: " + normalized);
        }
        textAssets.put(normalized, new String(bytes, StandardCharsets.UTF_8));
    }

    /** Materialize one already-preloaded binary asset without text transcoding. */
    public void preloadBinary(String path){
        String normalized = normalize(path);
        byte[] bytes = requestPreloadedBytes(assetUrl(normalized));
        if(bytes == null){
            throw new IllegalStateException("Browser binary asset was not preloaded by HTML bootstrap: " + normalized);
        }
        binaryAssets.put(normalized, bytes);
    }

    @Override
    public Fi get(String path, FileType type){
        if(type != FileType.internal && type != FileType.classpath){
            throw new UnsupportedOperationException("Browser Files currently supports packaged internal/classpath assets only: " + type);
        }
        return new BrowserFi(this, normalize(path), type);
    }

    @Override
    public String getExternalStoragePath(){
        return "";
    }

    @Override
    public boolean isExternalStorageAvailable(){
        return false;
    }

    @Override
    public String getLocalStoragePath(){
        return "";
    }

    @Override
    public boolean isLocalStorageAvailable(){
        return false;
    }

    String text(String path){
        String normalized = normalize(path);
        String value = textAssets.get(normalized);
        if(value != null) return value;

        byte[] bytes = requestPreloadedBytes(assetUrl(normalized));
        if(bytes == null){
            throw new IllegalStateException("Browser text asset is not packaged/preloaded: " + path);
        }
        value = new String(bytes, StandardCharsets.UTF_8);
        textAssets.put(normalized, value);
        return value;
    }

    byte[] bytes(String path){
        String normalized = normalize(path);
        byte[] binary = binaryAssets.get(normalized);
        if(binary == null){
            String text = textAssets.get(normalized);
            if(text != null) return text.getBytes(StandardCharsets.UTF_8);

            binary = requestPreloadedBytes(assetUrl(normalized));
            if(binary == null){
                throw new IllegalStateException("Browser asset is not packaged/preloaded: " + path);
            }
            binaryAssets.put(normalized, binary);
        }

        byte[] copy = new byte[binary.length];
        System.arraycopy(binary, 0, copy, 0, binary.length);
        return copy;
    }

    boolean contains(String path){
        String normalized = normalize(path);
        return textAssets.containsKey(normalized)
            || binaryAssets.containsKey(normalized)
            || hasPackagedAsset(normalized);
    }

    boolean hasChildren(String directory){
        String normalized = normalize(directory);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        for(String key : packagedPaths()){
            if(key.startsWith(prefix) && key.length() > prefix.length()) return true;
        }
        return false;
    }

    Fi[] children(String directory, FileType type){
        String normalized = normalize(directory);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        Map<String, Fi> children = new LinkedHashMap<>();
        for(String key : packagedPaths()){
            if(!key.startsWith(prefix) || key.length() <= prefix.length()) continue;
            String remainder = key.substring(prefix.length());
            int slash = remainder.indexOf('/');
            String childName = slash < 0 ? remainder : remainder.substring(0, slash);
            String childPath = prefix + childName;
            children.put(childName, new BrowserFi(this, childPath, type));
        }
        return children.values().toArray(new Fi[0]);
    }

    long length(String path){
        String normalized = normalize(path);
        byte[] binary = binaryAssets.get(normalized);
        if(binary != null) return binary.length;
        String text = textAssets.get(normalized);
        if(text != null) return text.getBytes(StandardCharsets.UTF_8).length;
        return preloadedLength(assetUrl(normalized));
    }

    private String assetUrl(String normalized){
        return assetRoot.isEmpty() ? normalized : assetRoot + "/" + normalized;
    }

    static String normalize(String path){
        String value = path == null ? "" : path.replace('\\', '/');
        while(value.startsWith("/")) value = value.substring(1);
        while(value.contains("//")) value = value.replace("//", "/");
        if(value.equals("..") || value.startsWith("../") || value.contains("/../")){
            throw new IllegalArgumentException("Parent traversal is not allowed in browser assets: " + path);
        }
        if(value.equals(".")) return "";
        if(value.startsWith("./")) value = value.substring(2);
        return value;
    }

    private static String trimSlashes(String value){
        value = value.replace('\\', '/');
        while(value.startsWith("/")) value = value.substring(1);
        while(value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    @JSBody(params = {"url"}, script = """
        const cache = globalThis.__mindustryAssetCache;
        return cache && cache[url] ? cache[url] : null;
        """)
    private static native byte[] requestPreloadedBytes(String url);

    @JSBody(params = {"path"}, script = """
        const manifest = globalThis.__mindustryAssetManifest || [];
        return manifest.indexOf(path) !== -1;
        """)
    private static native boolean hasPackagedAsset(String path);

    @JSBody(script = "return globalThis.__mindustryAssetManifest || [];")
    private static native String[] packagedPaths();

    @JSBody(params = {"url"}, script = """
        const cache = globalThis.__mindustryAssetCache;
        return cache && cache[url] ? cache[url].byteLength : 0;
        """)
    private static native int preloadedLength(String url);
}

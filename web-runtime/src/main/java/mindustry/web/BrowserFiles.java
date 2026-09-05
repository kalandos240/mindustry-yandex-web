package mindustry.web;

import arc.Files;
import arc.files.Fi;
import org.teavm.jso.JSBody;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Browser packaged assets + persistent local user files. */
public final class BrowserFiles implements Files{
    private static final String persistenceProbePath = "ci/persist-probe.bin";
    private static final byte[] persistenceProbeBytes = {0, 1, 2, 127, -1, 77, 83, 65};

    private final String assetRoot;
    private final Map<String, String> textAssets = new LinkedHashMap<>();
    private final Map<String, byte[]> binaryAssets = new LinkedHashMap<>();
    private final Set<String> localDirectories = new LinkedHashSet<>();

    public BrowserFiles(String assetRoot){
        this.assetRoot = trimSlashes(assetRoot == null ? "" : assetRoot);
        localDirectories.add("");
        if(persistentStorageReady()){
            for(String path : localPaths()) registerParents(path);
            if(isPersistenceSeedRequested()) seedPersistenceProbeThroughFi();
        }
    }

    private void seedPersistenceProbeThroughFi(){
        Fi probe = new BrowserFi(this, persistenceProbePath, FileType.local);
        probe.parent().mkdirs();
        probe.writeBytes(persistenceProbeBytes, false);

        byte[] immediate = probe.readBytes();
        if(immediate.length != persistenceProbeBytes.length){
            throw new IllegalStateException("BrowserFi persistence seed length mismatch");
        }
        for(int i = 0; i < immediate.length; i++){
            if(immediate[i] != persistenceProbeBytes[i]){
                throw new IllegalStateException("BrowserFi persistence seed byte mismatch at " + i);
            }
        }
        flushAndMarkPersistenceSeed();
    }

    public void preloadText(String path){
        String normalized = normalize(path);
        byte[] bytes = requestPreloadedBytes(assetUrl(normalized));
        if(bytes == null) throw new IllegalStateException("Browser text asset was not preloaded by HTML bootstrap: " + normalized);
        textAssets.put(normalized, new String(bytes, StandardCharsets.UTF_8));
    }

    public void preloadBinary(String path){
        String normalized = normalize(path);
        byte[] bytes = requestPreloadedBytes(assetUrl(normalized));
        if(bytes == null) throw new IllegalStateException("Browser binary asset was not preloaded by HTML bootstrap: " + normalized);
        binaryAssets.put(normalized, bytes);
    }

    @Override
    public Fi get(String path, FileType type){
        if(type != FileType.internal && type != FileType.classpath && type != FileType.local){
            throw new UnsupportedOperationException("Browser Files supports packaged internal/classpath assets and persistent local files only: " + type);
        }
        return new BrowserFi(this, normalize(path), type);
    }

    @Override public String getExternalStoragePath(){ return ""; }
    @Override public boolean isExternalStorageAvailable(){ return false; }
    @Override public String getLocalStoragePath(){ return "mindustry/"; }
    @Override public boolean isLocalStorageAvailable(){ return persistentStorageReady(); }

    String text(String path, FileType type){ return new String(bytes(path, type), StandardCharsets.UTF_8); }

    byte[] bytes(String path, FileType type){
        String normalized = normalize(path);
        if(type == FileType.local){
            byte[] value = requestLocalBytes(normalized);
            if(value == null) throw new IllegalStateException("Browser local file does not exist: " + path);
            return copy(value);
        }

        byte[] binary = binaryAssets.get(normalized);
        if(binary == null){
            String text = textAssets.get(normalized);
            if(text != null) return text.getBytes(StandardCharsets.UTF_8);
            binary = requestPreloadedBytes(assetUrl(normalized));
            if(binary == null) throw new IllegalStateException("Browser asset is not packaged/preloaded: " + path);
            binaryAssets.put(normalized, binary);
        }
        return copy(binary);
    }

    void putLocal(String path, byte[] bytes){
        String normalized = normalize(path);
        if(normalized.isEmpty()) throw new IllegalArgumentException("Cannot write the browser local root");
        if(!persistentStorageReady()) throw new IllegalStateException("Browser persistent storage is not initialized");
        registerParents(normalized);
        storeLocalBytes(normalized, bytes);
    }

    boolean mkdirLocal(String path){
        String normalized = normalize(path);
        int before = localDirectories.size();
        registerDirectory(normalized);
        return localDirectories.size() != before || localDirectories.contains(normalized);
    }

    boolean removeLocal(String path){
        String normalized = normalize(path);
        if(localDirectories.contains(normalized) && hasChildren(normalized, FileType.local)) return false;
        boolean removed = removeLocalFile(normalized);
        if(!hasLocalFile(normalized)) localDirectories.remove(normalized);
        return removed;
    }

    boolean removeLocalTree(String path){
        String normalized = normalize(path);
        boolean removed = removeLocalDirectory(normalized);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        localDirectories.removeIf(dir -> dir.equals(normalized) || (!prefix.isEmpty() && dir.startsWith(prefix)));
        localDirectories.add("");
        return removed || !normalized.isEmpty();
    }

    boolean contains(String path, FileType type){
        String normalized = normalize(path);
        if(type == FileType.local) return hasLocalFile(normalized) || localDirectories.contains(normalized);
        return textAssets.containsKey(normalized) || binaryAssets.containsKey(normalized) || hasPackagedAsset(normalized);
    }

    boolean isDirectory(String path, FileType type){
        String normalized = normalize(path);
        if(type == FileType.local) return localDirectories.contains(normalized) || (!hasLocalFile(normalized) && hasChildren(normalized, type));
        return !contains(normalized, type) && hasChildren(normalized, type);
    }

    boolean hasChildren(String directory, FileType type){
        String normalized = normalize(directory);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        for(String key : paths(type)) if(key.startsWith(prefix) && key.length() > prefix.length()) return true;
        if(type == FileType.local){
            for(String dir : localDirectories) if(dir.startsWith(prefix) && dir.length() > prefix.length()) return true;
        }
        return false;
    }

    Fi[] children(String directory, FileType type){
        String normalized = normalize(directory);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        Map<String, Fi> children = new LinkedHashMap<>();
        for(String key : paths(type)) addChild(children, prefix, key, type);
        if(type == FileType.local) for(String dir : localDirectories) addChild(children, prefix, dir, type);
        return children.values().toArray(new Fi[0]);
    }

    private void addChild(Map<String, Fi> children, String prefix, String key, FileType type){
        if(!key.startsWith(prefix) || key.length() <= prefix.length()) return;
        String remainder = key.substring(prefix.length());
        int slash = remainder.indexOf('/');
        String childName = slash < 0 ? remainder : remainder.substring(0, slash);
        children.put(childName, new BrowserFi(this, prefix + childName, type));
    }

    long length(String path, FileType type){
        String normalized = normalize(path);
        if(type == FileType.local) return hasLocalFile(normalized) ? localLength(normalized) : 0;
        byte[] binary = binaryAssets.get(normalized);
        if(binary != null) return binary.length;
        String text = textAssets.get(normalized);
        if(text != null) return text.getBytes(StandardCharsets.UTF_8).length;
        return preloadedLength(assetUrl(normalized));
    }

    private void registerParents(String filePath){ int slash = filePath.lastIndexOf('/'); if(slash >= 0) registerDirectory(filePath.substring(0, slash)); }
    private void registerDirectory(String directory){
        String normalized = normalize(directory);
        localDirectories.add("");
        if(normalized.isEmpty()) return;
        String[] parts = normalized.split("/");
        String current = "";
        for(String part : parts){ current = current.isEmpty() ? part : current + "/" + part; localDirectories.add(current); }
    }

    private String[] paths(FileType type){ return type == FileType.local ? localPaths() : packagedPaths(); }
    private String assetUrl(String normalized){ return assetRoot.isEmpty() ? normalized : assetRoot + "/" + normalized; }

    static String normalize(String path){
        String value = path == null ? "" : path.replace('\\', '/');
        while(value.startsWith("/")) value = value.substring(1);
        while(value.contains("//")) value = value.replace("//", "/");
        if(value.equals("..") || value.startsWith("../") || value.contains("/../")) throw new IllegalArgumentException("Parent traversal is not allowed in browser files: " + path);
        if(value.equals(".")) return "";
        if(value.startsWith("./")) value = value.substring(2);
        return value;
    }

    private static byte[] copy(byte[] value){ byte[] result = new byte[value.length]; System.arraycopy(value, 0, result, 0, value.length); return result; }
    private static String trimSlashes(String value){ value = value.replace('\\', '/'); while(value.startsWith("/")) value = value.substring(1); while(value.endsWith("/")) value = value.substring(0, value.length() - 1); return value; }

    @JSBody(params = {"url"}, script = "const cache = globalThis.__mindustryAssetCache; return cache && cache[url] ? cache[url] : null;")
    private static native byte[] requestPreloadedBytes(String url);
    @JSBody(params = {"path"}, script = "const manifest = globalThis.__mindustryAssetManifest || []; return manifest.indexOf(path) !== -1;")
    private static native boolean hasPackagedAsset(String path);
    @JSBody(script = "return globalThis.__mindustryAssetManifest || [];")
    private static native String[] packagedPaths();
    @JSBody(params = {"url"}, script = "const cache = globalThis.__mindustryAssetCache; return cache && cache[url] ? cache[url].byteLength : 0;")
    private static native int preloadedLength(String url);
    @JSBody(script = "return !!(globalThis.__mindustryStorage && document.documentElement.getAttribute('data-mindustry-storage') === 'ready');")
    private static native boolean persistentStorageReady();
    @JSBody(params = {"path"}, script = "return globalThis.__mindustryStorage.get(path);")
    private static native byte[] requestLocalBytes(String path);
    @JSBody(params = {"path", "bytes"}, script = "globalThis.__mindustryStorage.put(path, bytes);")
    private static native void storeLocalBytes(String path, byte[] bytes);
    @JSBody(params = {"path"}, script = "return globalThis.__mindustryStorage.remove(path);")
    private static native boolean removeLocalFile(String path);
    @JSBody(params = {"path"}, script = "return globalThis.__mindustryStorage.removeTree(path);")
    private static native boolean removeLocalDirectory(String path);
    @JSBody(params = {"path"}, script = "return globalThis.__mindustryStorage.exists(path);")
    private static native boolean hasLocalFile(String path);
    @JSBody(script = "return globalThis.__mindustryStorage.paths();")
    private static native String[] localPaths();
    @JSBody(params = {"path"}, script = "return globalThis.__mindustryStorage.byteLength(path);")
    private static native int localLength(String path);
    @JSBody(script = "return new URLSearchParams(location.search).get('persistenceSeed') === '1';")
    private static native boolean isPersistenceSeedRequested();
    @JSBody(script = "globalThis.__mindustryStorage.flush().then(function(){document.documentElement.setAttribute('data-mindustry-java-persistence-seed','ready');}).catch(function(e){document.documentElement.setAttribute('data-mindustry-java-persistence-seed','error');});")
    private static native void flushAndMarkPersistenceSeed();
}

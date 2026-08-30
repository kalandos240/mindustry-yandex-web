package mindustry.web;

import arc.Files;
import arc.files.Fi;
import org.teavm.jso.JSBody;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Browser asset filesystem. Internal/classpath assets are fetched during the preload
 * phase and are then exposed synchronously to Arc through normal Fi handles.
 */
public final class BrowserFiles implements Files{
    private final String assetRoot;
    private final Map<String, String> textAssets = new LinkedHashMap<>();
    private final Map<String, byte[]> binaryAssets = new LinkedHashMap<>();

    public BrowserFiles(String assetRoot){
        this.assetRoot = trimSlashes(assetRoot == null ? "" : assetRoot);
    }

    /** Load one packaged UTF-8 text asset before the application lifecycle starts. */
    public void preloadText(String path){
        String normalized = normalize(path);
        String url = assetUrl(normalized);
        String text = requestText(url);
        if(text == null){
            throw new IllegalStateException("Failed to preload browser text asset: " + url);
        }
        textAssets.put(normalized, text);
    }

    /** Load one packaged binary asset without any text transcoding. */
    public void preloadBinary(String path){
        String normalized = normalize(path);
        String url = assetUrl(normalized);
        byte[] bytes = requestBytes(url);
        if(bytes == null){
            throw new IllegalStateException("Failed to preload browser binary asset: " + url);
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
        String value = textAssets.get(normalize(path));
        if(value == null){
            throw new IllegalStateException("Browser text asset was not preloaded: " + path);
        }
        return value;
    }

    byte[] bytes(String path){
        String normalized = normalize(path);
        byte[] binary = binaryAssets.get(normalized);
        if(binary != null){
            byte[] copy = new byte[binary.length];
            System.arraycopy(binary, 0, copy, 0, binary.length);
            return copy;
        }

        String text = textAssets.get(normalized);
        if(text != null){
            return text.getBytes(StandardCharsets.UTF_8);
        }

        throw new IllegalStateException("Browser asset was not preloaded: " + path);
    }

    boolean contains(String path){
        String normalized = normalize(path);
        return textAssets.containsKey(normalized) || binaryAssets.containsKey(normalized);
    }

    boolean hasChildren(String directory){
        String normalized = normalize(directory);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        return hasChild(textAssets, prefix) || hasChild(binaryAssets, prefix);
    }

    Fi[] children(String directory, FileType type){
        String normalized = normalize(directory);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        Map<String, Fi> children = new LinkedHashMap<>();
        collectChildren(textAssets, prefix, type, children);
        collectChildren(binaryAssets, prefix, type, children);
        return children.values().toArray(new Fi[0]);
    }

    long length(String path){
        String normalized = normalize(path);
        byte[] binary = binaryAssets.get(normalized);
        if(binary != null) return binary.length;
        String text = textAssets.get(normalized);
        return text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;
    }

    private String assetUrl(String normalized){
        return assetRoot.isEmpty() ? normalized : assetRoot + "/" + normalized;
    }

    private static boolean hasChild(Map<String, ?> assets, String prefix){
        for(String key : assets.keySet()){
            if(key.startsWith(prefix) && key.length() > prefix.length()) return true;
        }
        return false;
    }

    private void collectChildren(Map<String, ?> assets, String prefix, FileType type, Map<String, Fi> children){
        for(String key : assets.keySet()){
            if(!key.startsWith(prefix) || key.length() <= prefix.length()) continue;
            String remainder = key.substring(prefix.length());
            int slash = remainder.indexOf('/');
            String childName = slash < 0 ? remainder : remainder.substring(0, slash);
            String childPath = prefix + childName;
            children.put(childName, new BrowserFi(this, childPath, type));
        }
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
        try {
            const xhr = new XMLHttpRequest();
            xhr.open('GET', url, false);
            xhr.send(null);
            if ((xhr.status >= 200 && xhr.status < 300) || xhr.status === 0) return xhr.responseText;
            return null;
        } catch (e) {
            return null;
        }
        """)
    private static native String requestText(String url);

    @JSBody(params = {"url"}, script = """
        try {
            const xhr = new XMLHttpRequest();
            xhr.open('GET', url, false);
            xhr.responseType = 'arraybuffer';
            xhr.send(null);
            if (((xhr.status >= 200 && xhr.status < 300) || xhr.status === 0) && xhr.response != null) {
                return new Int8Array(xhr.response);
            }
            return null;
        } catch (e) {
            return null;
        }
        """)
    private static native byte[] requestBytes(String url);
}

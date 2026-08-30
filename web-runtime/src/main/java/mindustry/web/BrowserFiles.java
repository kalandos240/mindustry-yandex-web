package mindustry.web;

import arc.Files;
import arc.files.Fi;
import org.teavm.jso.JSBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Browser asset filesystem. Internal/classpath assets are fetched during the preload
 * phase and are then exposed synchronously to Arc through normal Fi handles.
 */
public final class BrowserFiles implements Files{
    private final String assetRoot;
    private final Map<String, String> textAssets = new LinkedHashMap<>();

    public BrowserFiles(String assetRoot){
        this.assetRoot = trimSlashes(assetRoot == null ? "" : assetRoot);
    }

    /** Load one packaged text asset before the application lifecycle starts. */
    public void preloadText(String path){
        String normalized = normalize(path);
        String url = assetRoot.isEmpty() ? normalized : assetRoot + "/" + normalized;
        String text = requestText(url);
        if(text == null){
            throw new IllegalStateException("Failed to preload browser asset: " + url);
        }
        textAssets.put(normalized, text);
    }

    @Override
    public Fi get(String path, FileType type){
        if(type != FileType.internal && type != FileType.classpath){
            throw new UnsupportedOperationException("Browser Files currently supports packaged internal/classpath assets only: " + type);
        }
        return new BrowserFi(this, normalize(path), type);
    }

    String text(String path){
        String value = textAssets.get(normalize(path));
        if(value == null){
            throw new IllegalStateException("Browser asset was not preloaded: " + path);
        }
        return value;
    }

    boolean contains(String path){
        return textAssets.containsKey(normalize(path));
    }

    boolean hasChildren(String directory){
        String normalized = normalize(directory);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        for(String key : textAssets.keySet()){
            if(key.startsWith(prefix) && key.length() > prefix.length()) return true;
        }
        return false;
    }

    Fi[] children(String directory, FileType type){
        String normalized = normalize(directory);
        String prefix = normalized.isEmpty() ? "" : normalized + "/";
        Map<String, Fi> children = new LinkedHashMap<>();
        for(String key : textAssets.keySet()){
            if(!key.startsWith(prefix) || key.length() <= prefix.length()) continue;
            String remainder = key.substring(prefix.length());
            int slash = remainder.indexOf('/');
            String childName = slash < 0 ? remainder : remainder.substring(0, slash);
            String childPath = prefix + childName;
            children.put(childName, new BrowserFi(this, childPath, type));
        }
        return children.values().toArray(new Fi[0]);
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
}

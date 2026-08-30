package mindustry.web;

import arc.Files.FileType;
import arc.files.Fi;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Fi backed by BrowserFiles' in-memory preload cache; no java.io.File is constructed. */
public final class BrowserFi extends Fi{
    private final BrowserFiles files;
    private final String browserPath;

    BrowserFi(BrowserFiles files, String path, FileType type){
        super();
        this.files = files;
        this.browserPath = BrowserFiles.normalize(path);
        this.type = type;
    }

    @Override
    public String path(){
        return browserPath;
    }

    @Override
    public String absolutePath(){
        return browserPath;
    }

    @Override
    public String name(){
        int slash = browserPath.lastIndexOf('/');
        return slash < 0 ? browserPath : browserPath.substring(slash + 1);
    }

    @Override
    public String extension(){
        String name = name();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    @Override
    public String nameWithoutExtension(){
        String name = name();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    @Override
    public String pathWithoutExtension(){
        int dot = browserPath.lastIndexOf('.');
        int slash = browserPath.lastIndexOf('/');
        return dot > slash ? browserPath.substring(0, dot) : browserPath;
    }

    @Override
    public InputStream read(){
        return new ByteArrayInputStream(readBytes());
    }

    @Override
    public String readString(){
        return files.text(browserPath);
    }

    @Override
    public String readString(String charset){
        if(charset == null || charset.equalsIgnoreCase("UTF-8") || charset.equalsIgnoreCase("UTF8")){
            return files.text(browserPath);
        }
        throw new UnsupportedOperationException("Browser packaged text assets are UTF-8: " + charset);
    }

    @Override
    public byte[] readBytes(){
        return files.text(browserPath).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean exists(){
        return files.contains(browserPath) || files.hasChildren(browserPath);
    }

    @Override
    public boolean isDirectory(){
        return !files.contains(browserPath) && files.hasChildren(browserPath);
    }

    @Override
    public long length(){
        return files.contains(browserPath) ? readBytes().length : 0L;
    }

    @Override
    public Fi child(String name){
        String child = BrowserFiles.normalize(name);
        return new BrowserFi(files, browserPath.isEmpty() ? child : browserPath + "/" + child, type);
    }

    @Override
    public Fi sibling(String name){
        if(browserPath.isEmpty()) throw new IllegalStateException("Root browser asset has no sibling");
        return parent().child(name);
    }

    @Override
    public Fi parent(){
        int slash = browserPath.lastIndexOf('/');
        return new BrowserFi(files, slash < 0 ? "" : browserPath.substring(0, slash), type);
    }

    @Override
    public Fi[] list(){
        return files.children(browserPath, type);
    }

    @Override
    public String toString(){
        return browserPath;
    }
}

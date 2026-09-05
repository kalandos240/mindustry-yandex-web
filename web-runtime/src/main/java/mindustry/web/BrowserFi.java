package mindustry.web;

import arc.Files.FileType;
import arc.files.Fi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/** Fi backed by immutable packaged assets or BrowserFiles' persistent local store. */
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
        return files.text(browserPath, type);
    }

    @Override
    public String readString(String charset){
        String actual = charset == null ? "UTF-8" : charset;
        return new String(readBytes(), Charset.forName(actual));
    }

    @Override
    public byte[] readBytes(){
        return files.bytes(browserPath, type);
    }

    @Override
    public OutputStream write(boolean append){
        requireLocalWrite();
        return new PersistentOutputStream(files, browserPath, append);
    }

    @Override
    public OutputStream write(boolean append, int bufferSize){
        // PersistentOutputStream already buffers in memory and commits atomically on close.
        return write(append);
    }

    @Override
    public boolean exists(){
        return files.contains(browserPath, type) || files.hasChildren(browserPath, type);
    }

    @Override
    public boolean isDirectory(){
        return files.isDirectory(browserPath, type);
    }

    @Override
    public long length(){
        return files.length(browserPath, type);
    }

    @Override
    public boolean mkdirs(){
        requireLocalWrite();
        return files.mkdirLocal(browserPath);
    }

    @Override
    public boolean delete(){
        requireLocalWrite();
        return files.removeLocal(browserPath);
    }

    @Override
    public boolean deleteDirectory(){
        requireLocalWrite();
        return files.removeLocalTree(browserPath);
    }

    @Override
    public void emptyDirectory(boolean preserveTree){
        requireLocalWrite();
        for(Fi child : list()){
            if(child.isDirectory()) child.deleteDirectory();
            else child.delete();
        }
    }

    @Override
    public Fi child(String name){
        String child = BrowserFiles.normalize(name);
        return new BrowserFi(files, browserPath.isEmpty() ? child : browserPath + "/" + child, type);
    }

    @Override
    public Fi sibling(String name){
        if(browserPath.isEmpty()) throw new IllegalStateException("Root browser file has no sibling");
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
    public Fi[] list(String suffix){
        Fi[] all = list();
        int count = 0;
        for(Fi file : all) if(file.name().endsWith(suffix)) count++;
        Fi[] result = new Fi[count];
        int index = 0;
        for(Fi file : all) if(file.name().endsWith(suffix)) result[index++] = file;
        return result;
    }

    @Override
    public String toString(){
        return browserPath;
    }

    private void requireLocalWrite(){
        if(type != FileType.local){
            throw new UnsupportedOperationException("Browser packaged assets are read-only: " + browserPath + " (" + type + ")");
        }
    }

    private static final class PersistentOutputStream extends ByteArrayOutputStream{
        private final BrowserFiles files;
        private final String path;
        private boolean committed;

        PersistentOutputStream(BrowserFiles files, String path, boolean append){
            this.files = files;
            this.path = path;
            if(append && files.contains(path, FileType.local) && !files.isDirectory(path, FileType.local)){
                byte[] existing = files.bytes(path, FileType.local);
                write(existing, 0, existing.length);
            }
        }

        @Override
        public void close() throws IOException{
            if(committed) return;
            committed = true;
            files.putLocal(path, toByteArray());
            super.close();
        }
    }
}

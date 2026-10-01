package android.content.res;
import java.io.*;
public final class AssetManager {
    private final File root;
    public AssetManager(File directory) { root=directory; }
    public InputStream open(String name) throws IOException {return new FileInputStream(new File(root,name));}
    public AssetFileDescriptor openFd(String name) throws IOException {
        File file=new File(root,name);
        if(!file.isFile()) throw new FileNotFoundException(name);
        return new AssetFileDescriptor(file.length());
    }
}

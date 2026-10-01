package android.content;
import java.io.*;
import android.content.res.AssetManager;
public final class Context {
    private final File files;
    private final AssetManager assets;
    public Context(File files, File assetRoot) {this.files=files;assets=new AssetManager(assetRoot);}
    public File getFilesDir() {return files;}
    public AssetManager getAssets() {return assets;}
    public Context getApplicationContext() {return this;}
}

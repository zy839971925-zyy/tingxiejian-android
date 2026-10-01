package android.content.res;
public final class AssetFileDescriptor implements AutoCloseable {
    private final long length;
    public AssetFileDescriptor(long value) { length=value; }
    public long getLength() { return length; }
    public void close() {}
}

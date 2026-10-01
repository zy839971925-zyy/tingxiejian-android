package android.util;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
/** Test-only filesystem-backed stand-in for Android AtomicFile's rollback contract. */
public final class AtomicFile {
    private final File base;
    private final File backup;
    public static boolean failNextFinish;
    public AtomicFile(File base) { this.base = base; this.backup = new File(base + ".bak"); }
    public FileOutputStream startWrite() throws IOException {
        if (base.exists()) Files.copy(base.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return new FileOutputStream(base);
    }
    public void finishWrite(FileOutputStream out) throws IOException {
        if (failNextFinish) { failNextFinish = false; throw new IOException("injected write failure"); }
        out.getFD().sync(); out.close(); backup.delete();
    }
    public void failWrite(FileOutputStream out) {
        try { out.close(); if (backup.exists()) Files.move(backup.toPath(), base.toPath(), StandardCopyOption.REPLACE_EXISTING); else base.delete(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }
    public FileInputStream openRead() throws IOException {
        if (backup.exists()) Files.move(backup.toPath(), base.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return new FileInputStream(base);
    }
}

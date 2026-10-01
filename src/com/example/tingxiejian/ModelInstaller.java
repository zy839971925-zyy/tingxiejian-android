package com.example.tingxiejian;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** No network code. Verified, durable partial files are atomically promoted on the same volume. */
final class ModelInstaller {
    static final class Entry {
        final String filename, sha256;
        final long size;
        Entry(String filename, long size, String sha256) {
            this.filename = filename; this.size = size; this.sha256 = sha256;
        }
    }
    interface Source { InputStream open(String filename) throws IOException; }
    interface Progress { void copied(long bytes); }

    static String sha256(File file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[256 * 1024]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }

    static boolean verified(File file, long expectedSize, String expectedHash) throws IOException {
        return file.isFile() && expectedSize > 0 && file.length() == expectedSize
                && (expectedHash == null || sha256(file).equals(expectedHash));
    }

    /** A null hash is allowed only for APK assets with still-unresolved historical provenance. */
    static synchronized void install(InputStream input, File target, long expectedSize,
                                     String expectedHash, Progress progress) throws IOException {
        File partial = null;
        try {
            try (InputStream in = input) {
                if (expectedSize <= 0 || (expectedHash != null && !expectedHash.matches("[a-f0-9]{64}")))
                    throw new IOException("模型校验信息无效");
                File parent = target.getAbsoluteFile().getParentFile();
                mkdir(parent);
                partial = File.createTempFile(target.getName() + ".partial-", ".tmp", parent);
                MessageDigest digest = digest(); long copied = 0;
                try (FileOutputStream out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[256 * 1024]; int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (count == 0) continue;
                        if (copied > expectedSize - count) throw new IOException("模型大小超出预期：" + target.getName());
                        out.write(buffer, 0, count); digest.update(buffer, 0, count); copied += count;
                        if (progress != null) progress.copied(count);
                    }
                    out.getFD().sync();
                }
                if (copied != expectedSize || (expectedHash != null && !hex(digest.digest()).equals(expectedHash)))
                    throw new IOException("模型大小或 SHA-256 校验失败：" + target.getName());
            }
            // Fail closed if this volume cannot atomically rename. The existing target stays intact.
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (partial != null && partial.exists()) partial.delete();
        }
    }

    /** Install immutable generations; only a tiny active marker changes after ALL files pass. */
    static synchronized File installPack(File root, List<Entry> entries, Source source,
                                         Progress progress) throws IOException {
        validateEntries(entries); mkdir(root);
        File staging = new File(root, ".partial-" + UUID.randomUUID()); mkdir(staging);
        File complete = null;
        try {
            for (Entry entry : entries) {
                File file = resolve(staging, entry.filename);
                install(source.open(entry.filename), file, entry.size, entry.sha256, progress);
            }
            complete = new File(root, "version-" + UUID.randomUUID());
            Files.move(staging.toPath(), complete.toPath(), StandardCopyOption.ATOMIC_MOVE);
            byte[] marker = complete.getName().getBytes(StandardCharsets.UTF_8);
            String markerHash = hex(digest().digest(marker));
            install(new ByteArrayInputStream(marker), new File(root, "active"), marker.length, markerHash, null);
            // Old generations remain usable by already-open recognizers. Cleanup can occur on a later launch.
            return complete;
        } catch (IOException | RuntimeException failure) {
            deleteTree(staging);
            if (complete != null) deleteTree(complete);
            throw failure;
        }
    }

    static File activePack(File root, List<Entry> entries) throws IOException {
        File active = activeDirectory(root);
        if (active == null) return null;
        validateEntries(entries);
        for (Entry entry : entries)
            if (!verified(resolve(active, entry.filename), entry.size, entry.sha256)) return null;
        return active;
    }

    static File activeDirectory(File root) throws IOException {
        File marker = new File(root, "active");
        if (!marker.isFile() || marker.length() > 80) return null;
        String name = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8);
        if (!name.matches("version-[a-f0-9-]{36}")) return null;
        File active = resolve(root, name);
        return active.isDirectory() ? active : null;
    }

    private static void validateEntries(List<Entry> entries) throws IOException {
        if (entries == null || entries.isEmpty()) throw new IOException("模型包为空");
        Set<String> seen = new HashSet<>();
        for (Entry entry : entries) {
            validateName(entry.filename);
            if (!seen.add(entry.filename) || entry.size <= 0 || entry.sha256 == null
                    || !entry.sha256.matches("[a-f0-9]{64}")) throw new IOException("模型包校验信息无效");
        }
    }

    static File resolve(File root, String filename) throws IOException {
        validateName(filename);
        File target = new File(root, filename);
        if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator))
            throw new IOException("模型路径越界");
        return target;
    }

    private static void validateName(String filename) throws IOException {
        if (filename == null || filename.isEmpty() || filename.startsWith("/") || filename.contains("\\"))
            throw new IOException("模型路径无效");
        for (String part : filename.split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.contains(":"))
                throw new IOException("模型路径无效");
    }
    private static void mkdir(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建模型目录");
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
    private static void deleteTree(File root) {
        if (root.isDirectory()) {
            File[] files = root.listFiles();
            if (files != null) for (File file : files) deleteTree(file);
        }
        root.delete();
    }
}

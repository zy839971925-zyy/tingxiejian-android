package com.example.tingxiejian;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Behavioral disk tests: malformed input may never replace a known good model. */
public final class ModelInstallerCheck {
    private static int checks;
    static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static String hash(byte[] value) throws Exception {
        File f = File.createTempFile("model-hash", ".bin");
        try { Files.write(f.toPath(), value); return ModelInstaller.sha256(f); }
        finally { f.delete(); }
    }
    interface Throwing { void run() throws Exception; }
    static void rejected(Throwing action) throws Exception {
        try { action.run(); throw new AssertionError("invalid input was accepted"); }
        catch (IOException expected) { checks++; }
    }
    static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }
    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("model-install-test").toFile();
        byte[] good = bytes("original-model");
        String sha = hash(good);
        File model = new File(dir, "model.onnx");
        ModelInstaller.install(new ByteArrayInputStream(good), model, good.length, sha, null);
        check(model.isFile(), "verified input installed");
        check(read(model).equals("original-model"), "verified input bytes match");
        check(ModelInstaller.verified(model, good.length, sha), "hash readiness checks actual content");
        rejected(() -> ModelInstaller.install(new ByteArrayInputStream(bytes("corrupt!-model")), model, good.length, sha, null));
        check(read(model).equals("original-model"), "same-size bad hash preserves original");
        rejected(() -> ModelInstaller.install(new ByteArrayInputStream(bytes("short")), model, good.length, sha, null));
        check(read(model).equals("original-model"), "short download preserves original");
        rejected(() -> ModelInstaller.install(new ByteArrayInputStream(bytes("original-model!")), model, good.length, sha, null));
        check(read(model).equals("original-model"), "oversize preserves original");
        InputStream interrupted = new InputStream() {
            int count;
            public int read() throws IOException { if (count++ == 3) throw new IOException("interrupted"); return 'x'; }
        };
        rejected(() -> ModelInstaller.install(interrupted, model, good.length, sha, null));
        check(read(model).equals("original-model"), "interrupted read preserves original");
        byte[] closeBytes=bytes("replacement-ok");
        String closeHash=hash(closeBytes);
        InputStream closeFails=new ByteArrayInputStream(closeBytes) {
            public void close() throws IOException {throw new IOException("source close failure");}
        };
        rejected(() -> ModelInstaller.install(closeFails,model,closeBytes.length,closeHash,null));
        check(read(model).equals("original-model"), "source close failure before promotion preserves original");
        check(Arrays.stream(dir.list()).noneMatch(n -> n.contains(".partial")), "failure cleans partial input");
        List<ModelInstaller.Entry> entries = Arrays.asList(
            new ModelInstaller.Entry("encoder.onnx", good.length, sha),
            new ModelInstaller.Entry("tokenizer/vocab.json", good.length, sha));
        File packRoot = new File(dir, "qwen");
        File first = ModelInstaller.installPack(packRoot, entries, n -> new ByteArrayInputStream(good), null);
        check(read(new File(first, "tokenizer/vocab.json")).equals("original-model"), "pack nested tokenizer installed");
        check(ModelInstaller.activePack(packRoot, entries).equals(first), "active marker resolves verified generation");
        rejected(() -> ModelInstaller.installPack(packRoot, entries,
            n -> new ByteArrayInputStream(n.startsWith("tokenizer") ? bytes("bad") : good), null));
        check(ModelInstaller.activePack(packRoot, entries).equals(first), "failed pack preserves active generation");
        check(read(new File(first, "encoder.onnx")).equals("original-model"), "failed pack leaves live bytes unchanged");
        byte[] newBytes=bytes("upgraded-model");
        List<ModelInstaller.Entry> upgraded=Arrays.asList(
            new ModelInstaller.Entry("encoder.onnx", newBytes.length, hash(newBytes)),
            new ModelInstaller.Entry("tokenizer/vocab.json", newBytes.length, hash(newBytes)));
        File second=ModelInstaller.installPack(packRoot, upgraded,n -> new ByteArrayInputStream(newBytes), null);
        check(!first.equals(second), "upgrade uses a new immutable generation");
        check(ModelInstaller.activePack(packRoot, upgraded).equals(second), "upgrade atomically switches complete pack");
        check(read(new File(first,"encoder.onnx")).equals("original-model"), "open old generation remains available");
        rejected(() -> ModelInstaller.installPack(packRoot,
            Collections.singletonList(new ModelInstaller.Entry("../escape",good.length,sha)),n -> new ByteArrayInputStream(good), null));
        rejected(() -> ModelInstaller.installPack(packRoot,
            Collections.singletonList(new ModelInstaller.Entry("/escape",good.length,sha)),n -> new ByteArrayInputStream(good), null));
        check(!new File(dir,"escape").exists(), "path traversal cannot write outside pack");
        Files.write(new File(second,"encoder.onnx").toPath(),bytes("same-size-bad!"));
        check(ModelInstaller.activePack(packRoot,upgraded)==null,"pack corruption detected by fresh hash verification");
        System.out.println("model installer: " + checks + " behavioral checks passed");
    }
}

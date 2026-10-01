package com.example.tingxiejian;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Capability-based local model storage. APK assets or explicit user imports only; no downloads. */
final class ModelManager {
    enum Pack {
        CORE_STREAMING("core-streaming"), ACCURATE_FINALIZER("accurate-finalizer"),
        PUNCTUATION("punctuation"), DIARIZATION("diarization"), QWEN3("qwen3-asr-0.6b-int8");
        final String id;
        Pack(String id) { this.id=id; }
    }
    interface ImportSource { InputStream open(String relativeFilename) throws IOException; }
    private static final Map<Context,List<Spec>> MANIFESTS = new WeakHashMap<>();
    private static final Map<String,String> VERIFIED = new ConcurrentHashMap<>();
    private static final class Spec {
        String filename, pack, hash, redistribution;
        long size;
    }
    static File dir(Context context) { return new File(context.getFilesDir(), "models-v1"); }

    static synchronized File ensure(Context context, String filename) throws IOException {
        return ensure(context, filename, null);
    }
    static synchronized File ensure(Context context, String filename, ModelInstaller.Progress progress) throws IOException {
        Spec spec = find(context, filename);
        if (spec.pack.equals(Pack.QWEN3.id)) {
            File pack = ensureQwen(context);
            return ModelInstaller.resolve(pack, filename.substring(Pack.QWEN3.id.length()+1));
        }
        File file = ModelInstaller.resolve(dir(context), filename);
        long size = expectedSize(context, spec);
        if (verified(file, size, spec.hash)) return file;
        ModelInstaller.install(context.getAssets().open("model/"+filename), file, size, spec.hash, progress);
        remember(file, size, spec.hash);
        return file;
    }

    static synchronized void prepare(Context context, Pack pack, ModelPrep.Progress progress) throws IOException {
        if (pack == Pack.QWEN3) { ensureQwen(context, progress); return; }
        List<Spec> specs = specs(context, pack); long total=0;
        for (Spec spec : specs) total += expectedSize(context, spec);
        final long totalBytes=total; final long[] done={0};
        for (Spec spec : specs) {
            long before=done[0], size=expectedSize(context,spec);
            ensure(context,spec.filename,count->{done[0]+=count;publish(progress,done[0],totalBytes,spec.filename);});
            done[0]=before+size;publish(progress,done[0],totalBytes,spec.filename);
        }
    }
    static boolean ready(Context context, Pack pack) {
        if (pack==Pack.QWEN3) return readyQwen(context);
        try {
            for (Spec spec : specs(context,pack))
                if (!verified(ModelInstaller.resolve(dir(context),spec.filename),expectedSize(context,spec),spec.hash)) return false;
            return true;
        } catch (IOException failure) {return false;}
    }
    static long totalBytes(Context context, Pack pack) throws IOException {
        long bytes=0;for(Spec spec:specs(context,pack)) bytes+=expectedSize(context,spec);return bytes;
    }
    static long doneBytes(Context context, Pack pack) {
        long bytes=0;
        try { for(Spec spec:specs(context,pack)) {
            File file=ModelInstaller.resolve(dir(context),spec.filename);
            if(file.isFile())bytes+=Math.min(file.length(),expectedSize(context,spec));
        }} catch(IOException ignored) {}
        return bytes;
    }

    /** Returns active immutable generation, or the not-yet-installed pack root. */
    static File qwenDir(Context context) {
        File root=new File(dir(context),Pack.QWEN3.id);
        try {File active=ModelInstaller.activeDirectory(root);return active==null?root:active;}
        catch(IOException error){return root;}
    }
    /** Cheap availability for UI: imported verified generation, or complete bundled pack. */
    static boolean availableQwen(Context context) {
        if(readyQwen(context))return true;
        return bundledQwen(context);
    }
    static boolean bundledQwen(Context context) {
        try {for(Spec spec:specs(context,Pack.QWEN3)) {
            try(AssetFileDescriptor afd=context.getAssets().openFd("model/"+spec.filename)) {
                if(spec.size<=0 || afd.getLength()!=spec.size)return false;
            }
        }return true;}catch(IOException failure){return false;}
    }
    /** Availability, not a fresh integrity audit. Full verification happens before native model load. */
    static boolean readyQwen(Context context) {
        try {
            File active=ModelInstaller.activeDirectory(new File(dir(context),Pack.QWEN3.id));
            if(active==null)return false;
            for(ModelInstaller.Entry entry:qwenEntries(context)) {
                File file=ModelInstaller.resolve(active,entry.filename);
                if(!file.isFile() || file.length()!=entry.size)return false;
            }
            return true;
        }catch(IOException failure){return false;}
    }
    static synchronized File ensureQwen(Context context) throws IOException {
        return ensureQwen(context, null);
    }
    static synchronized File ensureQwen(Context context, ModelPrep.Progress progress) throws IOException {
        List<ModelInstaller.Entry> entries=qwenEntries(context);
        File root=new File(dir(context),Pack.QWEN3.id);
        File active=ModelInstaller.activeDirectory(root);
        if(active!=null) {
            boolean good=true;
            for(ModelInstaller.Entry entry:entries)
                if(!verified(ModelInstaller.resolve(active,entry.filename),entry.size,entry.sha256)){good=false;break;}
            if(good)return active;
        }
        return importPack(context,Pack.QWEN3,n->context.getAssets().open("model/"+Pack.QWEN3.id+"/"+n),progress);
    }
    static File importQwen(Context context, ImportSource source, ModelPrep.Progress progress) throws IOException {
        return importPack(context,Pack.QWEN3,source,progress);
    }
    /** SAF callers resolve only relative filenames supplied by this trusted manifest. */
    static synchronized File importPack(Context context, Pack pack, ImportSource source,
                                        ModelPrep.Progress progress) throws IOException {
        if(pack!=Pack.QWEN3)throw new IOException("当前仅支持导入 Qwen3 高精度模型包");
        List<ModelInstaller.Entry> entries=qwenEntries(context);
        long total=0;for(ModelInstaller.Entry entry:entries)total+=entry.size;
        final long expected=total;final long[] done={0};
        return ModelInstaller.installPack(new File(dir(context),pack.id),entries,source::open,
                bytes->{done[0]+=bytes;publish(progress,done[0],expected,pack.id);});
    }
    private static List<ModelInstaller.Entry> qwenEntries(Context context) throws IOException {
        List<ModelInstaller.Entry> entries=new ArrayList<>();
        for(Spec spec:specs(context,Pack.QWEN3)) {
            if(!"verified".equals(spec.redistribution) || spec.hash==null || spec.size<=0
                    || !spec.filename.startsWith(Pack.QWEN3.id+"/"))throw new IOException("Qwen3 模型来源或校验信息尚未核实");
            entries.add(new ModelInstaller.Entry(spec.filename.substring(Pack.QWEN3.id.length()+1),spec.size,spec.hash));
        }
        return entries;
    }
    private static Spec find(Context context,String filename) throws IOException {
        for(Spec spec:manifest(context))if(spec.filename.equals(filename))return spec;
        throw new IOException("未知模型文件："+filename);
    }
    private static List<Spec> specs(Context context,Pack pack) throws IOException {
        List<Spec> list=new ArrayList<>();for(Spec spec:manifest(context))if(spec.pack.equals(pack.id))list.add(spec);
        if(list.isEmpty())throw new IOException("模型包信息缺失："+pack.id);return list;
    }
    private static List<Spec> manifest(Context context) throws IOException {
        synchronized (MANIFESTS) {
            Context key=context.getApplicationContext();
            if(key==null)key=context;
            List<Spec> cached=MANIFESTS.get(key);if(cached!=null)return cached;
            try(InputStream in=context.getAssets().open("model-manifest.json")) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
                JSONObject root=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
                if(root.getInt("schema_version")!=1)throw new IOException("模型清单版本不支持");
                JSONArray array=root.getJSONArray("models");List<Spec> list=new ArrayList<>();Set<String> names=new HashSet<>();
                for(int i=0;i<array.length();i++) {
                    JSONObject row=array.getJSONObject(i);Spec spec=new Spec();spec.filename=row.getString("filename");
                    ModelInstaller.resolve(dir(context),spec.filename);
                    if(!names.add(spec.filename))throw new IOException("模型清单重复文件");
                    spec.pack=row.getString("pack");spec.size=row.optLong("expected_bytes",-1);
                    spec.hash=row.isNull("sha256")?null:row.getString("sha256");
                    spec.redistribution=row.optString("redistribution","unresolved");
                    if(spec.hash!=null&&!spec.hash.matches("[a-f0-9]{64}"))throw new IOException("模型哈希无效");
                    list.add(spec);
                }
                MANIFESTS.put(key,list);return list;
            }catch(IOException failure){throw failure;}catch(Exception failure){throw new IOException("模型清单读取失败",failure);}
        }
    }
    private static long expectedSize(Context context,Spec spec) throws IOException {
        if(spec.size>0)return spec.size;
        try(AssetFileDescriptor afd=context.getAssets().openFd("model/"+spec.filename)){return afd.getLength();}
    }
    private static boolean verified(File file,long size,String hash) throws IOException {
        String key=file.getCanonicalPath(), fingerprint=size+":"+file.length()+":"+file.lastModified()+":"+hash;
        if(file.isFile() && fingerprint.equals(VERIFIED.get(key)))return true;
        if(!ModelInstaller.verified(file,size,hash))return false;
        VERIFIED.put(key,fingerprint);return true;
    }
    private static void remember(File file,long size,String hash)throws IOException {
        VERIFIED.put(file.getCanonicalPath(),size+":"+file.length()+":"+file.lastModified()+":"+hash);
    }
    private static void publish(ModelPrep.Progress progress,long done,long total,String file) {
        if(progress!=null)progress.onProgress(done,total,file);
    }
}

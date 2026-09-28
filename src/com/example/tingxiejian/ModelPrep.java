package com.example.tingxiejian;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Unpacks the bundled models from the APK into app-private storage on first use.
 *
 * The models ship inside the APK (nothing is downloaded), but Android's asset reader needs real
 * files on disk, so the first launch copies ~500 MB. That copy is the only "setup" this app has,
 * which is why it reports byte-level progress instead of a spinner.
 */
final class ModelPrep {
    /** Names must match the resources build.sh stores into assets/model/. */
    static final String[] FILES = {
            "stream-encoder.onnx",
            "stream-decoder.onnx",
            "stream-joiner.onnx",
            "stream-tokens.txt",
            "offline-paraformer.onnx",
            "offline-tokens.txt",
            "punct.onnx",
            "diar-segmentation.onnx",
            "diar-embedding.onnx",
    };

    interface Progress {
        void onProgress(long doneBytes, long totalBytes, String currentFile);
    }

    static File dir(Context context) {
        return new File(context.getFilesDir(), "models-v1");
    }

    static long totalBytes(Context context) throws IOException {
        long total = 0;
        for (String name : FILES) {
            try (AssetFileDescriptor afd = context.getAssets().openFd("model/" + name)) {
                total += afd.getLength();
            }
        }
        return total;
    }

    static long doneBytes(Context context) {
        long done = 0;
        File dir = dir(context);
        for (String name : FILES) {
            File file = new File(dir, name);
            if (file.isFile()) {
                done += file.length();
            }
        }
        return done;
    }

    /** True when every model file is already unpacked. */
    static boolean ready(Context context) {
        File dir = dir(context);
        for (String name : FILES) {
            File file = new File(dir, name);
            if (!file.isFile() || file.length() == 0) {
                return false;
            }
        }
        return true;
    }

    /** Copies anything that is missing. Safe to call repeatedly; already-good files are skipped. */
    static void prepare(Context context, Progress progress) throws IOException {
        if (!dir(context).isDirectory() && !dir(context).mkdirs()) {
            throw new IOException("无法创建私有模型目录");
        }
        long total = totalBytes(context);
        long done = 0;
        byte[] buffer = new byte[256 * 1024];
        for (String name : FILES) {
            long expected;
            try (AssetFileDescriptor afd = context.getAssets().openFd("model/" + name)) {
                expected = afd.getLength();
            }
            File file = new File(dir(context), name);
            if (file.isFile() && file.length() == expected) {
                done += expected;
                publish(progress, done, total, name);
                continue;
            }
            File temp = new File(dir(context), name + ".partial");
            try (InputStream in = context.getAssets().open("model/" + name);
                 FileOutputStream out = new FileOutputStream(temp)) {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    done += n;
                    publish(progress, done, total, name);
                }
            }
            if (temp.length() != expected) {
                throw new IOException("模型文件写入不完整：" + name);
            }
            if (!temp.renameTo(file)) {
                throw new IOException("模型文件不能移动到私有目录：" + name);
            }
        }
    }

    private static void publish(Progress progress, long done, long total, String name) {
        if (progress != null) {
            progress.onProgress(done, total, name);
        }
    }
}

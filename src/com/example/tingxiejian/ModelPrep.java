package com.example.tingxiejian;

import android.content.Context;
import java.io.File;
import java.io.IOException;

/** First-use setup prepares low-latency core only. Optional capabilities are prepared on demand. */
final class ModelPrep {
    static final String[] FILES = {
        "stream-encoder.onnx", "stream-decoder.onnx", "stream-joiner.onnx", "stream-tokens.txt"
    };
    interface Progress { void onProgress(long doneBytes,long totalBytes,String currentFile); }
    static File dir(Context context) {return ModelManager.dir(context);}
    static long totalBytes(Context context)throws IOException {return ModelManager.totalBytes(context,ModelManager.Pack.CORE_STREAMING);}
    static long doneBytes(Context context) {return ModelManager.doneBytes(context,ModelManager.Pack.CORE_STREAMING);}
    static boolean ready(Context context) {return ModelManager.ready(context,ModelManager.Pack.CORE_STREAMING);}
    static synchronized void prepare(Context context,Progress progress)throws IOException {
        ModelManager.prepare(context,ModelManager.Pack.CORE_STREAMING,progress);
    }
}

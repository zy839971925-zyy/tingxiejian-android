package com.example.tingxiejian;
import android.content.Context;
import org.json.JSONObject;
import java.io.*;
/** File adapter; recognition core is shared with live dictation. */
final class Transcriber {
    interface Events {void emit(JSONObject event)throws IOException;}
    private final Context context;
    Transcriber(Context context){this.context=context.getApplicationContext();}
    void transcribe(File input,boolean speakers,int count,Events out)throws Exception{
        try(SpeechPipeline pipeline=new SpeechPipeline(context,out)){
            pipeline.phase("正在逐段解码并识别录音");
            new FileAudioSource(input).pump(pipeline::accept);
            out.emit(pipeline.finish(speakers,count));
        }
    }
}

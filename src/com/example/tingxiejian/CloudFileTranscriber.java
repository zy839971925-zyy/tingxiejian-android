package com.example.tingxiejian;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;

/** File/cloud business pipeline. Service owns cancellation and lifecycle, not chunk assembly. */
final class CloudFileTranscriber implements PcmDecoder.Sink {
    interface Cancellation {void check() throws Exception;}
    interface Events {void emit(JSONObject event) throws Exception;}
    private final Context app;
    private final Cancellation cancellation;
    private final Events events;
    CloudFileTranscriber(Context context,String sessionId,Cancellation cancellation,Events events){
        app=context.getApplicationContext();this.cancellation=cancellation;this.events=events;
        textCorrectionEnabled=Cloud.configured(app);
        transcript=new CloudTranscript(sessionId,HotwordRepository.load(app),textCorrectionEnabled,
                Cloud.asrModel(app),Cloud.chatModel(app));
    }
    void transcribe(File input)throws Exception {PcmDecoder.decode(input,this);finish();}
    final int sampleRate = 16000;
    final float[] buffer = new float[sampleRate * 120];
    int filled;
    double chunkStart;
    int completed;
    double total;
    long lastEvent;
    final boolean textCorrectionEnabled;
    final CloudTranscript transcript;
    boolean finishing;

    @Override
    public void accept(float[] samples, int length, double seconds, double totalSecondsIn) throws Exception {
        cancellation.check();
        if (totalSecondsIn > 0) total = totalSecondsIn;
        int offset = 0;
        while (offset < length) {
            cancellation.check();
            int take = Math.min(length - offset, buffer.length - filled);
            System.arraycopy(samples, offset, buffer, filled, take);
            filled += take;
            offset += take;
            if (filled == buffer.length) {
                uploadChunk();
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastEvent > 250) {
            lastEvent = now;
            postProgress(seconds);
        }
    }

    void uploadChunk() throws Exception {
        cancellation.check();
        if (filled == 0) return;
        float[] chunk = new float[filled];
        System.arraycopy(buffer, 0, chunk, 0, filled);
        double end = chunkStart + (double) filled / sampleRate;
        byte[] wav = Cloud.toWav(chunk);
        String chunkText = Cloud.asr(app, wav, null);
        cancellation.check(); // An in-flight HTTP request may finish after the user stops.
        if (chunkText != null && !chunkText.trim().isEmpty()) {
            String clean = chunkText.trim();
            JSONObject segment = transcript.append(clean, chunkStart, end, (faithful, words) -> {
                cancellation.check();
                events.emit(new JSONObject().put("type", "segment").put("message", "语句已记录")
                        .put("segment", faithful));
                if (finishing) events.emit(new JSONObject().put("type", "phase").put("state", "POLISHING")
                        .put("message", "正在完成文字轻度校正"));
                String edited = TranscriptPolisher.polishSegment(app, faithful, words);
                cancellation.check();
                return edited;
            });
            if (!textCorrectionEnabled) events.emit(new JSONObject().put("type", "segment")
                    .put("message", "语句已记录").put("segment", segment));
            if (segment.has("polished_text")) events.emit(new JSONObject().put("type", "polished-segment")
                    .put("message", "文字轻度校正完成").put("segment_id", segment.getString("segment_id"))
                    .put("polished_text", segment.getString("polished_text")));
        }
        completed++;
        chunkStart = end;
        filled = 0;
        postProgress(end);
    }

    void postProgress(double processed) throws Exception {
        JSONObject event = new JSONObject();
        event.put("type", "progress");
        event.put("processed", processed);
        event.put("total", total);
        event.put("completed", completed);
        event.put("message", "云端识别");
        events.emit(event);
    }

    void finish() throws Exception {
        cancellation.check();
        finishing = true;
        events.emit(new JSONObject().put("type", "phase").put("state", "FINALIZING")
                .put("message", "正在完成云端识别"));
        uploadChunk();
        events.emit(transcript.result(Math.max(total, chunkStart)));
    }
}

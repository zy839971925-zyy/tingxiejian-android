package com.example.tingxiejian;
import java.io.File;
final class FileAudioSource implements AudioSource {
    private final File file;FileAudioSource(File file){this.file=file;}
    public double pump(PcmDecoder.Sink sink)throws Exception{return PcmDecoder.decode(file,sink);}
}

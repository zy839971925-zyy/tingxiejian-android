package com.example.tingxiejian;
/** Sources differ; recognition receives 16 kHz mono PCM through one shared core. */
interface AudioSource {double pump(PcmDecoder.Sink sink)throws Exception;}

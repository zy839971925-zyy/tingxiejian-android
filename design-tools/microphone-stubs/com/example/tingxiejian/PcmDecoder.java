package com.example.tingxiejian;
/** Test-only sink contract, identical to PcmDecoder.Sink; codec/Android SDK are unnecessary. */
final class PcmDecoder {
    interface Sink { void accept(float[] samples, int length, double seconds, double total) throws Exception; }
    private PcmDecoder() { }
}

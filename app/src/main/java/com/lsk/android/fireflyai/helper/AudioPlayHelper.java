package com.lsk.android.fireflyai.helper;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioPlayHelper {
    private static final String TAG = "AudioPlayHelper";

    // --- CONFIG ---
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioTrack audioTrack;
    private Thread playThread;
    private int minBufSize;
    private LinkedBlockingQueue<byte[]> audioDataQueue;
    private AtomicBoolean isPlaying = new AtomicBoolean(false);

    public AudioPlayHelper() {
        minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufSize < 0) {
            Log.e(TAG, "initialize: cannot get min buffer size: " + minBufSize);
            return;
        }

    }
}

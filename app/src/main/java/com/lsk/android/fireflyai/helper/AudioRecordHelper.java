package com.lsk.android.fireflyai.helper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;

import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class AudioRecordHelper {
    private static final String TAG = "AudioRecordHelper";

    // ---- CONFIG ----
    private static final int SAMPLE_RATE = 32000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private BiConsumer<byte[], Integer> recordCb;
    private AtomicBoolean isRecording;
    private Thread recordThread;


    public boolean checkMicrophonePermission(Context owner) {
        return ContextCompat.checkSelfPermission(owner, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void setRecordCb(BiConsumer<byte[], Integer> recordCb) {
        this.recordCb = recordCb;
    }

    public void startRecording() {

    }
}

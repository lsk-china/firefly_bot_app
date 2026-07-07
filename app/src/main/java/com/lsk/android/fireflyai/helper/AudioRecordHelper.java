package com.lsk.android.fireflyai.helper;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.RequiresPermission;
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
    private int minBufSize;

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void initialize() {
        minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufSize < 0) {
            Log.e(TAG, "initialize: cannot get min buffer size: " + minBufSize);
            return;
        }
        this.audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufSize * 2
        );
        if (audioRecord.getState() !=  AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "initialize: audio hardware initialization failed: "
                    + audioRecord.getState());
            return;
        }
    }

    public boolean checkMicrophonePermission(Context owner) {
        return ContextCompat.checkSelfPermission(owner, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void startRecording() {
        this.isRecording.set(true);
        this.audioRecord.startRecording();
        this.recordThread = new Thread(() -> {
            byte[] buffer = new byte[minBufSize];
            while (isRecording.get()) {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && recordCb != null) {
                    recordCb.accept(buffer, bytesRead);
                } else if (bytesRead < 0) {
                    Log.e(TAG, "record: AudioRecord#read failed: " + bytesRead);
                    break;
                }
            }
        }, "AudioRecordThread");
        this.recordThread.start();
    }

    /*
     * According to Gemini, I shouldn't just stop the thread and AudioRecorder, which may lead to
     * an audio misbehavior for a few hundreds of milliseconds when the record is resumed.
     * But since the audio is piped directly to ASR model, not for human to listen, so I think
     * it's OK to just stop recording completely when the Activity is paused. And perhaps this
     * method will never be called.
     */
    public void stopRecording() {
        if (!this.isRecording.get()) return;
        this.isRecording.set(false);
        if (this.recordThread != null) {
            try {
                this.recordThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.recordThread = null;
        }
        if (this.audioRecord != null) {
            try {
                this.audioRecord.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "stopRecording: AudioRecord#stop failed", e);
            }
        }
    }

    public void destroy() {
        stopRecording();
        if (this.audioRecord != null) {
            this.audioRecord.release();
            this.audioRecord = null;
        }
    }

    public void setRecordCb(BiConsumer<byte[], Integer> recordCb) {
        this.recordCb = recordCb;
    }

}

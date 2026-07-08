package com.lsk.android.fireflyai.helper;

import android.media.AudioAttributes;
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
    private LinkedBlockingQueue<byte[]> audioDataQueue = new LinkedBlockingQueue<>();
    private AtomicBoolean isPlaying = new AtomicBoolean(false);

    public AudioPlayHelper() {
        minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufSize < 0) {
            Log.e(TAG, "initialize: cannot get min buffer size: " + minBufSize);
            return;
        }
        this.audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .setEncoding(AUDIO_FORMAT)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
    }

    public void start() {
        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioPlayHelper: AudioTrack init failed: " + audioTrack.getState());
            return;
        }
        isPlaying.set(true);
        audioDataQueue.clear();
        audioTrack.play();
        playThread = new Thread(() -> {
            while (isPlaying.get()) {
                try {
                    byte[] chunk = audioDataQueue.take();
                    if (chunk != null && audioTrack != null) {
                        audioTrack.write(chunk, 0, chunk.length);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "start: interrupted", e);
                    break;
                }
            }
        }, "AudioPlayThread");
        playThread.start();
    }

    public void stop() {
        isPlaying.set(false);
        if (this.playThread != null) {
            try {
                this.playThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.playThread = null;
        }
        if (this.audioTrack != null) {
            try {
                this.audioTrack.stop();
                this.audioTrack.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "stop: cannot stop audioTrack", e);
            } finally {
                this.audioTrack = null;
                this.audioDataQueue.clear();
            }
        }
    }
}

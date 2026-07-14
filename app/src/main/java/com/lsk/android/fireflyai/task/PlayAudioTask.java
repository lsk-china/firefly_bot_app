package com.lsk.android.fireflyai.task;

import com.lsk.android.fireflyai.helper.AudioPlayHelper;
import com.lsk.android.fireflyai.helper.WebsocketHelper;

import java.util.function.Consumer;

public class PlayAudioTask {
    private static final String TAG = "PlayAudioTask";
    private static final String ENDPOINT = "/audio";
    private final AudioPlayHelper audioPlayHelper;
    private WebsocketHelper websocketHelper;

    public PlayAudioTask(String baseURL) {
        this(baseURL, null);
    }

    public PlayAudioTask(String baseURL, Consumer<String> statusHandler) {
        this.audioPlayHelper = new AudioPlayHelper();
        this.websocketHelper = new WebsocketHelper(baseURL + ENDPOINT);
        this.websocketHelper.setStatusHandler(statusHandler);
    }

    public void start() {
        this.audioPlayHelper.start();
        this.websocketHelper.setRawHandler(byteBuffer -> {
            audioPlayHelper.putData(byteBuffer.array());
        });
        this.websocketHelper.start();
    }

    public void stop() {
        this.websocketHelper.stop();
        this.audioPlayHelper.stop();
    }
}

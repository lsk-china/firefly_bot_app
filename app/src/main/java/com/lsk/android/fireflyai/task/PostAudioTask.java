package com.lsk.android.fireflyai.task;

import com.lsk.android.fireflyai.helper.AudioRecordHelper;
import com.lsk.android.fireflyai.helper.WebsocketHelper;

import java.util.function.Consumer;

public class PostAudioTask {
    private static final String TAG = "PostAudioTask";
    private static final String ENDPOINT = "/microphone";

    private final AudioRecordHelper audioRecordHelper;
    private final WebsocketHelper websocketHelper;

    public PostAudioTask(AudioRecordHelper audioRecordHelper, String baseURL) {
        this(audioRecordHelper, baseURL, null);
    }

    public PostAudioTask(AudioRecordHelper audioRecordHelper,
                         String baseURL,
                         Consumer<String> statusHandler) {
        this.audioRecordHelper = audioRecordHelper;
        this.websocketHelper = new WebsocketHelper(baseURL + ENDPOINT);
        this.websocketHelper.setStatusHandler(statusHandler);
        this.audioRecordHelper.setRecordCb((data, length) -> {
            byte[] dataToSend;
            // Remove trailing zeros if needed
            if (data.length == length) {
                dataToSend = data;
            } else {
                dataToSend = new byte[length];
                System.arraycopy(data, 0, dataToSend, 0, length);
            }
            websocketHelper.send(dataToSend);
        });
    }

    public void start() {
        this.audioRecordHelper.startRecording();
        this.websocketHelper.start();
    }

    public void stop() {
        this.audioRecordHelper.stopRecording();
    }

    public void destroy() {
        this.audioRecordHelper.destroy();
        this.websocketHelper.stop();
    }

}

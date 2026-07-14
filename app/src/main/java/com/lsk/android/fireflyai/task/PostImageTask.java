package com.lsk.android.fireflyai.task;

import android.media.Image;

import com.lsk.android.fireflyai.helper.CameraHelper;
import com.lsk.android.fireflyai.helper.WebsocketHelper;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class PostImageTask implements Runnable {
    private static final String TAG = "PostImageTask";
    private final CameraHelper cameraHelper;
    private final WebsocketHelper wsHelper;

    public PostImageTask(CameraHelper cameraHelper, String baseUrl) {
        this(cameraHelper, baseUrl, null);
    }

    public PostImageTask(CameraHelper cameraHelper, String baseUrl, Consumer<String> statusHandler) {
        this.cameraHelper = cameraHelper;
        // Endpoint: ws://ip:port/postImage
        this.wsHelper = new WebsocketHelper(baseUrl + "/postImage");
        this.wsHelper.setStatusHandler(statusHandler);
        this.wsHelper.start();
    }

    @Override
    public void run() {
        cameraHelper.captureImage(buffer -> {
            // Runs in the background thread of CameraHelper,
            // so it's Ok to perform network operations.
            wsHelper.send(buffer.array());
        });
    }

    public void cleanup() {
        this.wsHelper.stop();
    }
}

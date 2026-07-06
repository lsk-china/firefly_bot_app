package com.lsk.android.fireflyai;

import android.os.Bundle;

import com.lsk.android.fireflyai.helper.CameraHelper;
import com.lsk.android.fireflyai.helper.IntervalHelper;
import com.lsk.android.fireflyai.task.PostImageTask;
import com.unity3d.player.UnityPlayerActivity;

public class MainActivity extends UnityPlayerActivity {
    private static final String TAG = "MainActivity";
    // TODO: Replace with real IP
    private static final String BASE_URL = "ws://";
    private static final int POST_IMAGE_INTERVAL = 2000; // post image every 2 seconds;

    private CameraHelper cameraHelper;
    private PostImageTask postImageTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.cameraHelper = new CameraHelper(this);
        // Check camera permission
        if (!cameraHelper.checkCameraPermission()) {
            cameraHelper.requestCameraPermission();
            return;
        }
        cameraHelper.startCamera();
        this.postImageTask = new PostImageTask(cameraHelper, BASE_URL);
        IntervalHelper.setInterval(postImageTask, POST_IMAGE_INTERVAL);
    }

    /*
        Resume IntervalHelper after CameraHelper, and pause it after CameraHelper.
        So when the capture task is run, the camera is always available.
     */

    @Override
    protected void onResume() {
        super.onResume();
        cameraHelper.resumeCamera();
        IntervalHelper.resume();
    }

    @Override
    protected void onPause() {
        IntervalHelper.pause();
        cameraHelper.stopCamera();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        cameraHelper.handleRequestPermissionResult(requestCode, permissions, results);
    }

    @Override
    protected void onDestroy() {
        postImageTask.cleanup();
        super.onDestroy();
    }


}

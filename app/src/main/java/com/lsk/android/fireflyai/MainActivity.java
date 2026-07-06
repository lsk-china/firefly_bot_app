package com.lsk.android.fireflyai;

import android.os.Bundle;

import com.lsk.android.fireflyai.helper.CameraHelper;
import com.lsk.android.fireflyai.helper.IntervalHelper;
import com.unity3d.player.UnityPlayerActivity;

public class MainActivity extends UnityPlayerActivity {
    private static final String TAG = "MainActivity";
    private CameraHelper cameraHelper;


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
        super.onDestroy();
    }


}

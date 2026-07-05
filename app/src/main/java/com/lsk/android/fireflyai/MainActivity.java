package com.lsk.android.fireflyai;

import android.os.Bundle;

import com.lsk.android.fireflyai.helper.CameraHelper;
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


    @Override
    protected void onResume() {
        super.onResume();
        cameraHelper.resumeCamera();

    }

    @Override
    protected void onPause() {
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

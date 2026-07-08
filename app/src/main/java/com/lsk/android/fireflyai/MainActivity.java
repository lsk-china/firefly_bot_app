package com.lsk.android.fireflyai;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.lsk.android.fireflyai.helper.AudioRecordHelper;
import com.lsk.android.fireflyai.helper.CameraHelper;
import com.lsk.android.fireflyai.helper.IntervalHelper;
import com.lsk.android.fireflyai.task.ChangeEmotionTask;
import com.lsk.android.fireflyai.task.PlayAudioTask;
import com.lsk.android.fireflyai.task.PostAudioTask;
import com.lsk.android.fireflyai.task.PostImageTask;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;

public class MainActivity extends UnityPlayerActivity {
    private static final String TAG = "MainActivity";
    // TODO: Replace with real IP
    private static final String BASE_URL = "ws://";
    private static final int POST_IMAGE_INTERVAL = 2000; // post image every 2 seconds;

    private CameraHelper cameraHelper;
    private AudioRecordHelper audioRecordHelper;

    private PostImageTask postImageTask;
    private PostAudioTask postAudioTask;
    private ChangeEmotionTask changeEmotionTask;
    private PlayAudioTask playAudioTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.cameraHelper = new CameraHelper(this);
        this.audioRecordHelper = new AudioRecordHelper();
        boolean init = true;
        // Check permission
        if (!cameraHelper.checkCameraPermission()) {
            cameraHelper.requestCameraPermission();
            init = false;
        }
        if (!audioRecordHelper.checkMicrophonePermission(this)) {
            audioRecordHelper.requestForMicrophonePermission(this);
            init = false;
        }
        if (init) {
            cameraHelper.startCamera();
            audioRecordHelper.initialize();
            this.postImageTask = new PostImageTask(cameraHelper, BASE_URL);
            this.postAudioTask = new PostAudioTask(audioRecordHelper, BASE_URL);
            this.changeEmotionTask = new ChangeEmotionTask(this, BASE_URL);
            this.playAudioTask = new PlayAudioTask(BASE_URL);
            IntervalHelper.setInterval(postImageTask, POST_IMAGE_INTERVAL);
            changeEmotionTask.start();
            playAudioTask.start();

        }
        UnityPlayer.UnitySendMessage("MyCharacter", "SetVisibility", "hide");
    }

    /*
     * Resume IntervalHelper after CameraHelper, and pause it after CameraHelper.
     * So when the capture task is run, the camera is always available.
     */

    @Override
    protected void onResume() {
        super.onResume();
        cameraHelper.resumeCamera();
        postAudioTask.start();
        IntervalHelper.resume();
    }

    @Override
    protected void onPause() {
        IntervalHelper.pause();
        cameraHelper.stopCamera();
        postAudioTask.stop();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] results
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        cameraHelper.handleRequestPermissionResult(requestCode, permissions, results);
        audioRecordHelper.handlePermissionRequestResult(requestCode, permissions, results);
    }

    @Override
    protected void onDestroy() {
        postImageTask.cleanup();
        postAudioTask.destroy();
        changeEmotionTask.stop();
        playAudioTask.stop();
        super.onDestroy();
    }


}

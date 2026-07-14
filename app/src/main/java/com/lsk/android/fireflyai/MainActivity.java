package com.lsk.android.fireflyai;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.lsk.android.fireflyai.helper.AudioRecordHelper;
import com.lsk.android.fireflyai.helper.CameraHelper;
import com.lsk.android.fireflyai.helper.IntervalHelper;
import com.lsk.android.fireflyai.task.ChangeEmotionTask;
import com.lsk.android.fireflyai.task.PlayAudioTask;
import com.lsk.android.fireflyai.task.PostAudioTask;
import com.lsk.android.fireflyai.task.PostImageTask;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends UnityPlayerActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RUNTIME_PERMISSIONS = 100;
    private static final String PREFS_NAME = "firefly_settings";
    private static final String PREF_BASE_URL = "base_url";
    private static final String DEFAULT_BASE_URL = "ws://";
    private static final int POST_IMAGE_INTERVAL = 2000; // post image every 2 seconds;
    private static final long CONNECTION_TOAST_INTERVAL_MS = 5000;

    private CameraHelper cameraHelper;
    private AudioRecordHelper audioRecordHelper;

    private PostImageTask postImageTask;
    private PostAudioTask postAudioTask;
    private ChangeEmotionTask changeEmotionTask;
    private PlayAudioTask playAudioTask;
    private boolean cameraStarted = false;
    private boolean appStarted = false;
    private boolean baseUrlConfirmed = false;
    private boolean configDialogShown = false;
    private boolean configDialogShowing = false;
    private boolean permissionRequestInFlight = false;
    private boolean permissionRequestAttempted = false;
    private String baseUrl = "";
    private long lastConnectionToastMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.cameraHelper = new CameraHelper(this);
        this.audioRecordHelper = new AudioRecordHelper();
        this.baseUrl = getPreferences().getString(PREF_BASE_URL, "");
        requestMissingPermissionsIfNeeded();
        UnityPlayer.UnitySendMessage("MyCharacter", "SetVisibility", "hide");
    }

    private boolean hasAllRequiredPermissions() {
        return cameraHelper.checkCameraPermission()
                && audioRecordHelper.checkMicrophonePermission(this);
    }

    private boolean isBackendConfigured() {
        return baseUrl != null && !baseUrl.isEmpty();
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private String normalizeBaseUrl(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || DEFAULT_BASE_URL.equals(text)) {
            return null;
        }
        if (!text.contains("://")) {
            text = DEFAULT_BASE_URL + text;
        }

        Uri uri = Uri.parse(text);
        String scheme = uri.getScheme();
        if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
            return null;
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            return null;
        }
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            return null;
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private void showBaseUrlDialogIfReady() {
        if (configDialogShown || configDialogShowing || !hasAllRequiredPermissions()) {
            return;
        }

        configDialogShowing = true;
        configDialogShown = true;

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSelectAllOnFocus(true);
        input.setHint("ws://192.168.1.23:8765");
        input.setText(isBackendConfigured() ? baseUrl : DEFAULT_BASE_URL);

        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(padding, padding / 2, padding, 0);
        layout.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Server URL")
                .setView(layout)
                .setPositiveButton("Connect", null)
                .setNegativeButton("Camera only", (d, which) -> {
                    baseUrlConfirmed = false;
                })
                .create();
        dialog.setOnDismissListener(d -> configDialogShowing = false);
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String normalizedBaseUrl = normalizeBaseUrl(input.getText().toString());
                if (normalizedBaseUrl == null) {
                    input.setError("Use ws://host:port or wss://host:port");
                    Toast.makeText(this, "Invalid server URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                baseUrl = normalizedBaseUrl;
                baseUrlConfirmed = true;
                getPreferences().edit().putString(PREF_BASE_URL, baseUrl).apply();
                dialog.dismiss();
                startAppIfPermissionsGranted();
            });
        });
        dialog.show();
    }

    private void showConnectionStatus(String message) {
        runOnUiThread(() -> {
            long now = System.currentTimeMillis();
            if (now - lastConnectionToastMs < CONNECTION_TOAST_INTERVAL_MS) {
                return;
            }
            lastConnectionToastMs = now;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void requestMissingPermissionsIfNeeded() {
        if (hasAllRequiredPermissions()
                || permissionRequestInFlight
                || permissionRequestAttempted) {
            return;
        }

        List<String> missingPermissions = new ArrayList<>();
        if (!cameraHelper.checkCameraPermission()) {
            missingPermissions.add(Manifest.permission.CAMERA);
        }
        if (!audioRecordHelper.checkMicrophonePermission(this)) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO);
        }
        permissionRequestInFlight = true;
        permissionRequestAttempted = true;
        ActivityCompat.requestPermissions(
                this,
                missingPermissions.toArray(new String[0]),
                REQUEST_RUNTIME_PERMISSIONS
        );
    }

    private boolean startCameraIfPermissionGranted() {
        if (cameraStarted) {
            return false;
        }
        if (!cameraHelper.checkCameraPermission()) {
            return false;
        }

        cameraHelper.startCamera();
        cameraStarted = true;
        return true;
    }

    private boolean startAppIfPermissionsGranted() {
        if (appStarted) {
            return false;
        }
        if (!hasAllRequiredPermissions()) {
            return false;
        }
        if (!baseUrlConfirmed) {
            return false;
        }
        if (!isBackendConfigured()) {
            return false;
        }

        startCameraIfPermissionGranted();
        audioRecordHelper.initialize();
        this.postImageTask = new PostImageTask(cameraHelper, baseUrl, this::showConnectionStatus);
        this.postAudioTask = new PostAudioTask(audioRecordHelper, baseUrl, this::showConnectionStatus);
        this.changeEmotionTask = new ChangeEmotionTask(this, baseUrl, this::showConnectionStatus);
        this.playAudioTask = new PlayAudioTask(baseUrl, this::showConnectionStatus);
        IntervalHelper.setInterval(postImageTask, POST_IMAGE_INTERVAL);
        postAudioTask.start();
        changeEmotionTask.start();
        playAudioTask.start();
        appStarted = true;
        return true;
    }

    /*
     * Resume IntervalHelper after CameraHelper, and pause it after CameraHelper.
     * So when the capture task is run, the camera is always available.
     */

    @Override
    protected void onResume() {
        super.onResume();
        boolean cameraStartedNow = startCameraIfPermissionGranted();
        boolean appStartedNow = startAppIfPermissionsGranted();
        if (!cameraStarted && !appStarted) {
            requestMissingPermissionsIfNeeded();
            return;
        }
        showBaseUrlDialogIfReady();
        if (cameraStarted && !cameraStartedNow) {
            cameraHelper.resumeCamera();
        }
        if (appStarted && !appStartedNow) {
            IntervalHelper.resume();
        }
        if (appStarted && postAudioTask != null) {
            postAudioTask.start();
        }
    }

    @Override
    protected void onPause() {
        IntervalHelper.pause();
        if (cameraHelper != null && cameraStarted) {
            cameraHelper.stopCamera();
        }
        if (postAudioTask != null) {
            postAudioTask.stop();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] results
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_RUNTIME_PERMISSIONS) {
            permissionRequestInFlight = false;
            startCameraIfPermissionGranted();
            showBaseUrlDialogIfReady();
            if (!cameraHelper.checkCameraPermission()) {
                Log.e(TAG, "Camera permission is required for preview");
            } else if (!audioRecordHelper.checkMicrophonePermission(this)) {
                Log.e(TAG, "Microphone permission is required for audio streaming");
            }
            return;
        }
        cameraHelper.handleRequestPermissionResult(requestCode, permissions, results);
        audioRecordHelper.handlePermissionRequestResult(requestCode, permissions, results);
    }

    @Override
    protected void onDestroy() {
        if (postImageTask != null) {
            postImageTask.cleanup();
        }
        if (postAudioTask != null) {
            postAudioTask.destroy();
        }
        if (changeEmotionTask != null) {
            changeEmotionTask.stop();
        }
        if (playAudioTask != null) {
            playAudioTask.stop();
        }
        super.onDestroy();
    }


}

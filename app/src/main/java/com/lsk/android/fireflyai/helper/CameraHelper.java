package com.lsk.android.fireflyai.helper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;

public class CameraHelper {

    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;

    private SurfaceView cameraSurfaceView;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private final Activity owner;

    private String cameraID;
    private ImageReader imageReader;

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String TAG = "CameraHelper";

    public CameraHelper(Activity owner) {
        this.owner = owner;
    }

    public boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(owner, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void closeCaptureSession() {
        if (this.captureSession != null) {
            this.captureSession.close();
            this.captureSession = null;
        }
    }

    private void createCaptureSession(Surface surface) {
        try {
            closeCaptureSession();
            if (imageReader == null) {
                imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2);
            }

            this.previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            this.previewRequestBuilder.addTarget(surface);

            CameraCaptureSession.StateCallback sessionStateCb =
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession
                        ) {
                            Log.e(TAG, "onConfigureFailed");
                        }

                        @Override
                        public void onConfigured(
                                @NonNull CameraCaptureSession cameraCaptureSession
                        ) {
                            captureSession = cameraCaptureSession;
                            previewRequestBuilder.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            );
                            try {
                                captureSession.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        null,
                                        backgroundHandler
                                );
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "cannot start preview", e);
                            }
                        }
                    };

            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, imageReader.getSurface()),
                    sessionStateCb,
                    backgroundHandler
            );
        } catch (CameraAccessException e) {
            Log.e(TAG, "cannot create capture session", e);
        }
    }
    public void initCamera() {
        this.cameraManager = (CameraManager) owner.getSystemService(Context.CAMERA_SERVICE);
        this.cameraSurfaceView = new SurfaceView(owner);
        cameraSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder,
                                       int format,
                                       int width,
                                       int height) {
            }

            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                if (cameraDevice != null) {
                    // Create camera preview session
                    createCaptureSession(surfaceHolder.getSurface());
                }
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                closeCaptureSession();
            }


        });
        int density = (int) owner.getResources().getDisplayMetrics().density;
        int sizeW = 176 * density;
        int sizeH = 144 * density;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizeW, sizeH);
        params.gravity = Gravity.TOP | Gravity.END;
        params.topMargin = 20 * density;
        params.rightMargin = 20 * density;
        cameraSurfaceView.setLayoutParams(params);
        cameraSurfaceView.setZOrderOnTop(true);
        cameraSurfaceView.setZOrderMediaOverlay(true);

        FrameLayout unityLayout = owner.findViewById(android.R.id.content);
        unityLayout.addView(cameraSurfaceView, params);
    }

    public void startBackgroundThread() {
        this.backgroundThread = new HandlerThread("CameraBackground");
        this.backgroundThread.start();
        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());
    }

    private String getFrontCameraID() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(id);
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access cameras", e);
        }
        return null;
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            Log.e(TAG, "onError: " + i);
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            SurfaceHolder holder = cameraSurfaceView.getHolder();
            if (holder.getSurface() != null && holder.getSurface().isValid()) {
                createCaptureSession(holder.getSurface());
            }
        }
    };

    public void openCamera() {
        try {
            // Obtain Camera ID
            cameraID = getFrontCameraID();
            if (cameraID == null) {
                Log.e(TAG, "Cannot find front camera");
                return;
            }
            CameraCharacteristics characteristics =
                    cameraManager.getCameraCharacteristics(cameraID);
            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Log.i(TAG, "openCamera: supported sizes:");
                for (Size size : map.getOutputSizes(SurfaceTexture.class)) {
                    Log.d(TAG, "openCamera: " + size.getWidth() + "*" + size.getHeight());
                }
            } else {
                Log.e(TAG, "openCamera: map is null");
            }

            if (ActivityCompat.checkSelfPermission(owner, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
            ) {
                cameraManager.openCamera(
                        cameraID,
                        stateCallback,
                        backgroundHandler
                );
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera: ", e);
        }
    }

    public void closeCamera() {
        closeCaptureSession();
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    public void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "stopBackgroundThread: ", e);
            }
        }
    }

    public void startCamera() {
        initCamera();
        startBackgroundThread();
        openCamera();
    }

    public void stopCamera() {
        closeCamera();
        stopBackgroundThread();
    }

    public void resumeCamera() {
        startBackgroundThread();
        if (cameraDevice == null && checkCameraPermission()) {
            openCamera();
        }
    }

    public void handleRequestPermissionResult(int requestCode,
                                              @NonNull String[] permissions,
                                              @NonNull int[] grantResults
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera();
                startBackgroundThread();
                openCamera();
            }
        }
    }

    public void requestCameraPermission() {
        ActivityCompat.requestPermissions(owner,
                new String[]{ Manifest.permission.CAMERA },
                REQUEST_CAMERA_PERMISSION
        );
    }

    public void captureImage(Consumer<ByteBuffer> callback) {
        if (cameraDevice == null || captureSession == null || imageReader == null) {
            Log.e(TAG, "captureImage: camera is not ready!");
            return;
        }
        imageReader.setOnImageAvailableListener(imageReader -> {
            Image image = imageReader.acquireLatestImage();
            callback.accept(image.getPlanes()[0].getBuffer());
            image.close();
        }, backgroundHandler);
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureSession.stopRepeating();
            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.i(TAG, "onCaptureCompleted: successful captured, resuming preview");
                    resumePreview();
                }
            }, backgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "captureImage: failed to capture", e);
        }
    }

    private void resumePreview() {
        try {
            if (captureSession != null && previewRequestBuilder != null) {
                captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "resumePreview: cannot resume preview", e);
        }
    }
}

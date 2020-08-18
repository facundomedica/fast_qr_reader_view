package co.apperto.fastqrreaderview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.apperto.fastqrreaderview.common.CameraSource;
import co.apperto.fastqrreaderview.common.CameraSourcePreview;
import co.apperto.fastqrreaderview.java.barcodescanning.BarcodeScanningProcessor;
import co.apperto.fastqrreaderview.java.barcodescanning.OnCodeScanned;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterView;

/**
 * FastQrReaderViewPlugin
 */
public class FastQrReaderViewPlugin implements MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {

    private static final int CAMERA_REQUEST_ID = 513469796;
    private static final int REQUEST_PERMISSION = 47;
    private static final String TAG = "FastQrReaderViewPlugin";
    private static final SparseIntArray ORIENTATIONS =
            new SparseIntArray() {
                {
                    append(Surface.ROTATION_0, 0);
                    append(Surface.ROTATION_90, 90);
                    append(Surface.ROTATION_180, 180);
                    append(Surface.ROTATION_270, 270);
                }
            };

    private static CameraManager cameraManager;
    private static QrReader camera;
    private static MethodChannel channel;

    private final FlutterView view;

    private Activity activity;
    private Registrar registrar;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;
    private boolean requestingPermission;
    private Result permissionResult;

    private FastQrReaderViewPlugin(Registrar registrar, FlutterView view, Activity activity) {
        Log.d("TestSimone", "new instance FastQrReaderViewPlugin");
        this.registrar = registrar;
        this.view = view;
        this.activity = activity;
        this.activityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                Log.d("TestSimone", "onActivityCreated");
            }

            @Override
            public void onActivityStarted(Activity activity) {
                Log.d("TestSimone", "onActivityStarted");
            }

            @Override
            public void onActivityResumed(Activity activity) {
                Log.d("TestSimone", "onActivityResumed");
                if (requestingPermission) {
                    requestingPermission = false;
                    return;
                }
                if (activity == FastQrReaderViewPlugin.this.activity) {
                    if (camera != null) {
                        camera.startCameraSource();
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                Log.d("TestSimone", "onActivityPaused");
                if (activity == FastQrReaderViewPlugin.this.activity) {
                    if (camera != null) {
                        if (camera.preview != null) {
                            camera.preview.stop();

                        }
                    }
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                Log.d("TestSimone", "onActivityStopped");
                if (activity == FastQrReaderViewPlugin.this.activity) {
                    if (camera != null) {
                        if (camera.preview != null) {
                            camera.preview.stop();
                        }

                        if (camera.cameraSource != null) {
                            camera.cameraSource.release();
                        }
                    }
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                Log.d("TestSimone", "onActivitySaveInstanceState");
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                Log.d("TestSimone", "onActivityDestroyed");

            }
        };
        registrar.addRequestPermissionsResultListener(new CameraRequestPermissionsListener());
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        channel = new MethodChannel(registrar.messenger(), "fast_qr_reader_view");
        cameraManager = (CameraManager) registrar.activity().getSystemService(Context.CAMERA_SERVICE);
        channel.setMethodCallHandler(new FastQrReaderViewPlugin(registrar, registrar.view(), registrar.activity()));

        FastQrReaderViewPlugin plugin = new FastQrReaderViewPlugin(registrar, registrar.view(), registrar.activity());
        channel.setMethodCallHandler(plugin);
        registrar.addRequestPermissionsResultListener(plugin);
    }

    private void openSettings() {
        Activity activity = registrar.activity();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            Log.d("TestSimone", "Result permission ON CLASS BASE");
            // for each permission check if the user granted/denied them
            // you may want to group the rationale in a single dialog,
            // this is just an example
            for (int i = 0, len = permissions.length; i < len; i++) {
                String permission = permissions[i];
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // user rejected the permission
                    boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale( activity, permission );
                    if (! showRationale) {
                        // user also CHECKED "never ask again"
                        // you can either enable some fall back,
                        // disable features of your app
                        // or open another dialog explaining
                        // again the permission and directing to
                        // the app setting
                        permissionResult.success("dismissedForever");
                    } else {
                        permissionResult.success("denied");
                    }
                } else if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    permissionResult.success("granted");
                } else {
                    permissionResult.success("unknown");
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public void onMethodCall(MethodCall call, final Result result) {
        switch (call.method) {
            case "init":
                Log.d("TestSimone", "init");
                if (camera != null) {
                    camera.close();
                }
                result.success(null);
                break;
            case "availableCameras":
                Log.d("TestSimone", "availableCameras");
                try {
                    String[] cameraNames = cameraManager.getCameraIdList();
                    List<Map<String, Object>> cameras = new ArrayList<>();
                    for (String cameraName : cameraNames) {
                        HashMap<String, Object> details = new HashMap<>();
                        CameraCharacteristics characteristics =
                                cameraManager.getCameraCharacteristics(cameraName);
//                        Object test = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
//                        Log.d(TAG, "onMethodCall: "+test.toString());
                        details.put("name", cameraName);
                        @SuppressWarnings("ConstantConditions")
                        int lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        switch (lensFacing) {
                            case CameraMetadata.LENS_FACING_FRONT:
                                details.put("lensFacing", "front");
                                break;
                            case CameraMetadata.LENS_FACING_BACK:
                                details.put("lensFacing", "back");
                                break;
                            case CameraMetadata.LENS_FACING_EXTERNAL:
                                details.put("lensFacing", "external");
                                break;
                        }
                        cameras.add(details);
                    }
                    result.success(cameras);
                } catch (CameraAccessException e) {
                    result.error("cameraAccess", e.getMessage(), null);
                }
                break;
            case "initialize": {
                Log.d("TestSimone", "initialize");
                String cameraName = call.argument("cameraName");
                String resolutionPreset = call.argument("resolutionPreset");
                ArrayList<String> codeFormats = call.argument("codeFormats");

                if (camera != null) {
                    camera.close();
                }
                camera = new QrReader(cameraName, resolutionPreset, codeFormats, result);
                break;
            }
            case "startScanning":
                Log.d("TestSimone", "startScanning");
                startScanning(result);
                break;
            case "stopScanning":
                Log.d("TestSimone", "stopScanning");
                stopScanning(result);
                break;
            case "checkPermission":
                Log.d("TestSimone", "checkPermission");
                String permission;
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    permission = "granted";
                } else {
                    permission = "denied";
                }
                result.success(permission);
                break;
            case "requestPermission":
                Log.d("TestSimone", "requestPermission");
                this.permissionResult = result;
                Activity activity = registrar.activity();
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION);
                break;
            case "settings":
                Log.d("TestSimone", "settings");
                openSettings();
            case "toggleFlash":
                Log.d("TestSimone", "toggleFlash");
                toggleFlash(result);
                break;
            case "dispose": {
                Log.d("TestSimone", "dispose");
                if (camera != null) {
                    camera.dispose();
                }

                if (this.activity != null && this.activityLifecycleCallbacks != null) {
                    this.activity
                            .getApplication()
                            .unregisterActivityLifecycleCallbacks(this.activityLifecycleCallbacks);
                }
                result.success(null);
                break;
            }
            default:
                result.notImplemented();
                break;
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow.
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    void startScanning(@NonNull Result result) {
        camera.scanning = true;
        camera.barcodeScanningProcessor.shouldThrottle.set(false);
        result.success(null);
    }

    void stopScanning(@NonNull Result result) {
        stopScanning();
        result.success(null);
    }

    private void stopScanning() {
        camera.scanning = false;
        camera.barcodeScanningProcessor.shouldThrottle.set(true);
    }

    void toggleFlash(@NonNull Result result) {
        toggleFlash();
        result.success(null);
    }

    private void toggleFlash() {
        camera.cameraSource.toggleFlash();
    }

    private class QrReader {

        private static final int PERMISSION_REQUESTS = 1;

        private CameraSource cameraSource = null;
        private CameraSourcePreview preview;

        BarcodeScanningProcessor barcodeScanningProcessor;
        ArrayList<Integer> reqFormats;
        private final FlutterView.SurfaceTextureEntry textureEntry;
        private EventChannel.EventSink eventSink;
        private boolean isFrontFacing;
        private boolean scanning;
        private Size captureSize;
        private Runnable cameraPermissionContinuation;

        QrReader(final String cameraName, final String resolutionPreset, final ArrayList<String> formats, @NonNull final Result result) {
            Log.d("TestSimone", "" + cameraName + " " + resolutionPreset + " " + result);
            // AVAILABLE FORMATS:
            // enum CodeFormat { codabar, code39, code93, code128, ean8, ean13, itf, upca, upce, aztec, datamatrix, pdf417, qr }

            Map<String, Integer> map = new HashMap<>();
            map.put("codabar", FirebaseVisionBarcode.FORMAT_CODABAR);
            map.put("code39", FirebaseVisionBarcode.FORMAT_CODE_39);
            map.put("code93", FirebaseVisionBarcode.FORMAT_CODE_93);
            map.put("code128", FirebaseVisionBarcode.FORMAT_CODE_128);
            map.put("ean8", FirebaseVisionBarcode.FORMAT_EAN_8);
            map.put("ean13", FirebaseVisionBarcode.FORMAT_EAN_13);
            map.put("itf", FirebaseVisionBarcode.FORMAT_ITF);
            map.put("upca", FirebaseVisionBarcode.FORMAT_UPC_A);
            map.put("upce", FirebaseVisionBarcode.FORMAT_UPC_E);
            map.put("aztec", FirebaseVisionBarcode.FORMAT_AZTEC);
            map.put("datamatrix", FirebaseVisionBarcode.FORMAT_DATA_MATRIX);
            map.put("pdf417", FirebaseVisionBarcode.FORMAT_PDF417);
            map.put("qr", FirebaseVisionBarcode.FORMAT_QR_CODE);


            reqFormats = new ArrayList<>();

            for (String f :
                    formats) {
                if (map.get(f) != null) {
                    reqFormats.add(map.get(f));
                }
            }

            textureEntry = view.createSurfaceTexture();
//barcodeScanningProcessor.onSuccess();
//
            try {
                Size minPreviewSize;
                switch (resolutionPreset) {
                    case "high":
                        minPreviewSize = new Size(1024, 768);
                        break;
                    case "medium":
                        minPreviewSize = new Size(640, 480);
                        break;
                    case "low":
                        minPreviewSize = new Size(320, 240);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown preset: " + resolutionPreset);
                }
//
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraName);
                StreamConfigurationMap streamConfigurationMap =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                //noinspection ConstantConditions
                int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                //noinspection ConstantConditions
                isFrontFacing =
                        characteristics.get(CameraCharacteristics.LENS_FACING)
                                == CameraMetadata.LENS_FACING_FRONT;
                computeBestCaptureSize(streamConfigurationMap);
                computeBestPreviewAndRecordingSize(streamConfigurationMap, minPreviewSize, captureSize);

                if (cameraPermissionContinuation != null) {
                    result.error("cameraPermission", "Camera permission request ongoing", null);
                }

                Log.d("TestSimone", "cameraPermissionContinuation new Instance");
                cameraPermissionContinuation =
                        new Runnable() {
                            @Override
                            public void run() {
                                Log.d("TestSimone", "cameraPermissionContinuation Run");
                                cameraPermissionContinuation = null;
                                if (!hasCameraPermission()) {
                                    result.error(
                                            "cameraPermission", "MediaRecorderCamera permission not granted", null);
                                    return;
                                }
//                                if (!hasAudioPermission()) {
//                                    result.error(
//                                            "cameraPermission", "MediaRecorderAudio permission not granted", null);
//                                    return;
//                                }
                                open(result);
                            }
                        };
                requestingPermission = false;
                if (hasCameraPermission()) {
                    cameraPermissionContinuation.run();
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestingPermission = true;
                        registrar
                                .activity()
                                .requestPermissions(
                                        new String[]{Manifest.permission.CAMERA},
                                        CAMERA_REQUEST_ID);
                    }
                }
            } catch (CameraAccessException e) {
                result.error("CameraAccess", e.getMessage(), null);
            } catch (IllegalArgumentException e) {
                result.error("IllegalArgumentException", e.getMessage(), null);
            }
        }

        private void startCameraSource() {
            if (cameraSource != null) {
                try {
                    if (preview == null) {
                        Log.d(TAG, "resume: Preview is null");
                    } else {
                        preview.start(cameraSource);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Unable to start camera source.", e);
                    cameraSource.release();
                    cameraSource = null;
                }
            }
        }

        private void registerEventChannel() {
            new EventChannel(
                    registrar.messenger(), "fast_qr_reader_view/cameraEvents" + textureEntry.id())
                    .setStreamHandler(
                            new EventChannel.StreamHandler() {
                                @Override
                                public void onListen(Object arguments, EventChannel.EventSink eventSink) {
                                    QrReader.this.eventSink = eventSink;
                                }

                                @Override
                                public void onCancel(Object arguments) {
                                    QrReader.this.eventSink = null;
                                }
                            });
        }

        private boolean hasCameraPermission() {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || activity.checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        }

        private void computeBestPreviewAndRecordingSize(StreamConfigurationMap streamConfigurationMap, Size minPreviewSize, Size captureSize) {
            Size[] sizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
            float captureSizeRatio = (float) captureSize.getWidth() / captureSize.getHeight();
            List<Size> goodEnough = new ArrayList<>();
            for (Size s : sizes) {
                if ((float) s.getWidth() / s.getHeight() == captureSizeRatio
                        && minPreviewSize.getWidth() < s.getWidth()
                        && minPreviewSize.getHeight() < s.getHeight()) {
                    goodEnough.add(s);
                }
            }

            Collections.sort(goodEnough, new CompareSizesByArea());

            Size previewSize;
            Size videoSize;
            if (goodEnough.isEmpty()) {
                previewSize = sizes[0];
                videoSize = sizes[0];
            } else {
                previewSize = goodEnough.get(0);

                // Video capture size should not be greater than 1080 because MediaRecorder cannot handle higher resolutions.
                videoSize = goodEnough.get(0);
                for (int i = goodEnough.size() - 1; i >= 0; i--) {
                    if (goodEnough.get(i).getHeight() <= 1080) {
                        videoSize = goodEnough.get(i);
                        break;
                    }
                }
            }
        }

        private void computeBestCaptureSize(StreamConfigurationMap streamConfigurationMap) {
            // For still image captures, we use the largest available size.
            captureSize =
                    Collections.max(
                            Arrays.asList(streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888)),
                            new CompareSizesByArea());
        }

        @SuppressLint("MissingPermission")
        private void open(@Nullable final Result result) {
            if (!hasCameraPermission()) {
                if (result != null)
                    result.error("cameraPermission", "Camera permission not granted", null);
            } else {
//                try {
                cameraSource = new CameraSource(activity);
                cameraSource.setFacing(isFrontFacing ? 1 : 0);
                barcodeScanningProcessor = new BarcodeScanningProcessor(reqFormats);
                barcodeScanningProcessor.callback = new OnCodeScanned() {
                    @Override
                    public void onCodeScanned(FirebaseVisionBarcode barcode) {
                        if (camera.scanning) {
//                                            if (firebaseVisionBarcodes.size() > 0) {
                            Log.w(TAG, "onSuccess: " + barcode.getRawValue());
                            channel.invokeMethod("updateCode", barcode.getRawValue());
//                                                Map<String, String> event = new HashMap<>();
//                                                event.put("eventType", "cameraClosing");
//                                                camera.eventSink.success(event);
                            stopScanning();
//                                            }
                        }
                    }
                };
                cameraSource.setMachineLearningFrameProcessor(barcodeScanningProcessor);
                preview = new CameraSourcePreview(activity, null, textureEntry.surfaceTexture());

                startCameraSource();
                registerEventChannel();

                Map<String, Object> reply = new HashMap<>();
                reply.put("textureId", textureEntry.id());
                reply.put("previewWidth", cameraSource.getPreviewSize().getWidth());
                reply.put("previewHeight", cameraSource.getPreviewSize().getHeight());
                result.success(reply);
            }
        }

        private void sendErrorEvent(String errorDescription) {
            if (eventSink != null) {
                Map<String, String> event = new HashMap<>();
                event.put("eventType", "error");
                event.put("errorDescription", errorDescription);
                eventSink.success(event);
            }
        }

        private void close() {
            if (preview != null) {
                preview.stop();
            }

            if (cameraSource != null) {
                cameraSource.release();
            }

            camera = null;

        }

        private void dispose() {
            textureEntry.release();
            if (preview != null) {
                preview.stop();
            }

            if (cameraSource != null) {
                cameraSource.release();
            }
        }
    }

    private class CameraRequestPermissionsListener implements PluginRegistry.RequestPermissionsResultListener {
        @Override
        public boolean onRequestPermissionsResult(int id, String[] permissions, int[] grantResults) {
            if (id == CAMERA_REQUEST_ID) {
                Log.d("TestSimone", "Result permission");
                if (camera.cameraPermissionContinuation != null) {
                    Log.d("TestSimone", "cameraPermissionContinuation is not null");
                    camera.cameraPermissionContinuation.run();
                } else Log.d("TestSimone", "cameraPermissionContinuation is null");
                return true;
            }
            return false;
        }
    }
}

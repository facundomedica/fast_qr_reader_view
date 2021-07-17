package dev.facundo.fastqrreaderview;

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
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.barcode.Barcode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.facundo.fastqrreaderview.common.CameraSource;
import dev.facundo.fastqrreaderview.common.CameraSourcePreview;
import dev.facundo.fastqrreaderview.java.barcodescanning.BarcodeScanningProcessor;
import dev.facundo.fastqrreaderview.java.barcodescanning.OnCodeScanned;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.embedding.android.FlutterView;
import io.flutter.view.TextureRegistry;
//import io.flutter.view.FlutterView;
/**
 * FastQrReaderViewPlugin
 */
public class FastQrReaderViewPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {

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

//    private final FlutterView view;

    private TextureRegistry textureRegistry;
    private BinaryMessenger messenger;

    private Activity currentActivity;
//    private Registrar registrar;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;
    private boolean requestingPermission;
    private Result permissionResult;

    /**
     * Plugin registration.
     */
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {

        this.activityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (requestingPermission) {
                    requestingPermission = false;
                    return;
                }
                if (activity == FastQrReaderViewPlugin.this.currentActivity) {
                    if (camera != null) {
                        camera.startCameraSource();
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (activity == FastQrReaderViewPlugin.this.currentActivity) {
                    if (camera != null) {
                        if (camera.preview != null) {
                            camera.preview.stop();

                        }
                    }
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (activity == FastQrReaderViewPlugin.this.currentActivity) {
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
            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        };

        channel = new MethodChannel(binding.getBinaryMessenger(), "fast_qr_reader_view");
        this.textureRegistry = binding.getTextureRegistry();
        this.messenger = binding.getBinaryMessenger();
//        FastQrReaderViewPlugin plugin = new FastQrReaderViewPlugin();

        channel.setMethodCallHandler(this);
        cameraManager = (CameraManager) binding.getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
//        channel.setMethodCallHandler(new FastQrReaderViewPlugin(registrar, registrar.view(), activity));
    }

    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + currentActivity.getPackageName()));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        currentActivity.startActivity(intent);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            // for each permission check if the user granted/denied them
            // you may want to group the rationale in a single dialog,
            // this is just an example
            for (int i = 0, len = permissions.length; i < len; i++) {
                String permission = permissions[i];
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // user rejected the permission
                    boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(currentActivity, permission);
                    if (!showRationale) {
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
    public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
        switch (call.method) {
            case "init":
                if (camera != null) {
                    camera.close();
                }
                result.success(null);
                break;
            case "availableCameras":
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
                startScanning(result);
                break;
            case "stopScanning":
                stopScanning(result);
                break;
            case "checkPermission":
                String permission;
                if (ContextCompat.checkSelfPermission(currentActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    permission = "granted";
                } else {
                    permission = "denied";
                }
                result.success(permission);
                break;
            case "requestPermission":
                this.permissionResult = result;
                ActivityCompat.requestPermissions(currentActivity, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION);
                break;
            case "settings":
                openSettings();
            case "toggleFlash":
                toggleFlash(result);
                break;
            case "dispose": {
                if (camera != null) {
                    camera.dispose();
                }

                if (this.currentActivity != null && this.activityLifecycleCallbacks != null) {
                    this.currentActivity
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



    //    public static void registerWith(Registrar registrar) {
//        channel = new MethodChannel(registrar.messenger(), "fast_qr_reader_view");
//        cameraManager = (CameraManager) registrar.activity().getSystemService(Context.CAMERA_SERVICE);
//        channel.setMethodCallHandler(new FastQrReaderViewPlugin(registrar, registrar.view(), registrar.activity()));
//
//        FastQrReaderViewPlugin plugin = new FastQrReaderViewPlugin(registrar, registrar.view(), registrar.activity());
//        channel.setMethodCallHandler(plugin);
//        registrar.addRequestPermissionsResultListener(plugin);
//    }


    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        currentActivity = binding.getActivity();
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        currentActivity = binding.getActivity();
        binding.addRequestPermissionsResultListener(this);
    }

    @Override
    public void onDetachedFromActivity() {
        currentActivity = null;
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
        private final TextureRegistry.SurfaceTextureEntry textureEntry;
        private EventChannel.EventSink eventSink;
        private boolean isFrontFacing;
        private boolean scanning;
        private Size captureSize;
        private Runnable cameraPermissionContinuation;

        QrReader(final String cameraName, final String resolutionPreset, final ArrayList<String> formats, @NonNull final Result result) {
            // AVAILABLE FORMATS:
            // enum CodeFormat { codabar, code39, code93, code128, ean8, ean13, itf, upca, upce, aztec, datamatrix, pdf417, qr }

            Map<String, Integer> map = new HashMap<>();
            map.put("codabar", Barcode.FORMAT_CODABAR);
            map.put("code39", Barcode.FORMAT_CODE_39);
            map.put("code93", Barcode.FORMAT_CODE_93);
            map.put("code128", Barcode.FORMAT_CODE_128);
            map.put("ean8", Barcode.FORMAT_EAN_8);
            map.put("ean13", Barcode.FORMAT_EAN_13);
            map.put("itf", Barcode.FORMAT_ITF);
            map.put("upca", Barcode.FORMAT_UPC_A);
            map.put("upce", Barcode.FORMAT_UPC_E);
            map.put("aztec", Barcode.FORMAT_AZTEC);
            map.put("datamatrix", Barcode.FORMAT_DATA_MATRIX);
            map.put("pdf417", Barcode.FORMAT_PDF417);
            map.put("qr", Barcode.FORMAT_QR_CODE);


            reqFormats = new ArrayList<>();

            for (String f :
                    formats) {
                if (map.get(f) != null) {
                    reqFormats.add(map.get(f));
                }
            }

            textureEntry =  textureRegistry.createSurfaceTexture();
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

                cameraPermissionContinuation =
                        new Runnable() {
                            @Override
                            public void run() {
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
                        currentActivity
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
                    messenger, "fast_qr_reader_view/cameraEvents" + textureEntry.id())
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
                    || currentActivity.checkSelfPermission(Manifest.permission.CAMERA)
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
                cameraSource = new CameraSource(currentActivity);
                cameraSource.setFacing(isFrontFacing ? CameraSource.CAMERA_FACING_FRONT : CameraSource.CAMERA_FACING_BACK);
                barcodeScanningProcessor = new BarcodeScanningProcessor(reqFormats);
                barcodeScanningProcessor.callback = new OnCodeScanned() {
                    @Override
                    public void onCodeScanned(Barcode barcode) {
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
                preview = new CameraSourcePreview(currentActivity, null, textureEntry.surfaceTexture());

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
}
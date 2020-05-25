package co.apperto.fastqrreaderview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.apperto.fastqrreaderview.common.CameraSource;
import co.apperto.fastqrreaderview.common.CameraSourcePreview;
import co.apperto.fastqrreaderview.java.barcodescanning.BarcodeScanningProcessor;
import co.apperto.fastqrreaderview.java.barcodescanning.OnCodeScanned;
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
import io.flutter.view.TextureRegistry;
import io.flutter.view.TextureRegistry.SurfaceTextureEntry;

/**
 * FastQrReaderViewPlugin
 */
public class FastQrReaderViewPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {

    private static final int CAMERA_REQUEST_ID = 513469796;
    private static final int REQUEST_PERMISSION = 47;
    private static final String TAG = "FastQrReaderViewPlugin";

    private static CameraManager cameraManager;
    private QrReader camera;
    private Activity activity;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;
    // The code to run after requesting camera permissions.
    private Runnable cameraPermissionContinuation;
    private boolean requestingPermission;
    private static MethodChannel channel;
    private Result permissionResult;
    private BinaryMessenger binaryMessenger;
    private TextureRegistry textureRegistry;

    // Whether we should ignore process(). This is usually caused by feeding input data faster than
    // the model can handle.

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        // plugin is now attached to a Flutter experience.
        register(binding.getBinaryMessenger(), this);
        textureRegistry = binding.getTextureRegistry();
        Log.i(TAG, "Plugin binding with V2 interface");
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        // plugin is no longer attached to a Flutter experience.
    }

    @Override
    public void onAttachedToActivity(ActivityPluginBinding activityPluginBinding) {
        // plugin is now attached to an Activity
        Log.d(TAG, "onAttachedToActivity");
        activity = activityPluginBinding.getActivity();
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        activityPluginBinding.addRequestPermissionsResultListener(new CameraRequestPermissionsListener());
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        Log.d(TAG, "onDetachedFromActivityForConfigChanges");
        // the Activity your plugin was attached to was
        // destroyed to change configuration.
        // This call will be followed by onReattachedToActivityForConfigChanges().
        if (camera != null) {
            if (camera.preview != null) {
                camera.preview.stop();
            }
        }
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {
        Log.d(TAG, "onReattachedToActivityForConfigChanges");
        // your plugin is now attached to a new Activity
        activity = activityPluginBinding.getActivity();
        // after a configuration change.
        if (camera != null) {
            camera.startCameraSource();
        }
    }

    @Override
    public void onDetachedFromActivity() {
        Log.d(TAG, "onDetachedFromActivity");
        // your plugin is no longer associated with an Activity.
        // Clean up references.
        if (camera != null) {
            if (camera.preview != null) {
                camera.preview.stop();
            }
            if (camera.cameraSource != null) {
                camera.cameraSource.release();
            }
        }
        activity = null;
    }

    /**
     * Old v1 style plugin registration.
     */
    @SuppressWarnings("unused")
    public static void registerWith(Registrar registrar) {
        Log.i(TAG, "Plugin binding with V1 interface");
        FastQrReaderViewPlugin pluginInstance = new FastQrReaderViewPlugin(registrar);
        register(registrar.messenger(), pluginInstance);
    }

    private static void register(BinaryMessenger messenger, FastQrReaderViewPlugin pluginInstance) {
        pluginInstance.binaryMessenger = messenger;
        channel = new MethodChannel(messenger, "fast_qr_reader_view");
        channel.setMethodCallHandler(pluginInstance);
        channel.setMethodCallHandler(pluginInstance);
    }

    // Default constructor for V2 plugin
    public FastQrReaderViewPlugin() {
        //
    }

    // Legacy constructor for V1 plugin
    private FastQrReaderViewPlugin(Registrar registrar) {
        activity = registrar.activity();
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        textureRegistry = registrar.textures();

        registrar.addRequestPermissionsResultListener(new CameraRequestPermissionsListener());

        this.activityLifecycleCallbacks =
                new Application.ActivityLifecycleCallbacks() {
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
                        if (activity == FastQrReaderViewPlugin.this.activity) {
                            if (camera != null) {
                                camera.startCameraSource();
                            }
                        }
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
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
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {

                    }
                };
    }

    /*
     * Open Settings screens
     */
    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
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
                    boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
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
    public void onMethodCall(MethodCall call, @NonNull final Result result) {
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
                ArrayList<String> codeFormats = call.argument("codeFormats");

                if (camera != null) {
                    camera.close();
                }
                camera = new QrReader(cameraName, codeFormats, result);
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
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    permission = "granted";
                } else {
                    permission = "denied";
                }
                result.success(permission);
                break;
            case "requestPermission":
                this.permissionResult = result;
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION);
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

    private class CameraRequestPermissionsListener
            implements PluginRegistry.RequestPermissionsResultListener {
        @Override
        public boolean onRequestPermissionsResult(int id, String[] permissions, int[] grantResults) {
            if (id == CAMERA_REQUEST_ID) {
                cameraPermissionContinuation.run();
                return true;
            }
            return false;
        }
    }


    private void startScanning(@NonNull Result result) {
        camera.scanning = true;
        camera.barcodeScanningProcessor.shouldThrottle.set(false);
        result.success(null);
    }

    private void stopScanning(@NonNull Result result) {
        stopScanning();
        result.success(null);
    }

    private void stopScanning() {
        camera.scanning = false;
        camera.barcodeScanningProcessor.shouldThrottle.set(true);
    }

    private void toggleFlash(@NonNull Result result) {
        toggleFlash();
        result.success(null);
    }

    private void toggleFlash() {
        camera.cameraSource.toggleFlash();
    }

    private class QrReader {
        private CameraSource cameraSource = null;
        private CameraSourcePreview preview;

        private final SurfaceTextureEntry textureEntry;

        BarcodeScanningProcessor barcodeScanningProcessor;

        ArrayList<Integer> reqFormats;

        private boolean isFrontFacing;

        private boolean scanning;

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

        //
        QrReader(final String cameraName, final ArrayList<String> formats, @NonNull final Result result) {

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

            textureEntry = textureRegistry.createSurfaceTexture();
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraName);
                isFrontFacing =
                        characteristics.get(CameraCharacteristics.LENS_FACING)
                                == CameraMetadata.LENS_FACING_FRONT;

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
                                open(result);
                            }
                        };
                requestingPermission = false;
                if (hasCameraPermission()) {
                    cameraPermissionContinuation.run();
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestingPermission = true;
                        activity.requestPermissions(
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

        //
        private void registerEventChannel() {
            new EventChannel(
                    binaryMessenger, "fast_qr_reader_view/cameraEvents" + textureEntry.id())
                    .setStreamHandler(
                            new EventChannel.StreamHandler() {
                                @Override
                                public void onListen(Object arguments, EventChannel.EventSink eventSink) {
                                }

                                @Override
                                public void onCancel(Object arguments) {
                                }
                            });
        }

        //
        private boolean hasCameraPermission() {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || activity.checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        }

        @SuppressLint("MissingPermission")
        private void open(@Nullable final Result result) {
            if (!hasCameraPermission()) {
                if (result != null)
                    result.error("cameraPermission", "Camera permission not granted", null);
            } else {
                cameraSource = new CameraSource(activity);
                cameraSource.setFacing(isFrontFacing ? 1 : 0);
                barcodeScanningProcessor = new BarcodeScanningProcessor(reqFormats);
                barcodeScanningProcessor.callback = new OnCodeScanned() {
                    @Override
                    public void onCodeScanned(FirebaseVisionBarcode barcode) {
                        if (camera.scanning) {
                            Log.d(TAG, "onSuccess: " + barcode.getRawValue());
                            channel.invokeMethod("updateCode", barcode.getRawValue());
                            stopScanning();
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

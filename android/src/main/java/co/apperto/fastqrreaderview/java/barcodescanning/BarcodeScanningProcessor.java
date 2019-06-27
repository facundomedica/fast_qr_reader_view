// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package co.apperto.fastqrreaderview.java.barcodescanning;

import androidx.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.util.ArrayUtils;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.apperto.fastqrreaderview.common.FrameMetadata;
import co.apperto.fastqrreaderview.java.VisionProcessorBase;

import static com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode.FORMAT_CODE_128;
import static com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode.FORMAT_EAN_13;
import static com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode.FORMAT_EAN_8;
import static com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode.FORMAT_QR_CODE;
import static com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode.FORMAT_UPC_A;
import static com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode.FORMAT_UPC_E;

/**
 * Barcode Detector Demo.
 */
public class BarcodeScanningProcessor extends VisionProcessorBase<List<FirebaseVisionBarcode>> {

    private static final String TAG = "BarcodeScanProc";

    private final FirebaseVisionBarcodeDetector detector;

    private static final Map<Integer, String> formatMap;
    static {
        Map<Integer, String> map = new HashMap<>();
        map.put(FORMAT_QR_CODE, "qrcode");
        map.put(FORMAT_CODE_128, "code128");
        map.put(FORMAT_EAN_13, "ean13");
        map.put(FORMAT_EAN_8, "ean8");
        map.put(FORMAT_UPC_A, "upca");
        map.put(FORMAT_UPC_E, "upce");
        formatMap = Collections.unmodifiableMap(map);
    }

    public OnCodeScanned callback;

    public BarcodeScanningProcessor(ArrayList<Integer> reqFormats) {
        // Note that if you know which format of barcode your app is dealing with, detection will be
        // faster to specify the supported barcode formats one by one, e.g.
        // new FirebaseVisionBarcodeDetectorOptions.Builder()
        //     .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_QR_CODE)
        //     .build();
        int[] additionalFormats = Arrays.copyOfRange(ArrayUtils.toPrimitiveArray(reqFormats), 1, reqFormats.size());


        detector = FirebaseVision.getInstance().getVisionBarcodeDetector(new FirebaseVisionBarcodeDetectorOptions.Builder()
                // setBarcodeFormats is quite weird. I have to do all of these just to pass a bunch of ints
                .setBarcodeFormats(ArrayUtils.toPrimitiveArray(reqFormats)[0], additionalFormats)
                .build());
    }

    @Override
    public void stop() {
        try {
            detector.close();
        } catch (IOException e) {
            Log.e(TAG, "Exception thrown while trying to close Barcode Detector: " + e);
        }
    }


    @Override
    protected Task<List<FirebaseVisionBarcode>> detectInImage(FirebaseVisionImage image) {
        return detector.detectInImage(image);
    }

    @Override
    protected void onSuccess(
            @NonNull List<FirebaseVisionBarcode> barcodes,
            @NonNull FrameMetadata frameMetadata) {

        List<Map<String,Object>> barcodeList = new ArrayList<>();
        for (FirebaseVisionBarcode barcode : barcodes) {
            Map<String, Object> barcodeObject = new HashMap<>();
            barcodeObject.put("rawValue", barcode.getRawValue());
            barcodeObject.put("type", formatMap.get(barcode.getFormat()));

            barcodeList.add(barcodeObject);
        }
        callback.onCodeScanned(barcodeList);
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Barcode detection failed " + e);
    }
}


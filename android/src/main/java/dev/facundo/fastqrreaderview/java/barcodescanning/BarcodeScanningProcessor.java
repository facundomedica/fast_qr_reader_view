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
package dev.facundo.fastqrreaderview.java.barcodescanning;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.common.util.ArrayUtils;
import com.google.android.gms.tasks.Task;
import com.google.barhopper.deeplearning.BarcodeDetectorClientOptions;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.facundo.fastqrreaderview.common.FrameMetadata;
import dev.facundo.fastqrreaderview.java.VisionProcessorBase;

/**
 * Barcode Detector Demo.
 */
public class BarcodeScanningProcessor extends VisionProcessorBase<List<Barcode>> {

    private static final String TAG = "BarcodeScanProc";

    private final BarcodeScanner scanner;

    public OnCodeScanned callback;

    public BarcodeScanningProcessor(ArrayList<Integer> reqFormats) {
        // Note that if you know which format of barcode your app is dealing with, detection will be
        // faster to specify the supported barcode formats one by one, e.g.
        // new FirebaseVisionBarcodeDetectorOptions.Builder()
        //     .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_QR_CODE)
        //     .build();
        int[] additionalFormats = Arrays.copyOfRange(ArrayUtils.toPrimitiveArray(reqFormats), 1, reqFormats.size());

        BarcodeScannerOptions options =
                new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_PDF417)
//                        .setBarcodeFormats(
//                                ArrayUtils.toPrimitiveArray(reqFormats)[0], additionalFormats)
                        .build();

        scanner = BarcodeScanning.getClient(options);
//        detector = FirebaseVision.getInstance().getVisionBarcodeDetector(new FirebaseVisionBarcodeDetectorOptions.Builder()
//                // setBarcodeFormats is quite weird. I have to do all of these just to pass a bunch of ints
//                .setBarcodeFormats(ArrayUtils.toPrimitiveArray(reqFormats)[0], additionalFormats)
//                .build());
    }

    @Override
    public void stop() {
        try {
            scanner.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception thrown while trying to close Barcode Detector: " + e);
        }
    }


    @Override
    protected Task<List<Barcode>> detectInImage(InputImage image) {
//        ByteBuffer buf = image.getByteBuffer();
//        Log.d("Asd", Integer.toString( buf.remaining()));
//        byte[] arr = new byte[buf.remaining()];
//        buf.get(arr);
//        InputImage iii = InputImage.fromByteArray(arr,
//                image.getWidth(),
//                image.getHeight(),
//                image.getRotationDegrees(),
//                image.getFormat());

        return scanner.process(image);
    }

    @Override
    protected void onSuccess(
            @NonNull List<Barcode> barcodes,
            @NonNull FrameMetadata frameMetadata) { //,
//      @NonNull GraphicOverlay graphicOverlay) {
//    graphicOverlay.clear();

        for (int i = 0; i < barcodes.size(); ++i) {
            Barcode barcode = barcodes.get(i);
            Log.d("BARCODE!", barcode.getRawValue());
            callback.onCodeScanned(barcode);
//      BarcodeGraphic barcodeGraphic = new BarcodeGraphic(graphicOverlay, barcode);
//      graphicOverlay.add(barcodeGraphic);
        }
    }

    @Override
    protected void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Barcode detection failed " + e);
    }
}


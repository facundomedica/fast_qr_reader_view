package co.apperto.fastqrreaderview.java.barcodescanning;

import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;

public interface OnCodeScanned {
    void onCodeScanned(FirebaseVisionBarcode barcode);
}

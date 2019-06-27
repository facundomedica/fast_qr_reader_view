package co.apperto.fastqrreaderview.java.barcodescanning;

import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;

import java.util.List;
import java.util.Map;

public interface OnCodeScanned {
    void onCodeScanned(List<Map<String, Object>> barcoes);
}

package dev.facundo.fastqrreaderview.java.barcodescanning;

import com.google.mlkit.vision.barcode.Barcode;

public interface OnCodeScanned {
    void onCodeScanned(Barcode barcode);
}

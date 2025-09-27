package com.example.invenzora;

import android.app.Activity;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Helper class to easily integrate ML Kit scanner into existing activities
 */
public class MLKitIntegrationHelper {

    public interface ScanResultCallback {
        void onScanSuccess(String barcode);
        void onScanCancelled();
        void onScanError(String error);
    }

    private ActivityResultLauncher<Intent> scannerLauncher;
    private ScanResultCallback callback;

    /**
     * Initialize the ML Kit scanner integration
     * Call this in your activity's onCreate() method
     */
    public void initialize(AppCompatActivity activity, ScanResultCallback callback) {
        this.callback = callback;

        scannerLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        String scannedBarcode = MLKitScannerActivity.getScanResult(data);

                        if (scannedBarcode != null && !scannedBarcode.trim().isEmpty()) {
                            callback.onScanSuccess(scannedBarcode);
                        } else {
                            callback.onScanError("No barcode data received");
                        }
                    } else {
                        callback.onScanCancelled();
                    }
                }
        );
    }

    /**
     * Launch the ML Kit barcode scanner
     */
    public void startScan(Activity activity) {
        Intent intent = new Intent(activity, MLKitScannerActivity.class);
        scannerLauncher.launch(intent);
    }

    /**
     * Static method for simple one-time use
     */
    public static void scanBarcode(AppCompatActivity activity, ScanResultCallback callback) {
        MLKitIntegrationHelper helper = new MLKitIntegrationHelper();
        helper.initialize(activity, callback);
        helper.startScan(activity);
    }
}
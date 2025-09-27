package com.example.invenzora;

import android.app.Activity;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Simplified barcode integration helper
 * Replace the complex MLKitIntegrationHelper with this simpler version
 */
public class BarcodeHelper {

    public static final int BARCODE_SCAN_REQUEST = 1001;

    public interface ScanResultCallback {
        void onScanSuccess(String barcode);
        void onScanCancelled();
        void onScanError(String error);
    }

    /**
     * Start barcode scanning
     * Use this in your activities instead of the ML Kit version
     */
    public static void startScan(AppCompatActivity activity) {
        Intent intent = new Intent(activity, SimpleBarcodeScanner.class);
        activity.startActivityForResult(intent, BARCODE_SCAN_REQUEST);
    }

    /**
     * Handle scan result in your activity's onActivityResult
     * Call this from onActivityResult with the callback
     */
    public static void handleScanResult(int requestCode, int resultCode, Intent data,
                                        ScanResultCallback callback) {
        if (requestCode == BARCODE_SCAN_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                String barcode = SimpleBarcodeScanner.getScanResult(data);
                if (barcode != null && !barcode.trim().isEmpty()) {
                    callback.onScanSuccess(barcode);
                } else {
                    callback.onScanError("No barcode data received");
                }
            } else {
                callback.onScanCancelled();
            }
        }
    }

    /**
     * Alternative method using the ZXing IntentIntegrator (even simpler)
     * Add this if you want to use the standard ZXing scan intent
     */
    public static void startSimpleScan(AppCompatActivity activity) {
        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.ALL_CODE_TYPES);
        options.setPrompt("Scan a barcode");
        options.setCameraId(0);  // Use back camera
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(true);
        options.setOrientationLocked(false);

        com.journeyapps.barcodescanner.ScanContract scanContract = new com.journeyapps.barcodescanner.ScanContract();
        // Note: This requires using registerForActivityResult instead of startActivityForResult
    }

    /**
     * Check if device can scan barcodes
     */
    public static boolean canScan(Activity activity) {
        return activity.getPackageManager().hasSystemFeature("android.hardware.camera");
    }

    /**
     * Show user-friendly error messages
     */
    public static String getUserFriendlyError(String error) {
        if (error.contains("permission")) {
            return "Camera permission is required for barcode scanning";
        } else if (error.contains("camera")) {
            return "Cannot access camera. Please try again.";
        } else {
            return "Barcode scanning failed: " + error;
        }
    }
}
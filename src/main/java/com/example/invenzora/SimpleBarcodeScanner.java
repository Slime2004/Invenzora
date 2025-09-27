package com.example.invenzora;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.google.zxing.ResultPoint;

import java.util.List;

/**
 * Simple barcode scanner using ZXing library
 * More reliable than ML Kit for basic scanning needs
 */
public class SimpleBarcodeScanner extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private DecoratedBarcodeView barcodeView;
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_barcode_scanner);

        barcodeView = findViewById(R.id.barcode_scanner);

        // Check camera permission
        if (checkCameraPermission()) {
            initializeScanner();
        } else {
            requestCameraPermission();
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeScanner();
            } else {
                Toast.makeText(this, "Camera permission required for scanning",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeScanner() {
        // Set up barcode callback
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (!isScanning) {
                    return; // Prevent multiple scans
                }

                isScanning = false;
                handleScanResult(result.getText());
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
                // Optional: Handle result points for UI feedback
            }
        });

        isScanning = true;
    }

    private void handleScanResult(String barcode) {
        // Return result to calling activity
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SCAN_RESULT", barcode);
        setResult(RESULT_OK, resultIntent);

        // Show feedback to user
        Toast.makeText(this, "Scanned: " + barcode, Toast.LENGTH_SHORT).show();

        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeView != null) {
            barcodeView.resume();
            isScanning = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeView != null) {
            barcodeView.pause();
            isScanning = false;
        }
    }

    // Utility method for other activities to use
    public static void startScan(AppCompatActivity activity, int requestCode) {
        Intent intent = new Intent(activity, SimpleBarcodeScanner.class);
        activity.startActivityForResult(intent, requestCode);
    }

    // Get scan result from intent
    public static String getScanResult(Intent data) {
        if (data != null) {
            return data.getStringExtra("SCAN_RESULT");
        }
        return null;
    }
}
package com.example.invenzora;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MLKitScannerActivity extends AppCompatActivity {
    private static final String TAG = "MLKitScanner";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String EXTRA_SCAN_RESULT = "SCAN_RESULT";

    private PreviewView previewView;
    private TextView instructionText;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private boolean isScanning = true;
    private Camera camera;
    private boolean isFlashOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mlkit_scanner);

        previewView = findViewById(R.id.preview_view);
        instructionText = findViewById(R.id.tv_instruction);

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Configure barcode scanner for optimal performance
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_CODE_128,    // Most common
                        Barcode.FORMAT_CODE_39,     // Widely supported
                        Barcode.FORMAT_UPC_A,       // Retail standard
                        Barcode.FORMAT_UPC_E,       // Retail standard
                        Barcode.FORMAT_EAN_13,      // International standard
                        Barcode.FORMAT_EAN_8,       // International standard
                        Barcode.FORMAT_QR_CODE,     // QR codes
                        Barcode.FORMAT_DATA_MATRIX  // Data Matrix
                )
                .build();

        barcodeScanner = BarcodeScanning.getClient(options);

        // Setup click listeners
        setupClickListeners();

        // Check camera permission
        if (hasCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        findViewById(R.id.btn_flashlight).setOnClickListener(v -> toggleFlashlight());
    }

    private void toggleFlashlight() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isFlashOn = !isFlashOn;
            camera.getCameraControl().enableTorch(isFlashOn);

            TextView flashButton = findViewById(R.id.btn_flashlight);
            flashButton.setText(isFlashOn ? "🔦 Flash" : "💡 Flash");
        } else {
            Toast.makeText(this, "Flash not available", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required for scanning", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, "Error starting camera: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        // Preview use case
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image analysis use case for barcode detection
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new BarcodeAnalyzer());

        // Camera selector (back camera)
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll();

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis);

            // Enable tap to focus
            setupTapToFocus(camera);

            // Show flash button only if available
            if (camera.getCameraInfo().hasFlashUnit()) {
                findViewById(R.id.btn_flashlight).setVisibility(View.VISIBLE);
            } else {
                findViewById(R.id.btn_flashlight).setVisibility(View.GONE);
            }

        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
            Toast.makeText(this, "Camera binding failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupTapToFocus(Camera camera) {
        previewView.setOnTouchListener((v, event) -> {
            if (camera.getCameraInfo().isFocusMeteringSupported(
                    new FocusMeteringAction.Builder(
                            previewView.getMeteringPointFactory()
                                    .createPoint(event.getX(), event.getY())
                    ).build())) {

                FocusMeteringAction action = new FocusMeteringAction.Builder(
                        previewView.getMeteringPointFactory()
                                .createPoint(event.getX(), event.getY())
                ).build();

                camera.getCameraControl().startFocusAndMetering(action);

                // Show focus indicator
                runOnUiThread(() -> {
                    instructionText.setText("🔍 Focusing... Hold steady");
                    instructionText.postDelayed(() -> {
                        if (isScanning) {
                            instructionText.setText("🎯 Point camera at barcode");
                        }
                    }, 1500);
                });
            }
            return true;
        });
    }

    private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            if (!isScanning) {
                imageProxy.close();
                return;
            }

            @SuppressWarnings("UnsafeOptInUsageError")
            android.media.Image mediaImage = imageProxy.getImage();

            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(
                        mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                barcodeScanner.process(image)
                        .addOnSuccessListener(barcodes -> {
                            for (Barcode barcode : barcodes) {
                                handleBarcodeDetected(barcode);
                                break; // Process only the first barcode found
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Barcode detection failed", e);
                            runOnUiThread(() -> {
                                instructionText.setText("⚠️ Detection error - try again");
                            });
                        })
                        .addOnCompleteListener(task -> imageProxy.close());
            } else {
                imageProxy.close();
            }
        }
    }

    private void handleBarcodeDetected(Barcode barcode) {
        if (!isScanning) return;

        isScanning = false; // Prevent multiple detections

        String barcodeValue = barcode.getRawValue();
        int format = barcode.getFormat();

        if (barcodeValue != null && !barcodeValue.trim().isEmpty()) {
            runOnUiThread(() -> {
                instructionText.setText("✅ Barcode detected: " + barcodeValue);

                // Provide haptic feedback
                previewView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                // Disable flashlight
                if (isFlashOn && camera != null) {
                    camera.getCameraControl().enableTorch(false);
                }

                // Return result after short delay
                previewView.postDelayed(() -> returnScanResult(barcodeValue, format), 800);
            });
        } else {
            isScanning = true; // Continue scanning if no valid data
            runOnUiThread(() -> {
                instructionText.setText("⚠️ Invalid barcode - try again");
            });
        }
    }

    private void returnScanResult(String barcodeValue, int format) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SCAN_RESULT, barcodeValue);
        resultIntent.putExtra("SCAN_RESULT_FORMAT", getBarcodeFormatName(format));

        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private String getBarcodeFormatName(int format) {
        switch (format) {
            case Barcode.FORMAT_CODE_128: return "CODE_128";
            case Barcode.FORMAT_CODE_39: return "CODE_39";
            case Barcode.FORMAT_UPC_A: return "UPC_A";
            case Barcode.FORMAT_UPC_E: return "UPC_E";
            case Barcode.FORMAT_EAN_13: return "EAN_13";
            case Barcode.FORMAT_EAN_8: return "EAN_8";
            case Barcode.FORMAT_QR_CODE: return "QR_CODE";
            case Barcode.FORMAT_DATA_MATRIX: return "DATA_MATRIX";
            default: return "UNKNOWN";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }

    @Override
    public void onBackPressed() {
        // Return cancelled result
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    // Public method to get scan result from intent
    public static String getScanResult(Intent data) {
        if (data != null) {
            return data.getStringExtra(EXTRA_SCAN_RESULT);
        }
        return null;
    }
}
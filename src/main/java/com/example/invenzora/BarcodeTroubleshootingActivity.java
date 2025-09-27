package com.example.invenzora;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.MultiFormatWriter;
import android.util.Log;

public class BarcodeTroubleshootingActivity extends AppCompatActivity {
    private TextView statusTextView;
    private ImageView testBarcodeImageView;
    private Button generateTestBtn, scanTestBtn, diagnosticsBtn;
    private Bitmap currentTestBarcode;
    private String testBarcodeData = "123456789012";

    // Barcode scanner launcher
    private androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    showResult("❌ Scan cancelled or failed", false);
                } else {
                    String scannedData = result.getContents();
                    if (scannedData.equals(testBarcodeData)) {
                        showResult("✅ SUCCESS! Barcode scanned correctly: " + scannedData, true);
                    } else {
                        showResult("⚠️ Scanned different data: " + scannedData +
                                "\nExpected: " + testBarcodeData, false);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_troubleshooting);

        initializeViews();
        runInitialDiagnostics();
        generateTestBarcode();
    }

    private void initializeViews() {
        statusTextView = findViewById(R.id.tv_status);
        testBarcodeImageView = findViewById(R.id.iv_test_barcode);
        generateTestBtn = findViewById(R.id.btn_generate_test);
        scanTestBtn = findViewById(R.id.btn_scan_test);
        diagnosticsBtn = findViewById(R.id.btn_run_diagnostics);

        generateTestBtn.setOnClickListener(v -> generateTestBarcode());
        scanTestBtn.setOnClickListener(v -> scanTestBarcode());
        diagnosticsBtn.setOnClickListener(v -> runDetailedDiagnostics());
    }

    private void runInitialDiagnostics() {
        StringBuilder status = new StringBuilder();
        status.append("🔍 BARCODE SCANNING DIAGNOSTICS\n\n");

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            status.append("✅ Camera permission: GRANTED\n");
        } else {
            status.append("❌ Camera permission: NOT GRANTED\n");
            status.append("   Please grant camera permission in settings\n");
        }

        // Check if camera hardware is available
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            status.append("✅ Camera hardware: Available\n");
        } else {
            status.append("❌ Camera hardware: Not available\n");
        }

        // Check for autofocus
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            status.append("✅ Autofocus: Available\n");
        } else {
            status.append("⚠️ Autofocus: Not available\n");
        }

        // Check for flash
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            status.append("✅ Flash: Available\n");
        } else {
            status.append("⚠️ Flash: Not available\n");
        }

        status.append("\n📱 DEVICE INFO:\n");
        status.append("Model: ").append(android.os.Build.MODEL).append("\n");
        status.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        status.append("API Level: ").append(android.os.Build.VERSION.SDK_INT).append("\n");

        statusTextView.setText(status.toString());
    }

    private void generateTestBarcode() {
        try {
            // Generate multiple test barcodes using ZXing for accuracy
            currentTestBarcode = generateRealTestBarcode(testBarcodeData,
                    getResources().getDisplayMetrics().widthPixels - 80);

            if (currentTestBarcode != null) {
                testBarcodeImageView.setImageBitmap(currentTestBarcode);
                showResult("✅ Generated REAL test barcode: " + testBarcodeData +
                        "\n📱 Format: Code 128 (compatible with your scanner)" +
                        "\n🎯 Try scanning this barcode with the button below", true);
            } else {
                showResult("❌ Failed to generate test barcode", false);
            }
        } catch (Exception e) {
            showResult("❌ Error generating barcode: " + e.getMessage(), false);
        }
    }

    private void scanTestBarcode() {
        if (currentTestBarcode == null) {
            showResult("❌ No test barcode to scan. Generate one first.", false);
            return;
        }

        try {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan the test barcode shown above");
            options.setBeepEnabled(true);
            options.setOrientationLocked(false);

            // Enable various barcode formats
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);

            barcodeLauncher.launch(options);

        } catch (Exception e) {
            showResult("❌ Error launching scanner: " + e.getMessage(), false);
        }
    }

    private void runDetailedDiagnostics() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Detailed Diagnostics");

        StringBuilder diagnostics = new StringBuilder();

        // Check permissions in detail
        diagnostics.append("🔐 PERMISSIONS:\n");
        String[] permissions = {
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
        };

        for (String permission : permissions) {
            boolean granted = ContextCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_GRANTED;
            diagnostics.append(granted ? "✅ " : "❌ ")
                    .append(permission.substring(permission.lastIndexOf('.') + 1))
                    .append("\n");
        }

        // Hardware features
        diagnostics.append("\n🔧 HARDWARE FEATURES:\n");
        String[] features = {
                PackageManager.FEATURE_CAMERA_ANY,
                PackageManager.FEATURE_CAMERA_AUTOFOCUS,
                PackageManager.FEATURE_CAMERA_FLASH,
                PackageManager.FEATURE_CAMERA_FRONT
        };

        for (String feature : features) {
            boolean available = getPackageManager().hasSystemFeature(feature);
            diagnostics.append(available ? "✅ " : "❌ ")
                    .append(feature.substring(feature.lastIndexOf('.') + 1))
                    .append("\n");
        }

        // Screen info
        diagnostics.append("\n📱 SCREEN INFO:\n");
        diagnostics.append("Density: ").append(getResources().getDisplayMetrics().density).append("\n");
        diagnostics.append("DPI: ").append(getResources().getDisplayMetrics().densityDpi).append("\n");
        diagnostics.append("Size: ").append(getResources().getDisplayMetrics().widthPixels)
                .append("x").append(getResources().getDisplayMetrics().heightPixels).append("\n");

        // Scanning tips
        diagnostics.append("\n💡 SCANNING TIPS:\n");
        diagnostics.append("• Hold device 6-8 inches from barcode\n");
        diagnostics.append("• Ensure good lighting\n");
        diagnostics.append("• Keep device steady\n");
        diagnostics.append("• Make sure barcode is flat\n");
        diagnostics.append("• Try landscape orientation\n");
        diagnostics.append("• Clean camera lens\n");
        diagnostics.append("• Use ML Kit scanner for better results\n");

        builder.setMessage(diagnostics.toString());
        builder.setPositiveButton("Test Different Formats", (dialog, which) -> showFormatTests());
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void showFormatTests() {
        String[] formats = {
                "Simple Numeric (Recommended)",
                "Code 39 (Alphanumeric)",
                "Test Barcode (12345678)",
                "Your Actual Barcode"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Test Different Barcode Formats");
        builder.setItems(formats, (dialog, which) -> {
            String testData;
            switch (which) {
                case 0:
                    testData = "123456789012";
                    break;
                case 1:
                    testData = "TEST123";
                    break;
                case 2:
                    testData = "12345678";
                    break;
                case 3:
                    showCustomBarcodeTest();
                    return;
                default:
                    testData = "123456789012";
            }

            generateAndTestFormat(testData, which);
        });
        builder.show();
    }

    private void generateAndTestFormat(String data, int formatType) {
        try {
            com.google.zxing.MultiFormatWriter multiFormatWriter = new com.google.zxing.MultiFormatWriter();
            com.google.zxing.common.BitMatrix bitMatrix = null;
            String formatName = "";
            int width = 600;
            int height = 200;

            switch (formatType) {
                case 0: // Simple Numeric
                    // Use Code 128 for numeric data - most reliable
                    bitMatrix = multiFormatWriter.encode(data, com.google.zxing.BarcodeFormat.CODE_128, width, height);
                    formatName = "Code 128 (Numeric)";
                    break;

                case 1: // Code 39 (Alphanumeric)
                    // Code 39 supports letters and numbers
                    String code39Data = data.toUpperCase().replaceAll("[^A-Z0-9\\-\\. ]", "");
                    if (!code39Data.isEmpty()) {
                        bitMatrix = multiFormatWriter.encode(code39Data, com.google.zxing.BarcodeFormat.CODE_39, width, height);
                        formatName = "Code 39";
                    } else {
                        showResult("❌ Code 39 requires alphanumeric characters only", false);
                        return;
                    }
                    break;

                case 2: // Test Barcode (QR Code for reliability)
                    bitMatrix = multiFormatWriter.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400);
                    width = 400;
                    height = 400;
                    formatName = "QR Code";
                    break;
            }

            if (bitMatrix != null) {
                // Convert BitMatrix to Bitmap - same as BarcodeDisplayActivity
                Bitmap testBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        testBitmap.setPixel(x, y,
                                bitMatrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                    }
                }

                testBarcodeImageView.setImageBitmap(testBitmap);
                currentTestBarcode = testBitmap;
                testBarcodeData = data;

                showResult("✅ Generated " + formatName + " barcode: " + data +
                        "\nThis is a REAL scannable barcode - try scanning it now!", true);
            } else {
                showResult("❌ Failed to generate " + formatName + " barcode", false);
            }

        } catch (com.google.zxing.WriterException e) {
            showResult("❌ Error generating barcode: " + e.getMessage(), false);
        }
    }

    /**
     * Generate a real test barcode using ZXing (same library as your scanner)
     */
    private Bitmap generateRealTestBarcode(String data, int maxWidth) {
        try {
            com.google.zxing.MultiFormatWriter multiFormatWriter = new com.google.zxing.MultiFormatWriter();

            // Use Code 128 since it works with your scanner
            com.google.zxing.common.BitMatrix bitMatrix = multiFormatWriter.encode(
                    data,
                    com.google.zxing.BarcodeFormat.CODE_128,
                    Math.min(maxWidth, 600),
                    200
            );

            // Convert to bitmap
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y,
                            bitMatrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }

            return bitmap;

        } catch (com.google.zxing.WriterException e) {
            android.util.Log.e("TroubleshootingActivity", "Error generating test barcode", e);
            return null;
        }
    }
    private void showCustomBarcodeTest() {
        EditText input = new EditText(this);
        input.setHint("Enter your barcode number");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Test Your Barcode");
        builder.setMessage("Enter the barcode number you want to test:\n\n" +
                "💡 Tips:\n" +
                "• 8-15 digits work best\n" +
                "• Letters + numbers also supported\n" +
                "• Avoid special characters");
        builder.setView(input);

        builder.setPositiveButton("Generate Code 128", (dialog, which) -> {
            String customData = input.getText().toString().trim();
            if (!customData.isEmpty()) {
                // Test with Code 128 format (format 0 in your updated method)
                generateAndTestFormat(customData, 0);

                // Show validation info
                if (ImprovedBarcodeSystem.isValidForScanning(customData)) {
                    showResult("✅ Input looks good for scanning: " + customData, true);
                } else {
                    showResult("⚠️ Input may have scanning issues, but trying anyway: " + customData, false);
                }
            }
        });

        builder.setNeutralButton("Generate QR Code", (dialog, which) -> {
            String customData = input.getText().toString().trim();
            if (!customData.isEmpty()) {
                // Test with QR Code format (format 2 in your updated method)
                generateAndTestFormat(customData, 2);
                showResult("🔲 Generated QR Code (very reliable for testing): " + customData, true);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showResult(String message, boolean isSuccess) {
        String currentText = statusTextView.getText().toString();
        String newText = currentText + "\n\n" + "🔍 " + message;
        statusTextView.setText(newText);

        // Scroll to bottom
        ScrollView scrollView = findViewById(R.id.scroll_view);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));

        // Show toast for important messages
        if (isSuccess) {
            Toast.makeText(this, "✅ Success!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "❌ Issue detected", Toast.LENGTH_SHORT).show();
        }
    }

    // Add method to share diagnostics
    private void shareDiagnostics() {
        String diagnosticsText = statusTextView.getText().toString();

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, diagnosticsText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Barcode Scanning Diagnostics");

        startActivity(Intent.createChooser(shareIntent, "Share Diagnostics"));
    }
}
package com.example.invenzora;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.MultiFormatWriter;
import android.util.Log;

public class BarcodeDisplayActivity extends AppCompatActivity {
    private ImageView barcodeImageView;
    private TextView barcodeInfoTextView;
    private Button saveButton, shareButton, printButton;
    private String barcodeData;
    private String itemName;
    private Bitmap currentBarcodeBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_display);

        // Get data from intent
        barcodeData = getIntent().getStringExtra("BARCODE_DATA");
        itemName = getIntent().getStringExtra("ITEM_NAME");

        if (barcodeData == null) {
            Toast.makeText(this, "No barcode data provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        generateAndDisplayBarcode();
    }

    private void initializeViews() {
        barcodeImageView = findViewById(R.id.iv_barcode);
        barcodeInfoTextView = findViewById(R.id.tv_barcode_info);
        saveButton = findViewById(R.id.btn_save_barcode);
        shareButton = findViewById(R.id.btn_share_barcode);
        printButton = findViewById(R.id.btn_print_barcode);

        saveButton.setOnClickListener(v -> saveBarcodeImage());
        shareButton.setOnClickListener(v -> shareBarcodeImage());
        printButton.setOnClickListener(v -> printBarcode());

        // Set item info
        String info = String.format("Item: %s\nBarcode: %s\nFormat: UPC-A Compatible",
                itemName != null ? itemName : "Unknown", barcodeData);
        barcodeInfoTextView.setText(info);
    }

    private void generateAndDisplayBarcode() {
        try {
            // Use ZXing library to generate REAL scannable barcodes
            currentBarcodeBitmap = generateScannableBarcodeWithZXing(barcodeData, 800, 300);

            if (currentBarcodeBitmap != null) {
                barcodeImageView.setImageBitmap(currentBarcodeBitmap);
            } else {
                // Fallback to text display
                showTextFallback();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error generating barcode: " + e.getMessage(), Toast.LENGTH_LONG).show();
            showTextFallback();
        }
    }

    private void saveBarcodeImage() {
        if (currentBarcodeBitmap == null) {
            Toast.makeText(this, "No barcode to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Use MediaStore for Android 10+ (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveImageToMediaStore();
            } else {
                // Fallback for older versions
                saveImageLegacy();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error saving barcode: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveImageToMediaStore() throws IOException {
        String fileName = "barcode_" + barcodeData + "_" + System.currentTimeMillis() + ".png";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Barcodes");

        ContentResolver resolver = getContentResolver();
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (imageUri != null) {
            try (OutputStream outputStream = resolver.openOutputStream(imageUri)) {
                if (outputStream != null) {
                    currentBarcodeBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    Toast.makeText(this, "Barcode saved to Pictures/Barcodes!", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            throw new IOException("Failed to create media store entry");
        }
    }

    @SuppressWarnings("deprecation")
    private void saveImageLegacy() {
        String fileName = "barcode_" + barcodeData + "_" + System.currentTimeMillis() + ".png";
        String savedImageURL = MediaStore.Images.Media.insertImage(
                getContentResolver(),
                currentBarcodeBitmap,
                fileName,
                "Barcode for " + (itemName != null ? itemName : barcodeData)
        );

        if (savedImageURL != null) {
            Toast.makeText(this, "Barcode saved to gallery!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Failed to save barcode", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareBarcodeImage() {
        if (currentBarcodeBitmap == null) {
            Toast.makeText(this, "No barcode to share", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Save temp file
            File cachePath = new File(getCacheDir(), "barcodes");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }

            File file = new File(cachePath, "barcode_" + barcodeData + ".png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                currentBarcodeBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }

            // Share using FileProvider
            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "Barcode for " + (itemName != null ? itemName : "item") + ": " + barcodeData);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Barcode"));

        } catch (IOException e) {
            Toast.makeText(this, "Error sharing barcode: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void printBarcode() {
        if (currentBarcodeBitmap == null) {
            Toast.makeText(this, "No barcode to print", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create print intent
            Intent printIntent = new Intent(Intent.ACTION_SEND);
            printIntent.setType("image/png");

            // Save temp file for printing
            File cachePath = new File(getCacheDir(), "print");
            if (!cachePath.exists()) {
                cachePath.mkdirs();
            }

            File file = new File(cachePath, "print_barcode_" + barcodeData + ".png");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                currentBarcodeBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }

            Uri contentUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);

            printIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            printIntent.putExtra(Intent.EXTRA_SUBJECT, "Barcode - " + barcodeData);
            printIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Try to find print apps
            Intent chooser = Intent.createChooser(printIntent, "Print Barcode");
            if (chooser.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
            } else {
                Toast.makeText(this, "No print apps available. Barcode saved to share instead.", Toast.LENGTH_LONG).show();
                shareBarcodeImage();
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error printing barcode: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Add method to create multiple barcode formats
    private void showBarcodeFormatOptions() {
        String[] formats = {
                "UPC-A (12 digits only)",
                "Code 128 (Recommended - works with your scanner!)",
                "QR Code (Square format)",
                "Data Matrix (Square format)"
        };

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Choose Barcode Format")
                .setMessage("Code 128 format works best with your scanner!")
                .setItems(formats, (dialog, which) -> {
                    generateBarcodeInFormat(which);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void generateBarcodeInFormat(int format) {
        int width = 800;
        int height = 300;

        try {
            com.google.zxing.MultiFormatWriter multiFormatWriter = new com.google.zxing.MultiFormatWriter();
            com.google.zxing.common.BitMatrix bitMatrix = null;

            switch (format) {
                case 0: // UPC-A
                    if (barcodeData.matches("\\d{12}")) {
                        bitMatrix = multiFormatWriter.encode(barcodeData, com.google.zxing.BarcodeFormat.UPC_A, width, height);
                    } else {
                        Toast.makeText(this, "UPC-A requires exactly 12 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    break;

                case 1: // Code 128 (This should work with your scanner!)
                    bitMatrix = multiFormatWriter.encode(barcodeData, com.google.zxing.BarcodeFormat.CODE_128, width, height);
                    break;

                case 2: // QR Code
                    bitMatrix = multiFormatWriter.encode(barcodeData, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400);
                    width = 400; // Square for QR code
                    height = 400;
                    break;

                case 3: // Data Matrix
                    bitMatrix = multiFormatWriter.encode(barcodeData, com.google.zxing.BarcodeFormat.DATA_MATRIX, 400, 400);
                    width = 400;
                    height = 400;
                    break;

                default:
                    Toast.makeText(this, "Unknown barcode format", Toast.LENGTH_SHORT).show();
                    return;
            }

            if (bitMatrix != null) {
                // Convert BitMatrix to Bitmap
                currentBarcodeBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        currentBarcodeBitmap.setPixel(x, y,
                                bitMatrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                    }
                }

                barcodeImageView.setImageBitmap(currentBarcodeBitmap);
                Toast.makeText(this, "✅ Generated scannable barcode in selected format", Toast.LENGTH_SHORT).show();
            }

        } catch (com.google.zxing.WriterException e) {
            Toast.makeText(this, "❌ Failed to generate barcode in selected format: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();

            // Fallback to text display
            showTextFallback();
        }
    }


    /**
     * Generate a REAL scannable barcode using ZXing library
     * This creates barcodes that your scanner can actually read
     */
    private android.graphics.Bitmap generateScannableBarcodeWithZXing(String data, int width, int height) {
        try {
            // Import these at the top of your file:
            // import com.google.zxing.BarcodeFormat;
            // import com.google.zxing.WriterException;
            // import com.google.zxing.common.BitMatrix;
            // import com.google.zxing.MultiFormatWriter;

            com.google.zxing.MultiFormatWriter multiFormatWriter = new com.google.zxing.MultiFormatWriter();
            com.google.zxing.common.BitMatrix bitMatrix;

            // Choose format based on data
            if (data.matches("\\d{12}")) {
                // 12 digits - try UPC-A first
                try {
                    bitMatrix = multiFormatWriter.encode(data, com.google.zxing.BarcodeFormat.UPC_A, width, height);
                } catch (Exception e) {
                    // Fallback to Code 128
                    bitMatrix = multiFormatWriter.encode(data, com.google.zxing.BarcodeFormat.CODE_128, width, height);
                }
            } else {
                // Use Code 128 for alphanumeric (this should work with your scanner!)
                bitMatrix = multiFormatWriter.encode(data, com.google.zxing.BarcodeFormat.CODE_128, width, height);
            }

            // Convert BitMatrix to Bitmap
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }

            return bitmap;

        } catch (com.google.zxing.WriterException e) {
            android.util.Log.e("BarcodeDisplay", "Error generating barcode", e);
            return null;
        }
    }

    /**
     * Fallback when barcode generation fails
     */
    private void showTextFallback() {
        // Create a simple text display
        android.graphics.Bitmap textBitmap = android.graphics.Bitmap.createBitmap(800, 300, android.graphics.Bitmap.Config.RGB_565);
        android.graphics.Canvas canvas = new android.graphics.Canvas(textBitmap);

        canvas.drawColor(android.graphics.Color.WHITE);

        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.BLACK);
        paint.setTextSize(40);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);

        canvas.drawText("Barcode: " + barcodeData, 400, 150, paint);
        canvas.drawText("(Text format - not scannable)", 400, 200, paint);

        currentBarcodeBitmap = textBitmap;
        barcodeImageView.setImageBitmap(currentBarcodeBitmap);
    }
}
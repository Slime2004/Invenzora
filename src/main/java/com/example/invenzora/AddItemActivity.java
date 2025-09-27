package com.example.invenzora;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.ArrayList;
import java.util.List;

public class AddItemActivity extends AppCompatActivity {
    private EditText nameEt, qtyEt, unitEt, priceEt, barcodeEt, lowStockThresholdEt;
    private Spinner groupSp;
    private ArrayAdapter<String> groupAdapter;
    private DatabaseHelper dbHelper;
    private CheckBox autoGenerateBarcodeCheckbox;
    private Button generateBarcodeBtn;
    private SessionManager sessionManager;

    private MLKitIntegrationHelper mlKitHelper;

    private androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    barcodeEt.setText(result.getContents());
                    autoGenerateBarcodeCheckbox.setChecked(false);
                }
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        sessionManager = new SessionManager(this);

        // Check if user has admin access
        if (!sessionManager.hasAdminAccess()) {
            showAccessDeniedAndFinish();
            return;
        }

        setContentView(R.layout.activity_add_item);

        dbHelper = new DatabaseHelper(this);

        // Initialize ML Kit scanner
        mlKitHelper = new MLKitIntegrationHelper();
        mlKitHelper.initialize(this, new MLKitIntegrationHelper.ScanResultCallback() {
            @Override
            public void onScanSuccess(String barcode) {
                barcodeEt.setText(barcode);
                autoGenerateBarcodeCheckbox.setChecked(false);
                Toast.makeText(AddItemActivity.this, "✅ Barcode scanned: " + barcode, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onScanCancelled() {
                Toast.makeText(AddItemActivity.this, "Scan cancelled", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onScanError(String error) {
                Toast.makeText(AddItemActivity.this, "Scan error: " + error, Toast.LENGTH_LONG).show();
            }
        });

        nameEt = findViewById(R.id.et_name);
        qtyEt = findViewById(R.id.et_quantity);
        unitEt = findViewById(R.id.et_unit);
        priceEt = findViewById(R.id.et_price);
        barcodeEt = findViewById(R.id.et_barcode);
        lowStockThresholdEt = findViewById(R.id.et_low_stock_threshold);
        groupSp = findViewById(R.id.sp_group);
        autoGenerateBarcodeCheckbox = findViewById(R.id.cb_auto_generate_barcode);
        generateBarcodeBtn = findViewById(R.id.btn_generate_barcode);
        Button addBtn = findViewById(R.id.btn_add_item);
        Button scanBarcodeBtn = findViewById(R.id.btn_scan_barcode);

        groupAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        groupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSp.setAdapter(groupAdapter);

        loadGroups();
        setupBarcodeControls();
        setupLowStockThreshold();

        addBtn.setOnClickListener(v -> addItem());
        scanBarcodeBtn.setOnClickListener(v -> mlKitHelper.startScan(this));

        Button viewBarcodeBtn = findViewById(R.id.btn_view_barcode);
        viewBarcodeBtn.setOnClickListener(v -> viewGeneratedBarcode());

        // Show admin indicator
        setTitle("Add Item - Admin Access");
    }

    private void showAccessDeniedAndFinish() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🚫 Access Denied")
                .setMessage("Sorry, only administrators can add new items.\n\nYour current role: " +
                        (sessionManager.isEmployee() ? "Employee" : "Unknown") +
                        "\n\nContact your administrator for access.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setupLowStockThreshold() {
        // Set default low stock threshold
        lowStockThresholdEt.setText("10");

        // Add input validation
        lowStockThresholdEt.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validateThresholdInput();
            }
        });
    }

    private void validateThresholdInput() {
        String thresholdStr = lowStockThresholdEt.getText().toString().trim();

        if (thresholdStr.isEmpty()) {
            lowStockThresholdEt.setText("10");
            return;
        }

        try {
            int threshold = Integer.parseInt(thresholdStr);
            if (threshold < 0) {
                lowStockThresholdEt.setText("0");
                Toast.makeText(this, "⚠️ Threshold cannot be negative. Set to 0.", Toast.LENGTH_SHORT).show();
            } else if (threshold > 9999) {
                lowStockThresholdEt.setText("9999");
                Toast.makeText(this, "⚠️ Threshold too high. Set to 9999.", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            lowStockThresholdEt.setText("10");
            Toast.makeText(this, "⚠️ Invalid threshold. Reset to 10.", Toast.LENGTH_SHORT).show();
        }
    }
    private void setupBarcodeControls() {
        autoGenerateBarcodeCheckbox.setChecked(true);
        updateBarcodeFieldState();

        autoGenerateBarcodeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateBarcodeFieldState();
            if (isChecked) {
                barcodeEt.setText("");
            }
        });

        generateBarcodeBtn.setOnClickListener(v -> generateNewBarcode());

        nameEt.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && autoGenerateBarcodeCheckbox.isChecked()) {
                generateNewBarcode();
            }
        });

        groupSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (autoGenerateBarcodeCheckbox.isChecked()) {
                    generateNewBarcode();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateBarcodeFieldState() {
        boolean autoGenerate = autoGenerateBarcodeCheckbox.isChecked();
        barcodeEt.setEnabled(!autoGenerate);
        generateBarcodeBtn.setVisibility(autoGenerate ? View.VISIBLE : View.GONE);

        if (autoGenerate) {
            barcodeEt.setHint("Barcode will be generated automatically");
        } else {
            barcodeEt.setHint("Enter barcode manually or scan");
        }
    }

    private void generateNewBarcode() {
        String itemName = nameEt.getText().toString().trim();
        String groupName = (String) groupSp.getSelectedItem();

        if (itemName.isEmpty()) {
            Toast.makeText(this, "Enter item name first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (groupName == null) {
            Toast.makeText(this, "Select a group first", Toast.LENGTH_SHORT).show();
            return;
        }

        String newBarcode;
        int attempts = 0;

        do {
            // Use the scanner-optimized barcode generator
            newBarcode = BarcodeGenerator.generateScannerOptimizedBarcode(itemName, groupName);
            attempts++;
        } while (isBarcodeExists(newBarcode) && attempts < 10);

        if (attempts >= 10) {
            // Fallback to simple numeric barcode
            newBarcode = BarcodeGenerator.generateNumericBarcode(itemName, groupName);
        }

        barcodeEt.setText(newBarcode);

        // Show detailed info to user
        Toast.makeText(this, "✅ Generated Code 128 barcode: " + newBarcode +
                        "\nFormat: " + newBarcode.length() + " characters",
                Toast.LENGTH_LONG).show();
    }

    private void viewGeneratedBarcode() {
        String barcode = barcodeEt.getText().toString().trim();
        String name = nameEt.getText().toString().trim();

        if (barcode.isEmpty()) {
            if (autoGenerateBarcodeCheckbox.isChecked()) {
                generateNewBarcode();
                barcode = barcodeEt.getText().toString().trim();
            } else {
                Toast.makeText(this, "No barcode to display", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Intent intent = new Intent(this, BarcodeDisplayActivity.class);
        intent.putExtra("BARCODE_DATA", barcode);
        intent.putExtra("ITEM_NAME", name.isEmpty() ? "Preview Item" : name);
        intent.putExtra("BARCODE_FORMAT", "CODE_128"); // Specify format
        startActivity(intent);
    }

    private boolean isBarcodeExists(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                DatabaseHelper.TABLE_INVENTORY,
                new String[]{DatabaseHelper.COL_ID},
                DatabaseHelper.COL_BARCODE + " = ?",
                new String[]{barcode},
                null, null, null
        );

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();

        return exists;
    }

    private void loadGroups() {
        List<String> groups = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                DatabaseHelper.TABLE_GROUPS,
                new String[]{DatabaseHelper.COL_GROUP_NAME_FIELD},
                null, null, null, null,
                DatabaseHelper.COL_GROUP_NAME_FIELD + " ASC"
        );

        if (cursor.moveToFirst()) {
            do {
                groups.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        groupAdapter.clear();
        groupAdapter.addAll(groups);
        groupAdapter.notifyDataSetChanged();
    }

    private void addItem() {
        String name = nameEt.getText().toString().trim();
        String unit = unitEt.getText().toString().trim();
        String priceStr = priceEt.getText().toString().trim();
        String qtyStr = qtyEt.getText().toString().trim();
        String thresholdStr = lowStockThresholdEt.getText().toString().trim();
        String group = (String) groupSp.getSelectedItem();

        if (name.isEmpty() || unit.isEmpty() || priceStr.isEmpty() || qtyStr.isEmpty() || group == null) {
            Toast.makeText(this, "Please fill all required fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (thresholdStr.isEmpty()) {
            lowStockThresholdEt.setText("10");
            thresholdStr = "10";
        }

        try {
            int qty = Integer.parseInt(qtyStr);
            double price = Double.parseDouble(priceStr);
            int threshold = Integer.parseInt(thresholdStr);

            String barcode = barcodeEt.getText().toString().trim();

            if (autoGenerateBarcodeCheckbox.isChecked()) {
                if (barcode.isEmpty()) {
                    generateNewBarcode();
                    barcode = barcodeEt.getText().toString().trim();
                }
            }

            if (!barcode.isEmpty() && !BarcodeGenerator.isValidBarcode(barcode)) {
                Toast.makeText(this, "Invalid barcode format. Please check the barcode.", Toast.LENGTH_LONG).show();
                return;
            }

            if (!barcode.isEmpty() && isBarcodeExists(barcode)) {
                Toast.makeText(this, "Barcode already exists. Please generate a new one or enter manually.", Toast.LENGTH_LONG).show();
                return;
            }

            if (threshold < 0) {
                threshold = 0;
                lowStockThresholdEt.setText("0");
            } else if (threshold > 9999) {
                threshold = 9999;
                lowStockThresholdEt.setText("9999");
            }

            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COL_NAME, name);
            values.put(DatabaseHelper.COL_QUANTITY, qty);
            values.put(DatabaseHelper.COL_UNIT, unit);
            values.put(DatabaseHelper.COL_PRICE, price);
            values.put(DatabaseHelper.COL_GROUP_NAME, group);
            values.put(DatabaseHelper.COL_LOW_STOCK_THRESHOLD, threshold);

            if (!barcode.isEmpty()) {
                values.put(DatabaseHelper.COL_BARCODE, barcode);
            }

            long result = db.insert(DatabaseHelper.TABLE_INVENTORY, null, values);
            db.close();

            if (result != -1) {
                String message = "✅ Item added successfully by " + sessionManager.getCurrentUserFullName() + "!";
                if (!barcode.isEmpty()) {
                    message += "\n📊 Barcode: " + barcode;
                }
                message += "\n⚠️ Low Stock Alert: " + threshold + " " + unit;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                if (qty <= threshold) {
                    message += "\n🔴 WARNING: Item is already below threshold!";
                }

                if (!barcode.isEmpty()) {
                    String finalBarcode = barcode;
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Item Added!")
                            .setMessage("Would you like to view and print the barcode?")
                            .setPositiveButton("View Barcode", (dialog, which) -> {
                                Intent intent = new Intent(this, BarcodeDisplayActivity.class);
                                intent.putExtra("BARCODE_DATA", finalBarcode);
                                intent.putExtra("ITEM_NAME", name);
                                startActivity(intent);
                                finish();
                            })
                            .setNegativeButton("Close", (dialog, which) -> finish())
                            .show();
                } else {
                    finish();
                }
            } else {
                Toast.makeText(this, "Failed to add item. Please try again.", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numbers for quantity and price.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
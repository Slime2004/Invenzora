package com.example.invenzora;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.journeyapps.barcodescanner.ScanIntentResult;

public class SellItemActivity extends AppCompatActivity {
    private EditText barcodeEt, sellQtyEt, itemIdEt;
    private LinearLayout itemDetailsLayout;
    private TextView itemNameTv, itemDetailsTv, userInfoTv;
    private RadioGroup searchMethodGroup;
    private RadioButton barcodeRadio, idRadio;
    private LinearLayout barcodeInputLayout, idInputLayout;
    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private int currentItemId = -1;
    private int currentStock = 0;
    private androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Check access control
        if (!AccessControlUtil.checkEmployeeAccess(this, sessionManager)) {
            return;
        }

        setContentView(R.layout.activity_sell_item);

        dbHelper = new DatabaseHelper(this);

        // ADD: Initialize the barcode launcher
        barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
            handleScanResult(result);
        });

        initializeViews();
        setupUserInterface();
        setupSearchMethodToggle();
        handleIntentExtras();
    }

    private void initializeViews() {
        searchMethodGroup = findViewById(R.id.rg_search_method);
        barcodeRadio = findViewById(R.id.rb_barcode);
        idRadio = findViewById(R.id.rb_id);

        barcodeInputLayout = findViewById(R.id.ll_barcode_input);
        idInputLayout = findViewById(R.id.ll_id_input);

        barcodeEt = findViewById(R.id.et_barcode_manual);
        itemIdEt = findViewById(R.id.et_item_id);
        sellQtyEt = findViewById(R.id.et_sell_quantity);

        itemDetailsLayout = findViewById(R.id.ll_item_details);
        itemNameTv = findViewById(R.id.tv_item_name);
        itemDetailsTv = findViewById(R.id.tv_item_details);
        userInfoTv = findViewById(R.id.tv_user_info);

        Button scanBtn = findViewById(R.id.btn_scan);
        Button findByBarcodeBtn = findViewById(R.id.btn_find_by_barcode);
        Button findByIdBtn = findViewById(R.id.btn_find_by_id);
        Button showItemsBtn = findViewById(R.id.btn_show_items);
        Button sellBtn = findViewById(R.id.btn_sell);

        // Set up click listeners
        scanBtn.setOnClickListener(v -> {
            try {
                startBarcodeScanning();
            } catch (Exception e) {
                Toast.makeText(this, "Error starting scanner: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });

        findByBarcodeBtn.setOnClickListener(v -> {
            String barcode = barcodeEt.getText().toString().trim();
            if (!barcode.isEmpty()) {
                findItemByBarcode(barcode);
            } else {
                Toast.makeText(this, "Enter or scan a barcode", Toast.LENGTH_SHORT).show();
            }
        });

        findByIdBtn.setOnClickListener(v -> {
            String idStr = itemIdEt.getText().toString().trim();
            if (!idStr.isEmpty()) {
                try {
                    int id = Integer.parseInt(idStr);
                    findItemById(id);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Enter a valid item ID", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Enter an item ID", Toast.LENGTH_SHORT).show();
            }
        });

        showItemsBtn.setOnClickListener(v -> showItemsList());
        sellBtn.setOnClickListener(v -> sellItem());
    }

    private void setupUserInterface() {
        // Update activity title and show user info
        AccessControlUtil.updateActivityTitle(this, "Sell Items", sessionManager);

        String userInfo = "🏪 Processing Sales - " + AccessControlUtil.getUserInfoString(sessionManager);
        userInfoTv.setText(userInfo);
    }

    private void handleIntentExtras() {
        // Handle prefilled data from search activity
        Intent intent = getIntent();
        String prefillItemId = intent.getStringExtra("PREFILL_ITEM_ID");
        String itemName = intent.getStringExtra("ITEM_NAME");

        if (prefillItemId != null && !prefillItemId.isEmpty()) {
            try {
                int itemId = Integer.parseInt(prefillItemId);
                idRadio.setChecked(true);
                itemIdEt.setText(prefillItemId);
                findItemById(itemId);

                if (itemName != null) {
                    Toast.makeText(this, "Item loaded: " + itemName, Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                // Invalid ID, ignore
            }
        }
    }

    private void setupSearchMethodToggle() {
        searchMethodGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_barcode) {
                barcodeInputLayout.setVisibility(LinearLayout.VISIBLE);
                idInputLayout.setVisibility(LinearLayout.GONE);
                hideItemDetails();
            } else if (checkedId == R.id.rb_id) {
                barcodeInputLayout.setVisibility(LinearLayout.GONE);
                idInputLayout.setVisibility(LinearLayout.VISIBLE);
                hideItemDetails();
            }
        });

        // Default to barcode method
        barcodeRadio.setChecked(true);
    }

    private void findItemByBarcode(String barcode) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                DatabaseHelper.TABLE_INVENTORY,
                null,
                DatabaseHelper.COL_BARCODE + " = ?",
                new String[]{barcode},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            displayItemDetails(cursor);
        } else {
            Toast.makeText(this, "Item not found with barcode: " + barcode, Toast.LENGTH_LONG).show();
            hideItemDetails();

            // Only admins can add new items
            if (sessionManager.isAdmin()) {
                showAddNewItemOption(barcode, null);
            }
        }

        cursor.close();
        db.close();
    }

    private void findItemById(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                DatabaseHelper.TABLE_INVENTORY,
                null,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(id)},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            displayItemDetails(cursor);
        } else {
            Toast.makeText(this, "Item not found with ID: " + id, Toast.LENGTH_LONG).show();
            hideItemDetails();
        }

        cursor.close();
        db.close();
    }

    // REPLACE your displayItemDetails() method in SellItemActivity.java:

    private void displayItemDetails(Cursor cursor) {
        try {
            currentItemId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NAME));
            currentStock = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUANTITY));
            String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
            double price = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRICE));
            String group = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GROUP_NAME));

            // Get barcode if it exists
            int barcodeIndex = cursor.getColumnIndex(DatabaseHelper.COL_BARCODE);
            String barcode = (barcodeIndex >= 0) ? cursor.getString(barcodeIndex) : "No barcode";

            itemNameTv.setText(name);

            // Enhanced display with RM currency formatting
            itemDetailsTv.setText(String.format(
                    "🆔 Item ID: %d\n" +
                            "📦 Stock Available: %d %s\n" +
                            "💰 Unit Price: RM %.2f\n" +
                            "🏷️ Category: %s\n" +
                            "📊 Barcode: %s",
                    currentItemId, currentStock, unit, price, group,
                    (barcode != null && !barcode.isEmpty()) ? barcode : "None"
            ));

            itemDetailsLayout.setVisibility(LinearLayout.VISIBLE);

            if (currentStock <= 0) {
                Toast.makeText(this, "⚠️ Item is out of stock!", Toast.LENGTH_LONG).show();
                findViewById(R.id.btn_sell).setEnabled(false);
            } else {
                findViewById(R.id.btn_sell).setEnabled(true);
                sellQtyEt.requestFocus();


            }
        } catch (Exception e) {
            Toast.makeText(this, "Error displaying item details: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private void hideItemDetails() {
        itemDetailsLayout.setVisibility(LinearLayout.GONE);
        currentItemId = -1;
        currentStock = 0;
    }

    private void showAddNewItemOption(String barcode, Integer id) {
        if (!sessionManager.isAdmin()) {
            return; // Only admins can add items
        }

        String message;
        if (barcode != null) {
            message = "No item found with barcode: " + barcode + "\n\nWould you like to add a new item with this barcode?";
        } else {
            message = "No item found with ID: " + id + "\n\nWould you like to add a new item?";
        }

        new AlertDialog.Builder(this)
                .setTitle("Item Not Found")
                .setMessage(message)
                .setPositiveButton("Add New Item", (dialog, which) -> {
                    Intent intent = new Intent(this, AddItemActivity.class);
                    if (barcode != null) {
                        intent.putExtra("PREFILL_BARCODE", barcode);
                    }
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sellItem() {
        if (currentItemId == -1) {
            Toast.makeText(this, "No item selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String qtyStr = sellQtyEt.getText().toString().trim();
        if (qtyStr.isEmpty()) {
            Toast.makeText(this, "Enter quantity to sell", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int sellQty = Integer.parseInt(qtyStr);

            if (sellQty <= 0) {
                Toast.makeText(this, "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }

            if (sellQty > currentStock) {
                Toast.makeText(this, "Not enough stock! Available: " + currentStock, Toast.LENGTH_LONG).show();
                return;
            }

            // GET THE ITEM PRICE for calculation
            double itemPrice = getCurrentItemPrice();
            double totalPrice = itemPrice * sellQty;

            // Enhanced confirmation dialog with price information
            String confirmMessage = "💰 Sale Confirmation\n\n" +
                    "📦 Item Details:\n" +
                    "• Quantity: " + sellQty + " units\n" +
                    "• Unit Price: RM " + String.format("%.2f", itemPrice) + "\n" +
                    "• Total Amount: RM " + String.format("%.2f", totalPrice) + "\n\n" +
                    "📊 Stock Information:\n" +
                    "• Current Stock: " + currentStock + " units\n" +
                    "• Remaining After Sale: " + (currentStock - sellQty) + " units\n\n" +
                    "👤 Transaction Details:\n" +
                    "• Processed by: " + sessionManager.getCurrentUserFullName() + "\n" +
                    "• Date: " + getCurrentDateTime();

            new AlertDialog.Builder(this)
                    .setTitle("🛒 Confirm Sale - RM " + String.format("%.2f", totalPrice))
                    .setMessage(confirmMessage)
                    .setPositiveButton("💳 Complete Sale", (dialog, which) -> performSale(sellQty, totalPrice))
                    .setNegativeButton("❌ Cancel", null)
                    .show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter a valid quantity", Toast.LENGTH_SHORT).show();
        }
    }

    // ADD this helper method to get the current item price:
    private double getCurrentItemPrice() {
        if (currentItemId == -1) {
            return 0.0;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        double price = 0.0;

        Cursor cursor = db.query(
                DatabaseHelper.TABLE_INVENTORY,
                new String[]{DatabaseHelper.COL_PRICE},
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(currentItemId)},
                null, null, null
        );

        if (cursor.moveToFirst()) {
            price = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRICE));
        }

        cursor.close();
        db.close();

        return price;
    }

    // ADD this helper method to get current date/time:
    private String getCurrentDateTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    // UPDATE the performSale method to include price information:
    private void performSale(int sellQty, double totalPrice) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int newStock = currentStock - sellQty;

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_QUANTITY, newStock);

        int rowsAffected = db.update(
                DatabaseHelper.TABLE_INVENTORY,
                values,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(currentItemId)}
        );

        db.close();

        if (rowsAffected > 0) {
            // Enhanced success message with price details
            String successMessage = "🎉 Sale Completed Successfully!\n\n" +
                    "💰 Transaction Summary:\n" +
                    "• Units Sold: " + sellQty + "\n" +
                    "• Total Amount: RM " + String.format("%.2f", totalPrice) + "\n" +
                    "• New Stock Level: " + newStock + " units\n\n" +
                    "👤 Processed by: " + sessionManager.getCurrentUserFullName() + "\n" +
                    "📅 Time: " + getCurrentDateTime();

            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();

            // Show detailed receipt dialog
            showReceiptDialog(sellQty, totalPrice, newStock);

            // Update display
            currentStock = newStock;
            updateItemDetailsDisplay();

            sellQtyEt.setText("1"); // Reset quantity

            if (newStock <= 0) {
                Toast.makeText(this, "⚠️ Item is now out of stock!", Toast.LENGTH_LONG).show();
                findViewById(R.id.btn_sell).setEnabled(false);
            }

        } else {
            Toast.makeText(this, "❌ Failed to update stock", Toast.LENGTH_SHORT).show();
        }
    }

    // ADD method to show detailed receipt:
    private void showReceiptDialog(int soldQty, double totalPrice, int newStock) {
        String itemName = itemNameTv.getText().toString();

        String receiptText = "🧾 SALES RECEIPT\n" +
                "================================\n\n" +
                "📦 Item: " + itemName + "\n" +
                "🔢 Quantity Sold: " + soldQty + " units\n" +
                "💵 Unit Price: RM " + String.format("%.2f", totalPrice / soldQty) + "\n" +
                "💰 TOTAL: RM " + String.format("%.2f", totalPrice) + "\n\n" +
                "📊 Stock After Sale: " + newStock + " units\n\n" +
                "👤 Cashier: " + sessionManager.getCurrentUserFullName() + "\n" +
                "📅 Date/Time: " + getCurrentDateTime() + "\n\n" +
                "Thank you for your business! 😊\n" +
                "================================";

        new AlertDialog.Builder(this)
                .setTitle("🧾 Transaction Receipt")
                .setMessage(receiptText)
                .setPositiveButton("📱 Share Receipt", (dialog, which) -> shareReceipt(receiptText))
                .setNegativeButton("✅ Done", (dialog, which) -> clearInputFields())
                .setNeutralButton("🖨️ Print", (dialog, which) -> {
                    // You can add print functionality here if needed
                    Toast.makeText(this, "Print feature coming soon!", Toast.LENGTH_SHORT).show();
                    clearInputFields();
                })
                .setCancelable(false)
                .show();
    }

    // ADD method to share receipt:
    private void shareReceipt(String receiptText) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, receiptText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Sales Receipt - " + getCurrentDateTime());

        startActivity(Intent.createChooser(shareIntent, "Share Receipt"));

        // Clear fields after sharing
        clearInputFields();
    }

    // ADD method to update item details display with price:
    private void updateItemDetailsDisplay() {
        if (currentItemId != -1) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            Cursor cursor = db.query(
                    DatabaseHelper.TABLE_INVENTORY,
                    null,
                    DatabaseHelper.COL_ID + " = ?",
                    new String[]{String.valueOf(currentItemId)},
                    null, null, null
            );

            if (cursor.moveToFirst()) {
                displayItemDetails(cursor);
            }

            cursor.close();
            db.close();
        }
    }

    private void performSale(int sellQty) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int newStock = currentStock - sellQty;

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_QUANTITY, newStock);

        int rowsAffected = db.update(
                DatabaseHelper.TABLE_INVENTORY,
                values,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(currentItemId)}
        );

        db.close();

        if (rowsAffected > 0) {
            String successMessage = "✅ Sale completed!\n" +
                    "Units sold: " + sellQty + "\n" +
                    "Processed by: " + sessionManager.getCurrentUserFullName();
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();

            // Update display
            currentStock = newStock;
            String currentText = itemDetailsTv.getText().toString();
            String updatedText = currentText.replaceFirst("Stock: \\d+", "Stock: " + newStock);
            itemDetailsTv.setText(updatedText);

            sellQtyEt.setText("1"); // Reset quantity

            if (newStock <= 0) {
                Toast.makeText(this, "⚠️ Item is now out of stock!", Toast.LENGTH_LONG).show();
                findViewById(R.id.btn_sell).setEnabled(false);
            }

            // Clear input fields for next transaction
            clearInputFields();

        } else {
            Toast.makeText(this, "❌ Failed to update stock", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearInputFields() {
        barcodeEt.setText("");
        itemIdEt.setText("");
        hideItemDetails();
        barcodeRadio.setChecked(true);
    }

    private void showItemsList() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                DatabaseHelper.TABLE_INVENTORY,
                new String[]{DatabaseHelper.COL_ID, DatabaseHelper.COL_NAME, DatabaseHelper.COL_QUANTITY},
                null, null, null, null,
                DatabaseHelper.COL_NAME + " ASC"
        );

        StringBuilder itemsList = new StringBuilder();
        itemsList.append("Available Items:\n\n");

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NAME));
                int qty = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUANTITY));

                itemsList.append(String.format("ID: %d - %s (Stock: %d)\n", id, name, qty));
            } while (cursor.moveToNext());
        } else {
            itemsList.append("No items found in inventory.");
        }

        cursor.close();
        db.close();

        new AlertDialog.Builder(this)
                .setTitle("📋 Item ID Reference")
                .setMessage(itemsList.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void startBarcodeScanning() {
        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.ALL_CODE_TYPES);
        options.setPrompt("Scan item barcode");
        options.setCameraId(0);
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);

        barcodeLauncher.launch(options);
    }

    private void handleScanResult(com.journeyapps.barcodescanner.ScanIntentResult result) {
        if (result.getContents() == null) {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
        } else {
            String scannedBarcode = result.getContents();
            String format = result.getFormatName();

            // DEBUG: Show what was scanned
            Toast.makeText(this, "✅ Scanned: " + scannedBarcode +
                    " (Format: " + format + ")", Toast.LENGTH_LONG).show();

            // Set the barcode and find the item
            barcodeEt.setText(scannedBarcode);
            findItemByBarcode(scannedBarcode);
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
package com.example.invenzora;

import static com.example.invenzora.BarcodeHelper.handleScanResult;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.*;

public class SearchItemActivity extends AppCompatActivity {
    private EditText searchEt;
    private List<Map<String,String>> data = new ArrayList<>();
    private SimpleAdapter adapter;
    private DatabaseHelper dbHelper;
    private TextView resultsHeaderTv;
    private Button bulkBarcodeBtn;

    private androidx.activity.result.ActivityResultLauncher<ScanOptions> barcodeLauncher;
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_search_item);

        barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
            handleScanResult(result);
        });

        dbHelper = new DatabaseHelper(this);


        searchEt = findViewById(R.id.et_search);
        Button searchBtn = findViewById(R.id.btn_search_go);
        Button scanSearchBtn = findViewById(R.id.btn_scan_search);
        Button clearSearchBtn = findViewById(R.id.btn_clear_search);
        ListView list = findViewById(R.id.search_list);
        resultsHeaderTv = findViewById(R.id.tv_results_header);
        bulkBarcodeBtn = findViewById(R.id.btn_bulk_barcode_generate);
        Button toggleHelpBtn = findViewById(R.id.btn_toggle_help);
        LinearLayout helpSection = findViewById(R.id.ll_help_section);

        // Enhanced adapter with three lines for better display
        adapter = new SimpleAdapter(this, data, android.R.layout.simple_list_item_2,
                new String[]{"name","details"},
                new int[]{android.R.id.text1, android.R.id.text2});
        list.setAdapter(adapter);

        // Enhanced adapter with three lines for better display
        adapter = new SimpleAdapter(this, data, android.R.layout.simple_list_item_2,
                new String[]{"name","details"},
                new int[]{android.R.id.text1, android.R.id.text2});
        list.setAdapter(adapter);

        // Set up item click listener for barcode actions
        list.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, String> selectedItem = data.get(position);
            showItemOptionsDialog(selectedItem);
        });

        // Set up click listeners
        searchBtn.setOnClickListener(v -> search());
        //scanSearchBtn.setOnClickListener(v -> mlKitHelper.startScan(this));
        scanSearchBtn.setOnClickListener(v -> {
            try {
                startBarcodeScanning();
            } catch (Exception e) {
                Toast.makeText(this, "Error starting scanner: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });

        clearSearchBtn.setOnClickListener(v -> {
            searchEt.setText("");
            search();
        });
        bulkBarcodeBtn.setOnClickListener(v -> generateBarcodesForSearchResults());

        // Help toggle functionality
        toggleHelpBtn.setOnClickListener(v -> {
            if (helpSection.getVisibility() == View.VISIBLE) {
                helpSection.setVisibility(View.GONE);
                toggleHelpBtn.setText("💡 Show Help");
            } else {
                helpSection.setVisibility(View.VISIBLE);
                toggleHelpBtn.setText("❌ Hide Help");
            }
        });

        // Handle search on enter key
        searchEt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });

        search(); // initial load (empty keyword)
    }

    private void search() {
        String keyword = searchEt.getText().toString().trim();
        List<Map<String,String>> rows = new ArrayList<>();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String selection = null;
        String[] selectionArgs = null;

        if (!keyword.isEmpty()) {
            // Search in both name and barcode fields
            selection = DatabaseHelper.COL_NAME + " LIKE ? OR " + DatabaseHelper.COL_BARCODE + " LIKE ?";
            selectionArgs = new String[]{"%" + keyword + "%", "%" + keyword + "%"};
        }

        Cursor cursor = db.query(
                DatabaseHelper.TABLE_INVENTORY,
                new String[]{
                        DatabaseHelper.COL_ID,
                        DatabaseHelper.COL_NAME,
                        DatabaseHelper.COL_QUANTITY,
                        DatabaseHelper.COL_UNIT,
                        DatabaseHelper.COL_PRICE,
                        DatabaseHelper.COL_GROUP_NAME,
                        DatabaseHelper.COL_BARCODE
                },
                selection, selectionArgs, null, null,
                DatabaseHelper.COL_ID + " ASC"
        );

        if (cursor.moveToFirst()) {
            do {
                Map<String,String> row = new HashMap<>();

                int itemId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NAME));
                int quantity = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUANTITY));
                String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                double price = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRICE));
                String group = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GROUP_NAME));

                // Handle barcode (might be null)
                int barcodeIndex = cursor.getColumnIndex(DatabaseHelper.COL_BARCODE);
                String barcode = (barcodeIndex >= 0) ? cursor.getString(barcodeIndex) : null;

                row.put("name", name + (barcode != null && !barcode.isEmpty() ? " 📊" : ""));

                String details = String.format("ID: %d | Qty: %d %s | Price: $%.2f | Group: %s\nBarcode: %s",
                        itemId, quantity, unit, price, group,
                        (barcode != null && !barcode.isEmpty()) ? barcode : "No barcode");

                row.put("details", details);
                row.put("id", String.valueOf(itemId));
                row.put("barcode", barcode != null ? barcode : "");
                row.put("item_name", name);

                rows.add(row);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();

        data.clear();
        data.addAll(rows);
        adapter.notifyDataSetChanged();

        if (rows.isEmpty()) {
            Toast.makeText(this, keyword.isEmpty() ? "No items found." : "No items match your search.", Toast.LENGTH_SHORT).show();
            resultsHeaderTv.setText("No Results");
            bulkBarcodeBtn.setVisibility(View.GONE);
        } else {
            // Count items without barcodes
            int itemsWithoutBarcode = 0;
            for (Map<String, String> row : rows) {
                if (row.get("barcode").isEmpty()) {
                    itemsWithoutBarcode++;
                }
            }

            resultsHeaderTv.setText("Found " + rows.size() + " item(s)");

            if (itemsWithoutBarcode > 0) {
                bulkBarcodeBtn.setVisibility(View.VISIBLE);
                bulkBarcodeBtn.setText("🏷️ Generate " + itemsWithoutBarcode + " Missing Barcodes");
            } else {
                bulkBarcodeBtn.setVisibility(View.GONE);
            }

            Toast.makeText(this, "Found " + rows.size() + " item(s). Tap any item for barcode options.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showItemOptionsDialog(Map<String, String> item) {
        String itemName = item.get("item_name");
        String barcode = item.get("barcode");
        String itemId = item.get("id");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📦 " + itemName);

        // Create options list
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (barcode != null && !barcode.isEmpty()) {
            // Item has barcode - show barcode options
            options.add("📊 View Barcode Image");
            actions.add(() -> viewBarcodeImage(barcode, itemName));

            options.add("📋 Copy Barcode to Clipboard");
            actions.add(() -> copyBarcodeToClipboard(barcode));

            options.add("🔍 Search by This Barcode");
            actions.add(() -> searchByBarcode(barcode));

            options.add("🛒 Sell This Item");
            actions.add(() -> sellItem(itemId, itemName));
        } else {
            // Item has no barcode - offer to generate one
            options.add("🏷️ Generate Barcode for This Item");
            actions.add(() -> generateBarcodeForItem(itemId, itemName));

            options.add("🛒 Sell This Item");
            actions.add(() -> sellItem(itemId, itemName));
        }

        options.add("✏️ Edit Item");
        actions.add(() -> editItem(itemId));

        options.add("📱 Share Item Details");
        actions.add(() -> shareItemDetails(item));

        String[] optionsArray = options.toArray(new String[0]);

        builder.setItems(optionsArray, (dialog, which) -> {
            if (which < actions.size()) {
                actions.get(which).run();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void viewBarcodeImage(String barcode, String itemName) {
        Intent intent = new Intent(this, BarcodeDisplayActivity.class);
        intent.putExtra("BARCODE_DATA", barcode);
        intent.putExtra("ITEM_NAME", itemName);
        startActivity(intent);
    }

    private void copyBarcodeToClipboard(String barcode) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Barcode", barcode);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "✅ Barcode copied: " + barcode, Toast.LENGTH_SHORT).show();
    }

    private void searchByBarcode(String barcode) {
        searchEt.setText(barcode);
        search();
        Toast.makeText(this, "🔍 Searching for items with barcode: " + barcode, Toast.LENGTH_SHORT).show();
    }

    private void sellItem(String itemId, String itemName) {
        Intent intent = new Intent(this, SellItemActivity.class);
        intent.putExtra("PREFILL_ITEM_ID", itemId);
        intent.putExtra("ITEM_NAME", itemName);
        startActivity(intent);
    }

    private void editItem(String itemId) {
        Intent intent = new Intent(this, UpdateItemActivity.class);
        intent.putExtra("PREFILL_ITEM_ID", itemId);
        startActivity(intent);
    }

    private void generateBarcodeForItem(String itemId, String itemName) {
        new AlertDialog.Builder(this)
                .setTitle("Generate Barcode")
                .setMessage("Generate a barcode for '" + itemName + "'?\n\nThis will create a unique barcode that can be used for scanning.")
                .setPositiveButton("Generate", (dialog, which) -> {
                    generateAndAssignBarcode(Integer.parseInt(itemId), itemName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void generateAndAssignBarcode(int itemId, String itemName) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            // Get item details for barcode generation
            Cursor cursor = db.query(
                    DatabaseHelper.TABLE_INVENTORY,
                    new String[]{DatabaseHelper.COL_GROUP_NAME},
                    DatabaseHelper.COL_ID + " = ?",
                    new String[]{String.valueOf(itemId)},
                    null, null, null
            );

            if (cursor.moveToFirst()) {
                String groupName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GROUP_NAME));

                // Generate unique barcode
                String newBarcode;
                int attempts = 0;
                do {
                    newBarcode = BarcodeGenerator.generateBarcode(itemName, groupName);
                    attempts++;
                } while (isBarcodeExists(newBarcode) && attempts < 10);

                if (attempts >= 10) {
                    newBarcode = BarcodeGenerator.generateEAN13StyleBarcode(itemName, groupName);
                }

                // Update item with new barcode
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(DatabaseHelper.COL_BARCODE, newBarcode);

                int updated = db.update(
                        DatabaseHelper.TABLE_INVENTORY,
                        values,
                        DatabaseHelper.COL_ID + " = ?",
                        new String[]{String.valueOf(itemId)}
                );

                if (updated > 0) {
                    Toast.makeText(this, "✅ Barcode generated: " + newBarcode, Toast.LENGTH_LONG).show();

                    // Offer to view the generated barcode
                    String finalNewBarcode = newBarcode;
                    new AlertDialog.Builder(this)
                            .setTitle("Barcode Generated!")
                            .setMessage("New barcode: " + newBarcode + "\n\nWould you like to view the barcode image?")
                            .setPositiveButton("View Barcode", (d, w) -> viewBarcodeImage(finalNewBarcode, itemName))
                            .setNegativeButton("Close", (d, w) -> search()) // Refresh search to show new barcode
                            .show();
                } else {
                    Toast.makeText(this, "❌ Failed to generate barcode", Toast.LENGTH_SHORT).show();
                }
            }

            cursor.close();
            db.close();

        } catch (Exception e) {
            Toast.makeText(this, "Error generating barcode: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

    private void shareItemDetails(Map<String, String> item) {
        String itemName = item.get("item_name");
        String barcode = item.get("barcode");
        String details = item.get("details");

        StringBuilder shareText = new StringBuilder();
        shareText.append("📦 Item Details from Invenzora\n\n");
        shareText.append("Name: ").append(itemName).append("\n");
        shareText.append(details.replace(" | ", "\n"));

        if (barcode != null && !barcode.isEmpty()) {
            shareText.append("\n\n📊 Barcode: ").append(barcode);
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Item Details: " + itemName);

        startActivity(Intent.createChooser(shareIntent, "Share Item Details"));
    }

    private void generateBarcodesForSearchResults() {
        // Get all items without barcodes from current search results
        List<Map<String, String>> itemsWithoutBarcode = new ArrayList<>();
        for (Map<String, String> item : data) {
            if (item.get("barcode").isEmpty()) {
                itemsWithoutBarcode.add(item);
            }
        }

        if (itemsWithoutBarcode.isEmpty()) {
            Toast.makeText(this, "All items in search results already have barcodes!", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Generate Barcodes")
                .setMessage("Generate barcodes for " + itemsWithoutBarcode.size() + " items from search results?\n\nThis will create unique barcodes for each item.")
                .setPositiveButton("Generate All", (dialog, which) -> {
                    generateBarcodesInBackground(itemsWithoutBarcode);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void generateBarcodesInBackground(List<Map<String, String>> items) {
        // Simple synchronous generation (for better user experience, could be made async)
        int generatedCount = 0;
        int totalItems = items.size();

        for (Map<String, String> item : items) {
            try {
                int itemId = Integer.parseInt(item.get("id"));
                String itemName = item.get("item_name");

                // Get group name for barcode generation
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                Cursor cursor = db.query(
                        DatabaseHelper.TABLE_INVENTORY,
                        new String[]{DatabaseHelper.COL_GROUP_NAME},
                        DatabaseHelper.COL_ID + " = ?",
                        new String[]{String.valueOf(itemId)},
                        null, null, null
                );

                if (cursor.moveToFirst()) {
                    String groupName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GROUP_NAME));

                    // Generate unique barcode
                    String newBarcode;
                    int attempts = 0;
                    do {
                        newBarcode = BarcodeGenerator.generateBarcode(itemName, groupName);
                        attempts++;
                    } while (isBarcodeExists(newBarcode) && attempts < 10);

                    if (attempts >= 10) {
                        newBarcode = BarcodeGenerator.generateEAN13StyleBarcode(itemName, groupName);
                    }

                    // Update item with new barcode
                    db.close();
                    db = dbHelper.getWritableDatabase();

                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(DatabaseHelper.COL_BARCODE, newBarcode);

                    int updated = db.update(
                            DatabaseHelper.TABLE_INVENTORY,
                            values,
                            DatabaseHelper.COL_ID + " = ?",
                            new String[]{String.valueOf(itemId)}
                    );

                    if (updated > 0) {
                        generatedCount++;
                    }
                }

                cursor.close();
                db.close();

            } catch (Exception e) {
                // Continue with next item if one fails
                continue;
            }
        }

        // Show results and refresh search
        Toast.makeText(this, "✅ Generated " + generatedCount + " out of " + totalItems + " barcodes!", Toast.LENGTH_LONG).show();
        search(); // Refresh to show new barcodes
    }

    private void startBarcodeScanning() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES);
        options.setPrompt("Scan a barcode");
        options.setCameraId(0);
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);

        barcodeLauncher.launch(options);
    }

    private void handleScanResult(ScanIntentResult result) {
        if (result.getContents() == null) {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
        } else {
            String scannedBarcode = result.getContents();
            searchEt.setText(scannedBarcode);
            search();
            Toast.makeText(this, "✅ Searching for: " + scannedBarcode, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh search when returning to this activity (in case barcodes were generated elsewhere)
        search();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
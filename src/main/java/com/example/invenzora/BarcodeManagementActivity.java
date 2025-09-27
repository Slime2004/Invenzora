package com.example.invenzora;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class BarcodeManagementActivity extends AppCompatActivity {
    private DatabaseHelper dbHelper;
    private TextView statsTextView;
    private Button generateAllBtn, viewMissingBtn;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_management);

        dbHelper = new DatabaseHelper(this);

        statsTextView = findViewById(R.id.tv_barcode_stats);
        generateAllBtn = findViewById(R.id.btn_generate_all_barcodes);
        viewMissingBtn = findViewById(R.id.btn_view_missing_barcodes);
        progressBar = findViewById(R.id.progress_bar);

        updateStats();

        generateAllBtn.setOnClickListener(v -> showGenerateConfirmation());
        viewMissingBtn.setOnClickListener(v -> showItemsWithoutBarcodes());
    }

    private void updateStats() {
        int totalItems = dbHelper.getTotalItemCount();
        int itemsWithBarcodes = dbHelper.getItemsWithBarcodesCount();
        int itemsWithoutBarcodes = totalItems - itemsWithBarcodes;

        String statsText = String.format(
                "Barcode Statistics:\n\n" +
                        "Total Items: %d\n" +
                        "Items with Barcodes: %d\n" +
                        "Items without Barcodes: %d\n\n" +
                        "Coverage: %.1f%%",
                totalItems, itemsWithBarcodes, itemsWithoutBarcodes,
                totalItems > 0 ? (itemsWithBarcodes * 100.0 / totalItems) : 0
        );

        statsTextView.setText(statsText);

        // Update button states
        generateAllBtn.setEnabled(itemsWithoutBarcodes > 0);
        viewMissingBtn.setEnabled(itemsWithoutBarcodes > 0);

        if (itemsWithoutBarcodes == 0) {
            generateAllBtn.setText("All items have barcodes");
            viewMissingBtn.setText("No missing barcodes");
        } else {
            generateAllBtn.setText("Generate Barcodes (" + itemsWithoutBarcodes + " items)");
            viewMissingBtn.setText("View Items Missing Barcodes");
        }
    }

    private void showGenerateConfirmation() {
        int itemsWithoutBarcodes = dbHelper.getTotalItemCount() - dbHelper.getItemsWithBarcodesCount();

        new AlertDialog.Builder(this)
                .setTitle("Generate Barcodes")
                .setMessage("Generate barcodes for " + itemsWithoutBarcodes + " items?\n\n" +
                        "This will create unique barcodes for all items that don't have them.")
                .setPositiveButton("Generate", (dialog, which) -> generateBarcodesForAllItems())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void generateBarcodesForAllItems() {
        new GenerateBarcodesTask().execute();
    }

    private void showItemsWithoutBarcodes() {
        Cursor cursor = dbHelper.getItemsWithoutBarcodes();

        if (cursor.getCount() == 0) {
            Toast.makeText(this, "All items have barcodes!", Toast.LENGTH_SHORT).show();
            cursor.close();
            return;
        }

        StringBuilder itemsList = new StringBuilder();
        itemsList.append("Items without barcodes:\n\n");

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndex(DatabaseHelper.COL_ID));
                String name = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_NAME));
                String group = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_GROUP_NAME));

                itemsList.append(String.format("ID: %d - %s (%s)\n", id, name, group));
            } while (cursor.moveToNext());
        }

        cursor.close();

        new AlertDialog.Builder(this)
                .setTitle("Items Missing Barcodes")
                .setMessage(itemsList.toString())
                .setPositiveButton("Generate All", (dialog, which) -> generateBarcodesForAllItems())
                .setNegativeButton("Close", null)
                .show();
    }

    private class GenerateBarcodesTask extends AsyncTask<Void, Integer, Integer> {
        private int totalItems = 0;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            generateAllBtn.setEnabled(false);
            viewMissingBtn.setEnabled(false);
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int generatedCount = 0;

            Cursor cursor = db.query(
                    DatabaseHelper.TABLE_INVENTORY,
                    new String[]{DatabaseHelper.COL_ID, DatabaseHelper.COL_NAME, DatabaseHelper.COL_GROUP_NAME},
                    DatabaseHelper.COL_BARCODE + " IS NULL OR " + DatabaseHelper.COL_BARCODE + " = ''",
                    null, null, null, DatabaseHelper.COL_ID + " ASC"
            );

            totalItems = cursor.getCount();

            if (cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(cursor.getColumnIndex(DatabaseHelper.COL_ID));
                    String name = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_NAME));
                    String group = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_GROUP_NAME));

                    // Generate unique barcode
                    String barcode = generateUniqueBarcode(db, name, group);

                    // Update item with barcode
                    ContentValues values = new ContentValues();
                    values.put(DatabaseHelper.COL_BARCODE, barcode);

                    int updated = db.update(
                            DatabaseHelper.TABLE_INVENTORY,
                            values,
                            DatabaseHelper.COL_ID + " = ?",
                            new String[]{String.valueOf(id)}
                    );

                    if (updated > 0) {
                        generatedCount++;
                    }

                    // Update progress
                    publishProgress(generatedCount);

                    // Small delay to show progress
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }

                } while (cursor.moveToNext());
            }

            cursor.close();
            db.close();
            return generatedCount;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            // Update progress if needed
        }

        @Override
        protected void onPostExecute(Integer generatedCount) {
            progressBar.setVisibility(View.GONE);

            Toast.makeText(BarcodeManagementActivity.this,
                    "Generated " + generatedCount + " barcodes successfully!",
                    Toast.LENGTH_LONG).show();

            updateStats();
        }

        private String generateUniqueBarcode(SQLiteDatabase db, String itemName, String groupName) {
            String barcode;
            int attempts = 0;

            do {
                barcode = BarcodeGenerator.generateBarcode(itemName, groupName);
                attempts++;

                if (attempts > 5) {
                    barcode = BarcodeGenerator.generateEAN13StyleBarcode(itemName, groupName);
                }

            } while (barcodeExistsInDb(db, barcode) && attempts < 20);

            if (attempts >= 20) {
                barcode = barcode + (System.currentTimeMillis() % 1000);
            }

            return barcode;
        }

        private boolean barcodeExistsInDb(SQLiteDatabase db, String barcode) {
            Cursor cursor = db.query(
                    DatabaseHelper.TABLE_INVENTORY,
                    new String[]{DatabaseHelper.COL_ID},
                    DatabaseHelper.COL_BARCODE + " = ?",
                    new String[]{barcode},
                    null, null, null
            );

            boolean exists = cursor.getCount() > 0;
            cursor.close();
            return exists;
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
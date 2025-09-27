package com.example.invenzora;

import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.invenzora.DatabaseHelper;

public class DeleteItemActivity extends AppCompatActivity {
    private EditText idEt;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_delete_item);

        dbHelper = new DatabaseHelper(this);

        idEt = findViewById(R.id.et_id);
        Button btnDelete = findViewById(R.id.btn_delete);
        Button btnPreview = findViewById(R.id.btn_preview);

        btnPreview.setOnClickListener(v -> previewItem());
        btnDelete.setOnClickListener(v -> doDelete());
    }

    private void previewItem() {
        String idStr = idEt.getText().toString().trim();
        if (idStr.isEmpty()) {
            Toast.makeText(this, "Enter ID to preview item.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int id = Integer.parseInt(idStr);
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            android.database.Cursor cursor = db.query(
                    DatabaseHelper.TABLE_INVENTORY,
                    null,
                    DatabaseHelper.COL_ID + " = ?",
                    new String[]{String.valueOf(id)},
                    null, null, null
            );

            if (cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_NAME));
                int qty = cursor.getInt(cursor.getColumnIndex(DatabaseHelper.COL_QUANTITY));
                String unit = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_UNIT));
                double price = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COL_PRICE));
                String group = cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_GROUP_NAME));

                String details = String.format("Item to delete:\nName: %s\nQuantity: %d %s\nPrice: $%.2f\nGroup: %s",
                        name, qty, unit, price, group);

                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Confirm Delete")
                        .setMessage(details)
                        .setPositiveButton("Delete", (dialog, which) -> performDelete(id))
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                Toast.makeText(this, "Item with ID " + id + " not found.", Toast.LENGTH_SHORT).show();
            }

            cursor.close();
            db.close();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid ID number.", Toast.LENGTH_SHORT).show();
        }
    }

    private void doDelete() {
        String idStr = idEt.getText().toString().trim();
        if (idStr.isEmpty()) {
            Toast.makeText(this, "Enter ID to delete.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int id = Integer.parseInt(idStr);
            performDelete(id);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid ID number.", Toast.LENGTH_SHORT).show();
        }
    }

    private void performDelete(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rowsAffected = db.delete(
                DatabaseHelper.TABLE_INVENTORY,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(id)}
        );

        db.close();

        if (rowsAffected > 0) {
            Toast.makeText(this, "Item deleted successfully!", Toast.LENGTH_SHORT).show();
            idEt.setText(""); // Clear the ID field
        } else {
            Toast.makeText(this, "Item with ID " + id + " not found.", Toast.LENGTH_SHORT).show();
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
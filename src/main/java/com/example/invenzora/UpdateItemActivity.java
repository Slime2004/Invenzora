package com.example.invenzora;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.invenzora.DatabaseHelper;

public class UpdateItemActivity extends AppCompatActivity {
    private EditText idEt, nameEt, qtyEt, unitEt, priceEt;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_update_item);

        dbHelper = new DatabaseHelper(this);

        idEt = findViewById(R.id.txtId);
        nameEt = findViewById(R.id.txtName);
        qtyEt = findViewById(R.id.txtQuantity);
        unitEt = findViewById(R.id.txtUnit);
        priceEt = findViewById(R.id.txtPrice);
        Button btnUpdate = findViewById(R.id.btnUpdate);
        Button btnLoad = findViewById(R.id.btnLoad);

        btnLoad.setOnClickListener(v -> loadItem());
        btnUpdate.setOnClickListener(v -> doUpdate());
    }

    private void loadItem() {
        String idStr = idEt.getText().toString().trim();
        if (idStr.isEmpty()) {
            Toast.makeText(this, "Enter ID to load item details.", Toast.LENGTH_SHORT).show();
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
                nameEt.setText(cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_NAME)));
                qtyEt.setText(String.valueOf(cursor.getInt(cursor.getColumnIndex(DatabaseHelper.COL_QUANTITY))));
                unitEt.setText(cursor.getString(cursor.getColumnIndex(DatabaseHelper.COL_UNIT)));
                priceEt.setText(String.valueOf(cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COL_PRICE))));
                Toast.makeText(this, "Item loaded successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Item with ID " + id + " not found.", Toast.LENGTH_SHORT).show();
                clearFields();
            }

            cursor.close();
            db.close();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid ID number.", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearFields() {
        nameEt.setText("");
        qtyEt.setText("");
        unitEt.setText("");
        priceEt.setText("");
    }

    private void doUpdate() {
        String idStr = idEt.getText().toString().trim();
        String name = nameEt.getText().toString().trim();
        String qtyStr = qtyEt.getText().toString().trim();
        String unit = unitEt.getText().toString().trim();
        String priceStr = priceEt.getText().toString().trim();

        if (idStr.isEmpty() || name.isEmpty() || qtyStr.isEmpty() || unit.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int id = Integer.parseInt(idStr);
            int qty = Integer.parseInt(qtyStr);
            double price = Double.parseDouble(priceStr);

            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(DatabaseHelper.COL_NAME, name);
            values.put(DatabaseHelper.COL_QUANTITY, qty);
            values.put(DatabaseHelper.COL_UNIT, unit);
            values.put(DatabaseHelper.COL_PRICE, price);

            int rowsAffected = db.update(
                    DatabaseHelper.TABLE_INVENTORY,
                    values,
                    DatabaseHelper.COL_ID + " = ?",
                    new String[]{String.valueOf(id)}
            );

            db.close();

            if (rowsAffected > 0) {
                Toast.makeText(this, "Item updated successfully!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Item with ID " + id + " not found.", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numbers for ID, quantity, and price.", Toast.LENGTH_SHORT).show();
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
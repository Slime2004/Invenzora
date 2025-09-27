package com.example.invenzora;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "inventory.db";
    private static final int DATABASE_VERSION = 4; // Incremented for user system

    // Table names
    public static final String TABLE_INVENTORY = "inventory";
    public static final String TABLE_GROUPS = "groups";
    public static final String TABLE_USERS = "users";

    // Inventory table columns
    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_QUANTITY = "quantity";
    public static final String COL_UNIT = "unit";
    public static final String COL_PRICE = "price";
    public static final String COL_GROUP_NAME = "groupName";
    public static final String COL_BARCODE = "barcode";
    public static final String COL_LOW_STOCK_THRESHOLD = "low_stock_threshold";

    // Groups table columns
    public static final String COL_GROUP_ID = "group_id";
    public static final String COL_GROUP_NAME_FIELD = "group_name";

    // Users table columns
    public static final String COL_USER_ID = "user_id";
    public static final String COL_USERNAME = "username";
    public static final String COL_PASSWORD_HASH = "password_hash";
    public static final String COL_FULL_NAME = "full_name";
    public static final String COL_ROLE = "role";
    public static final String COL_CREATED_AT = "created_at";

    // Create table statements
    private static final String CREATE_INVENTORY_TABLE =
            "CREATE TABLE " + TABLE_INVENTORY + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_NAME + " TEXT NOT NULL, " +
                    COL_QUANTITY + " INTEGER NOT NULL, " +
                    COL_UNIT + " TEXT NOT NULL, " +
                    COL_PRICE + " REAL NOT NULL, " +
                    COL_GROUP_NAME + " TEXT NOT NULL, " +
                    COL_BARCODE + " TEXT UNIQUE, " +
                    COL_LOW_STOCK_THRESHOLD + " INTEGER DEFAULT 10" +
                    ")";

    private static final String CREATE_GROUPS_TABLE =
            "CREATE TABLE " + TABLE_GROUPS + " (" +
                    COL_GROUP_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_GROUP_NAME_FIELD + " TEXT NOT NULL UNIQUE" +
                    ")";

    private static final String CREATE_USERS_TABLE =
            "CREATE TABLE " + TABLE_USERS + " (" +
                    COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_USERNAME + " TEXT NOT NULL UNIQUE, " +
                    COL_PASSWORD_HASH + " TEXT NOT NULL, " +
                    COL_FULL_NAME + " TEXT NOT NULL, " +
                    COL_ROLE + " TEXT NOT NULL CHECK(" + COL_ROLE + " IN ('admin', 'employee')), " +
                    COL_CREATED_AT + " TEXT NOT NULL" +
                    ")";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_INVENTORY_TABLE);
        db.execSQL(CREATE_GROUPS_TABLE);
        db.execSQL(CREATE_USERS_TABLE);

        insertDefaultGroups(db);
        insertDefaultUsers(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Add barcode column to existing table
            db.execSQL("ALTER TABLE " + TABLE_INVENTORY + " ADD COLUMN " + COL_BARCODE + " TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_barcode ON " + TABLE_INVENTORY + "(" + COL_BARCODE + ")");
            generateBarcodesForExistingItems(db);
        }

        if (oldVersion < 3) {
            // Add users table for authentication system
            db.execSQL(CREATE_USERS_TABLE);
            insertDefaultUsers(db);
        }

        if (oldVersion < 4) {
            // Add low stock threshold column
            db.execSQL("ALTER TABLE " + TABLE_INVENTORY + " ADD COLUMN " + COL_LOW_STOCK_THRESHOLD + " INTEGER DEFAULT 10");
        }
    }

    private void insertDefaultGroups(SQLiteDatabase db) {
        String[] defaultGroups = {
                "Electronics", "Food & Beverages", "Clothing",
                "Home & Garden", "Office Supplies", "Tools & Hardware"
        };

        for (String group : defaultGroups) {
            db.execSQL("INSERT INTO " + TABLE_GROUPS + " (" + COL_GROUP_NAME_FIELD + ") VALUES (?)",
                    new String[]{group});
        }
    }

    private void insertDefaultUsers(SQLiteDatabase db) {
        String currentDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        // Create default admin account
        String adminPasswordHash = hashPassword("admin123");
        db.execSQL("INSERT INTO " + TABLE_USERS + " (" +
                        COL_USERNAME + ", " + COL_PASSWORD_HASH + ", " + COL_FULL_NAME + ", " +
                        COL_ROLE + ", " + COL_CREATED_AT + ") VALUES (?, ?, ?, ?, ?)",
                new String[]{"admin", adminPasswordHash, "System Administrator", "admin", currentDateTime});

        // Create default employee account
        String empPasswordHash = hashPassword("emp123");
        db.execSQL("INSERT INTO " + TABLE_USERS + " (" +
                        COL_USERNAME + ", " + COL_PASSWORD_HASH + ", " + COL_FULL_NAME + ", " +
                        COL_ROLE + ", " + COL_CREATED_AT + ") VALUES (?, ?, ?, ?, ?)",
                new String[]{"employee", empPasswordHash, "Store Employee", "employee", currentDateTime});
    }

    // USER MANAGEMENT METHODS

    /**
     * Validate user login credentials
     */
    public User validateLogin(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String passwordHash = hashPassword(password);

        Cursor cursor = db.query(
                TABLE_USERS,
                null,
                COL_USERNAME + " = ? AND " + COL_PASSWORD_HASH + " = ?",
                new String[]{username, passwordHash},
                null, null, null
        );

        User user = null;
        if (cursor.moveToFirst()) {
            user = new User(
                    getColumnValue(cursor, COL_USER_ID, 0),
                    getColumnValue(cursor, COL_USERNAME, ""),
                    getColumnValue(cursor, COL_FULL_NAME, ""),
                    getColumnValue(cursor, COL_ROLE, "employee"),
                    getColumnValue(cursor, COL_CREATED_AT, "")
            );
        }

        cursor.close();
        db.close();
        return user;
    }

    /**
     * Create new user account
     */
    public boolean createUser(String username, String password, String fullName, String role) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            ContentValues values = new ContentValues();
            values.put(COL_USERNAME, username);
            values.put(COL_PASSWORD_HASH, hashPassword(password));
            values.put(COL_FULL_NAME, fullName);
            values.put(COL_ROLE, role);
            values.put(COL_CREATED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

            long result = db.insert(TABLE_USERS, null, values);
            db.close();
            return result != -1;
        } catch (Exception e) {
            db.close();
            return false; // Username already exists or other error
        }
    }

    /**
     * Get user by ID
     */
    public User getUserById(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(
                TABLE_USERS,
                null,
                COL_USER_ID + " = ?",
                new String[]{String.valueOf(userId)},
                null, null, null
        );

        User user = null;
        if (cursor.moveToFirst()) {
            user = new User(
                    getColumnValue(cursor, COL_USER_ID, 0),
                    getColumnValue(cursor, COL_USERNAME, ""),
                    getColumnValue(cursor, COL_FULL_NAME, ""),
                    getColumnValue(cursor, COL_ROLE, "employee"),
                    getColumnValue(cursor, COL_CREATED_AT, "")
            );
        }

        cursor.close();
        db.close();
        return user;
    }

    /**
     * Hash password using SHA-256
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple encoding if SHA-256 not available
            return String.valueOf(password.hashCode());
        }
    }

    // EXISTING INVENTORY METHODS (unchanged)

    /**
     * Generate barcodes for existing items that don't have them during database upgrade
     */
    private void generateBarcodesForExistingItems(SQLiteDatabase db) {
        Cursor cursor = db.query(
                TABLE_INVENTORY,
                new String[]{COL_ID, COL_NAME, COL_GROUP_NAME, COL_BARCODE},
                COL_BARCODE + " IS NULL OR " + COL_BARCODE + " = ''",
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            do {
                int id = getColumnValue(cursor, COL_ID, 0);
                String name = getColumnValue(cursor, COL_NAME, "");
                String group = getColumnValue(cursor, COL_GROUP_NAME, "");

                String barcode = generateUniqueBarcode(db, name, group);

                db.execSQL("UPDATE " + TABLE_INVENTORY + " SET " + COL_BARCODE + " = ? WHERE " + COL_ID + " = ?",
                        new String[]{barcode, String.valueOf(id)});

            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    /**
     * Helper method to safely get column values
     */
    private int getColumnValue(Cursor cursor, String columnName, int defaultValue) {
        try {
            int columnIndex = cursor.getColumnIndexOrThrow(columnName);
            return cursor.getInt(columnIndex);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private String getColumnValue(Cursor cursor, String columnName, String defaultValue) {
        try {
            int columnIndex = cursor.getColumnIndexOrThrow(columnName);
            return cursor.getString(columnIndex);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private double getColumnValue(Cursor cursor, String columnName, double defaultValue) {
        try {
            int columnIndex = cursor.getColumnIndexOrThrow(columnName);
            return cursor.getDouble(columnIndex);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /**
     * Generate a unique barcode that doesn't exist in the database
     */
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
            barcode = barcode + System.currentTimeMillis() % 1000;
        }

        return barcode;
    }

    /**
     * Check if barcode exists in database
     */
    public boolean barcodeExists(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase db = this.getReadableDatabase();
        boolean exists = barcodeExistsInDb(db, barcode);
        db.close();
        return exists;
    }

    /**
     * Helper method to check barcode existence with provided database connection
     */
    private boolean barcodeExistsInDb(SQLiteDatabase db, String barcode) {
        Cursor cursor = db.query(
                TABLE_INVENTORY,
                new String[]{COL_ID},
                COL_BARCODE + " = ?",
                new String[]{barcode},
                null, null, null
        );

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    /**
     * Get item by barcode
     */
    public Cursor getItemByBarcode(String barcode) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_INVENTORY,
                null,
                COL_BARCODE + " = ?",
                new String[]{barcode},
                null, null, null
        );
    }

    /**
     * Get all items without barcodes
     */
    public Cursor getItemsWithoutBarcodes() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_INVENTORY,
                null,
                COL_BARCODE + " IS NULL OR " + COL_BARCODE + " = ''",
                null, null, null,
                COL_ID + " ASC"
        );
    }

    /**
     * Count total items
     */
    public int getTotalItemCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_INVENTORY, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        db.close();
        return count;
    }

    /**
     * Count items with barcodes
     */
    public int getItemsWithBarcodesCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_INVENTORY +
                        " WHERE " + COL_BARCODE + " IS NOT NULL AND " + COL_BARCODE + " != ''",
                null
        );
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        db.close();
        return count;
    }

    /**
     * Get all items that are below their low stock threshold
     */
    public Cursor getLowStockItems() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery(
                "SELECT * FROM " + TABLE_INVENTORY +
                        " WHERE " + COL_QUANTITY + " <= " + COL_LOW_STOCK_THRESHOLD +
                        " ORDER BY (" + COL_LOW_STOCK_THRESHOLD + " - " + COL_QUANTITY + ") DESC, " + COL_NAME + " ASC",
                null
        );
    }

    /**
     * Get count of items that are below their low stock threshold
     */
    public int getLowStockItemsCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_INVENTORY +
                        " WHERE " + COL_QUANTITY + " <= " + COL_LOW_STOCK_THRESHOLD,
                null
        );

        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        db.close();
        return count;
    }

    /**
     * Update low stock threshold for a specific item
     */
    public boolean updateLowStockThreshold(int itemId, int threshold) {
        if (threshold < 0) return false;

        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_LOW_STOCK_THRESHOLD, threshold);

        int rowsAffected = db.update(
                TABLE_INVENTORY,
                values,
                COL_ID + " = ?",
                new String[]{String.valueOf(itemId)}
        );

        db.close();
        return rowsAffected > 0;
    }

    /**
     * Get low stock threshold for a specific item
     */
    public int getLowStockThreshold(int itemId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_INVENTORY,
                new String[]{COL_LOW_STOCK_THRESHOLD},
                COL_ID + " = ?",
                new String[]{String.valueOf(itemId)},
                null, null, null
        );

        int threshold = 10; // Default value
        if (cursor.moveToFirst()) {
            threshold = getColumnValue(cursor, COL_LOW_STOCK_THRESHOLD, 10);
        }

        cursor.close();
        db.close();
        return threshold;
    }
}
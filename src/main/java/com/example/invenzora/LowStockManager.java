package com.example.invenzora;

import android.content.Context;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing low stock notifications and alerts
 */
public class LowStockManager {

    public static class LowStockItem {
        public int id;
        public String name;
        public int currentQuantity;
        public String unit;
        public int threshold;
        public String group;
        public double price;
        public String barcode;
        public int shortageAmount; // How many units short

        public LowStockItem(int id, String name, int currentQuantity, String unit,
                            int threshold, String group, double price, String barcode) {
            this.id = id;
            this.name = name;
            this.currentQuantity = currentQuantity;
            this.unit = unit;
            this.threshold = threshold;
            this.group = group;
            this.price = price;
            this.barcode = barcode;
            this.shortageAmount = Math.max(0, threshold - currentQuantity);
        }

        public boolean isCriticallyLow() {
            return currentQuantity <= (threshold * 0.5); // 50% or less of threshold
        }

        public boolean isOutOfStock() {
            return currentQuantity <= 0;
        }

        public String getStatusText() {
            if (isOutOfStock()) {
                return "⛔ OUT OF STOCK";
            } else if (isCriticallyLow()) {
                return "🔴 CRITICALLY LOW";
            } else {
                return "⚠️ LOW STOCK";
            }
        }

        public String getSummaryText() {
            return String.format("%s - %s (%d/%d %s)",
                    name, getStatusText(), currentQuantity, threshold, unit);
        }
    }

    private DatabaseHelper dbHelper;
    private Context context;

    public LowStockManager(Context context) {
        this.context = context;
        this.dbHelper = new DatabaseHelper(context);
    }

    /**
     * Get all items that are currently below their low stock threshold
     */
    public List<LowStockItem> getLowStockItems() {
        List<LowStockItem> lowStockItems = new ArrayList<>();

        Cursor cursor = dbHelper.getLowStockItems();

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NAME));
                int quantity = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUANTITY));
                String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
                int threshold = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LOW_STOCK_THRESHOLD));
                String group = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GROUP_NAME));
                double price = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRICE));

                // Handle barcode (might be null)
                int barcodeIndex = cursor.getColumnIndex(DatabaseHelper.COL_BARCODE);
                String barcode = (barcodeIndex >= 0) ? cursor.getString(barcodeIndex) : "";
                if (barcode == null) barcode = "";

                LowStockItem item = new LowStockItem(id, name, quantity, unit, threshold, group, price, barcode);
                lowStockItems.add(item);

            } while (cursor.moveToNext());
        }

        cursor.close();
        return lowStockItems;
    }

    /**
     * Get count of items currently below their thresholds
     */
    public int getLowStockCount() {
        return dbHelper.getLowStockItemsCount();
    }

    /**
     * Check if there are any low stock items
     */
    public boolean hasLowStockItems() {
        return getLowStockCount() > 0;
    }

    /**
     * Get items by category (out of stock, critically low, low stock)
     */
    public List<LowStockItem> getOutOfStockItems() {
        List<LowStockItem> allLowStock = getLowStockItems();
        List<LowStockItem> outOfStock = new ArrayList<>();

        for (LowStockItem item : allLowStock) {
            if (item.isOutOfStock()) {
                outOfStock.add(item);
            }
        }

        return outOfStock;
    }

    public List<LowStockItem> getCriticallyLowItems() {
        List<LowStockItem> allLowStock = getLowStockItems();
        List<LowStockItem> criticallyLow = new ArrayList<>();

        for (LowStockItem item : allLowStock) {
            if (item.isCriticallyLow() && !item.isOutOfStock()) {
                criticallyLow.add(item);
            }
        }

        return criticallyLow;
    }

    public List<LowStockItem> getLowStockOnlyItems() {
        List<LowStockItem> allLowStock = getLowStockItems();
        List<LowStockItem> lowOnly = new ArrayList<>();

        for (LowStockItem item : allLowStock) {
            if (!item.isCriticallyLow() && !item.isOutOfStock()) {
                lowOnly.add(item);
            }
        }

        return lowOnly;
    }

    /**
     * Update threshold for a specific item
     */
    public boolean updateItemThreshold(int itemId, int newThreshold) {
        return dbHelper.updateLowStockThreshold(itemId, newThreshold);
    }

    /**
     * Check if specific item is low stock and return its details
     */
    public LowStockItem checkItemStatus(int itemId) {
        // Get item details including threshold
        android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                DatabaseHelper.TABLE_INVENTORY,
                null,
                DatabaseHelper.COL_ID + " = ?",
                new String[]{String.valueOf(itemId)},
                null, null, null
        );

        LowStockItem result = null;
        if (cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NAME));
            int quantity = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_QUANTITY));
            String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_UNIT));
            int threshold = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LOW_STOCK_THRESHOLD));
            String group = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GROUP_NAME));
            double price = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PRICE));

            // Handle barcode
            int barcodeIndex = cursor.getColumnIndex(DatabaseHelper.COL_BARCODE);
            String barcode = (barcodeIndex >= 0) ? cursor.getString(barcodeIndex) : "";
            if (barcode == null) barcode = "";

            result = new LowStockItem(itemId, name, quantity, unit, threshold, group, price, barcode);
        }

        cursor.close();
        db.close();
        return result;
    }

    /**
     * Generate restock suggestions based on low stock items
     */
    public String generateRestockSuggestion(LowStockItem item) {
        int suggestedOrder = item.threshold * 2; // Order double the threshold

        if (item.isOutOfStock()) {
            return String.format("🚨 URGENT: Order %d %s immediately (currently 0/%d)",
                    suggestedOrder, item.unit, item.threshold);
        } else if (item.isCriticallyLow()) {
            return String.format("⚡ HIGH PRIORITY: Order %d %s soon (currently %d/%d)",
                    suggestedOrder, item.unit, item.currentQuantity, item.threshold);
        } else {
            return String.format("📋 REORDER: Consider ordering %d %s (currently %d/%d)",
                    suggestedOrder, item.unit, item.currentQuantity, item.threshold);
        }
    }

    /**
     * Get summary statistics for low stock dashboard
     */
    public String getLowStockSummary() {
        List<LowStockItem> outOfStock = getOutOfStockItems();
        List<LowStockItem> criticallyLow = getCriticallyLowItems();
        List<LowStockItem> lowStock = getLowStockOnlyItems();

        StringBuilder summary = new StringBuilder();
        summary.append("📊 Low Stock Summary:\n\n");

        if (outOfStock.size() > 0) {
            summary.append("⛔ Out of Stock: ").append(outOfStock.size()).append(" items\n");
        }

        if (criticallyLow.size() > 0) {
            summary.append("🔴 Critically Low: ").append(criticallyLow.size()).append(" items\n");
        }

        if (lowStock.size() > 0) {
            summary.append("⚠️ Low Stock: ").append(lowStock.size()).append(" items\n");
        }

        if (outOfStock.size() == 0 && criticallyLow.size() == 0 && lowStock.size() == 0) {
            summary.append("✅ All items are adequately stocked!");
        } else {
            int totalLowStock = outOfStock.size() + criticallyLow.size() + lowStock.size();
            summary.append("\nTotal items needing attention: ").append(totalLowStock);
        }

        return summary.toString();
    }

    /**
     * Check if an item became low stock after a quantity change
     * Useful for triggering notifications after sales
     */
    public boolean didItemBecomeLowStock(int itemId, int previousQuantity, int newQuantity) {
        int threshold = dbHelper.getLowStockThreshold(itemId);

        boolean wasLowStock = previousQuantity <= threshold;
        boolean isNowLowStock = newQuantity <= threshold;

        // Return true if item wasn't low stock before but is now
        return !wasLowStock && isNowLowStock;
    }

    /**
     * Check if an item became out of stock after a quantity change
     */
    public boolean didItemBecomeOutOfStock(int itemId, int previousQuantity, int newQuantity) {
        boolean wasOutOfStock = previousQuantity <= 0;
        boolean isNowOutOfStock = newQuantity <= 0;

        // Return true if item wasn't out of stock before but is now
        return !wasOutOfStock && isNowOutOfStock;
    }

    /**
     * Get notification message for item status change
     */
    public String getStatusChangeNotification(int itemId, String itemName, int previousQuantity, int newQuantity) {
        int threshold = dbHelper.getLowStockThreshold(itemId);

        if (didItemBecomeOutOfStock(itemId, previousQuantity, newQuantity)) {
            return "⛔ OUT OF STOCK: " + itemName + " is now out of stock!";
        } else if (didItemBecomeLowStock(itemId, previousQuantity, newQuantity)) {
            return "⚠️ LOW STOCK: " + itemName + " is now below threshold (" + newQuantity + "/" + threshold + ")";
        } else if (newQuantity <= threshold) {
            // Already low stock, show current status
            LowStockItem item = checkItemStatus(itemId);
            if (item != null) {
                return item.getStatusText() + ": " + itemName + " (" + newQuantity + "/" + threshold + ")";
            }
        }

        return null; // No notification needed
    }

    /**
     * Clean up resources
     */
    public void close() {
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
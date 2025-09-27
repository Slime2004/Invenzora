package com.example.invenzora;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class BarcodeImageGenerator {

    /**
     * Generate a Code 128 compatible barcode
     * Code 128 can handle alphanumeric characters, which your scanner reads well
     */
    public static String generateBarcode(String itemName, String groupName) {
        try {
            // Create a shorter, Code 128 friendly barcode
            // Format: PREFIX + 8 digits
            String prefix = getGroupPrefix(groupName);

            // Get current timestamp (last 4 digits for uniqueness)
            long timestamp = System.currentTimeMillis();
            String timeStr = String.valueOf(timestamp % 10000);

            // Create hash of item name for consistency
            String input = itemName.toLowerCase().trim();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());

            // Convert first 2 bytes to positive integer
            int hashValue = Math.abs(((hashBytes[0] & 0xFF) << 8) | (hashBytes[1] & 0xFF));
            String hashStr = String.format("%04d", hashValue % 10000);

            // Format: 2-char prefix + 4-digit time + 4-digit hash = 10 characters
            return prefix + timeStr + hashStr;

        } catch (NoSuchAlgorithmException e) {
            return generateSimpleBarcode();
        }
    }

    /**
     * Get 2-character prefix based on group name
     */
    private static String getGroupPrefix(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return "GN"; // General
        }

        switch (groupName.toLowerCase()) {
            case "electronics": return "EL";
            case "food & beverages":
            case "food": return "FD";
            case "clothing": return "CL";
            case "home & garden":
            case "home": return "HG";
            case "office supplies":
            case "office": return "OF";
            case "tools & hardware":
            case "tools": return "TL";
            default:
                // Use first two letters of group name
                String clean = groupName.replaceAll("[^A-Za-z]", "").toUpperCase();
                if (clean.length() >= 2) {
                    return clean.substring(0, 2);
                }
                return "GN";
        }
    }

    /**
     * Generate Code 128 compatible barcode with prefix
     * This ensures compatibility with your scanner
     */
    public static String generateBarcodeWithPrefix(String prefix, String itemName) {
        if (prefix == null || prefix.length() > 3) {
            prefix = "INV";
        }

        // Generate 6-digit number based on timestamp and item name
        long timestamp = System.currentTimeMillis();
        int itemHash = Math.abs(itemName.hashCode());

        // Combine timestamp and hash to create 6-digit number
        String number = String.format("%06d", (timestamp + itemHash) % 1000000);

        return prefix.toUpperCase() + number;
    }

    /**
     * Simple fallback that's guaranteed to work with Code 128
     */
    private static String generateSimpleBarcode() {
        long timestamp = System.currentTimeMillis();
        Random random = new Random();

        // Simple format: "BC" + 8 digits
        String digits = String.format("%08d", (timestamp + random.nextInt(1000)) % 100000000);
        return "BC" + digits;
    }

    /**
     * Generate a purely numeric barcode (for UPC compatibility)
     */
    public static String generateNumericBarcode(String itemName, String groupName) {
        try {
            // 12-digit numeric code for UPC-A compatibility
            long timestamp = System.currentTimeMillis();
            int nameHash = Math.abs(itemName.hashCode());
            int groupHash = Math.abs(groupName.hashCode());

            // Combine hashes to create unique number
            long combined = timestamp + nameHash + groupHash;

            // Format as 12-digit string
            return String.format("%012d", Math.abs(combined % 1000000000000L));

        } catch (Exception e) {
            // Fallback
            return String.format("%012d", System.currentTimeMillis() % 1000000000000L);
        }
    }

    /**
     * Validate that a barcode is compatible with Code 128
     */
    public static boolean isValidBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return false;
        }

        // Code 128 supports alphanumeric characters
        // Check if contains only valid Code 128 characters
        return barcode.matches("^[A-Za-z0-9\\s\\-\\.]+$") && barcode.length() >= 6 && barcode.length() <= 15;
    }

    /**
     * Generate barcode optimized for your scanner (Code 128 format)
     */
    public static String generateScannerOptimizedBarcode(String itemName, String groupName) {
        // This creates barcodes that work well with your ZXing scanner
        String prefix = getGroupPrefix(groupName);

        // Create 8-digit unique identifier
        long timestamp = System.currentTimeMillis();
        int itemHash = Math.abs(itemName.hashCode());

        // Use timestamp and hash to create unique 8-digit number
        long uniqueId = (timestamp % 100000000L + itemHash % 100000000L) % 100000000L;
        String digits = String.format("%08d", uniqueId);

        return prefix + digits; // Total: 2 letters + 8 digits = 10 characters
    }
}
package com.example.invenzora;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

public class BarcodeGenerator {

    /**
     * Generates a unique barcode based on item properties
     * Uses a combination of timestamp, item name hash, and random number
     */
    public static String generateBarcode(String itemName, String groupName) {
        try {
            // Get current timestamp (last 6 digits for uniqueness)
            long timestamp = System.currentTimeMillis();
            String timeStr = String.valueOf(timestamp).substring(7); // Last 6 digits

            // Create hash of item name + group for consistency
            String input = (itemName + groupName).toLowerCase().trim();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());

            // Convert first 4 bytes to positive integer
            int hashValue = Math.abs(
                    ((hashBytes[0] & 0xFF) << 24) |
                            ((hashBytes[1] & 0xFF) << 16) |
                            ((hashBytes[2] & 0xFF) << 8) |
                            (hashBytes[3] & 0xFF)
            );

            // Get last 4 digits of hash
            String hashStr = String.format("%04d", hashValue % 10000);

            // Add random 2-digit number for extra uniqueness
            Random random = new Random();
            String randomStr = String.format("%02d", random.nextInt(100));

            // Combine: 6 digits timestamp + 4 digits hash + 2 digits random = 12 digit barcode
            return timeStr + hashStr + randomStr;

        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple timestamp + random if MD5 fails
            return generateSimpleBarcode();
        }
    }

    /**
     * Generate barcode optimized for your scanner (Code 128 format)
     * ADD THIS METHOD to your BarcodeGenerator class
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
     * Alternative method: Generate barcode with custom prefix
     * Format: PREFIX + 8 digit number (useful for different product categories)
     */
    public static String generateBarcodeWithPrefix(String prefix, String itemName) {
        if (prefix == null || prefix.length() > 4) {
            prefix = "INV"; // Default prefix
        }

        // Generate 8-digit number based on timestamp and item name
        long timestamp = System.currentTimeMillis();
        int itemHash = Math.abs(itemName.hashCode());

        // Combine timestamp and hash to create 8-digit number
        String number = String.format("%08d", (timestamp + itemHash) % 100000000);

        return prefix.toUpperCase() + number;
    }

    /**
     * Simple fallback barcode generation
     */
    private static String generateSimpleBarcode() {
        long timestamp = System.currentTimeMillis();
        Random random = new Random();

        // 10 digits from timestamp + 2 random digits
        String timeStr = String.valueOf(timestamp).substring(3, 11); // 8 digits
        String randomStr = String.format("%04d", random.nextInt(10000)); // 4 digits

        return timeStr + randomStr;
    }

    /**
     * Generate EAN-13 compatible barcode (13 digits)
     * Note: This generates the number only, not the actual EAN-13 with check digit calculation
     */
    public static String generateEAN13StyleBarcode(String itemName, String groupName) {
        // Country code (first 3 digits) - using 123 as example
        String countryCode = "123";

        // Manufacturer code (next 4 digits) - based on group
        int groupHash = Math.abs(groupName.hashCode());
        String manufacturerCode = String.format("%04d", groupHash % 10000);

        // Product code (next 5 digits) - based on item name and timestamp
        long timestamp = System.currentTimeMillis();
        int itemHash = Math.abs(itemName.hashCode());
        String productCode = String.format("%05d", (timestamp + itemHash) % 100000);

        // Check digit (last digit) - simplified calculation
        String barcode12 = countryCode + manufacturerCode + productCode;
        int checkDigit = calculateSimpleCheckDigit(barcode12);

        return barcode12 + checkDigit;
    }

    /**
     * Simple check digit calculation (not true EAN-13 algorithm, but sufficient for internal use)
     */
    private static int calculateSimpleCheckDigit(String barcode12) {
        int sum = 0;
        for (int i = 0; i < barcode12.length(); i++) {
            int digit = Character.getNumericValue(barcode12.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    /**
     * Validate that a barcode is compatible with modern scanners
     * Updated to support the new barcode formats we're generating
     */
    public static boolean isValidBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            return false;
        }

        // Remove whitespace for validation
        String cleanBarcode = barcode.trim();

        // Check length - allow wider range for different formats
        if (cleanBarcode.length() < 6 || cleanBarcode.length() > 20) {
            return false;
        }

        // Allow alphanumeric characters (Code 128 compatible)
        // This supports: letters, numbers, hyphens, dots
        if (cleanBarcode.matches("^[A-Za-z0-9\\-\\.]+$")) {
            return true;
        }

        // Also allow purely numeric barcodes (UPC compatible)
        if (cleanBarcode.matches("^\\d+$")) {
            return true;
        }

        // If it doesn't match either pattern, it's invalid
        return false;
    }
}
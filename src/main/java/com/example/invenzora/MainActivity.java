package com.example.invenzora;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private SessionManager sessionManager;
    private TextView welcomeTextView;
    private LinearLayout adminOnlySection, employeeSection;
    private LowStockManager lowStockManager;
    private TextView lowStockAlertTv;
    private Button lowStockDetailsBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        lowStockManager = new LowStockManager(this);

        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        initializeViews();
        setupUserInterface();
        setupClickListeners();
        setupUserMenu();
        setupLowStockNotifications();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initializeViews() {
        welcomeTextView = findViewById(R.id.tv_welcome_user);
        adminOnlySection = findViewById(R.id.ll_admin_only_section);
        employeeSection = findViewById(R.id.ll_employee_section);
        lowStockAlertTv = findViewById(R.id.tv_low_stock_alert);
        lowStockDetailsBtn = findViewById(R.id.btn_low_stock_details);
    }

    private void setupUserInterface() {
        // Set welcome message
        String welcomeMessage = "Welcome, " + sessionManager.getCurrentUserFullName() + "!\n" +
                "Role: " + (sessionManager.isAdmin() ? "Administrator 👑" : "Employee 👤");
        welcomeTextView.setText(welcomeMessage);

        // Show/hide sections based on user role
        if (sessionManager.isAdmin()) {
            adminOnlySection.setVisibility(View.VISIBLE);
            employeeSection.setVisibility(View.VISIBLE);
        } else {
            adminOnlySection.setVisibility(View.GONE);
            employeeSection.setVisibility(View.VISIBLE);
        }
    }

    private void setupClickListeners() {
        // Employee-accessible functions
        Button btnSearch = findViewById(R.id.btnSearch);
        Button btnSell = findViewById(R.id.btnSell);

        btnSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchItemActivity.class)));
        btnSell.setOnClickListener(v -> startActivity(new Intent(this, SellItemActivity.class)));

        // Admin-only functions
        if (sessionManager.isAdmin()) {
            Button btnAdd = findViewById(R.id.btnAdd);
            Button btnUpdate = findViewById(R.id.btnUpdate);
            Button btnDelete = findViewById(R.id.btnDelete);
            Button btnBarcodeManagement = findViewById(R.id.btnBarcodeManagement);
            Button btnBarcodeTroubleshooting = findViewById(R.id.btnBarcodeTroubleshooting);

            btnAdd.setOnClickListener(v -> startActivity(new Intent(this, AddItemActivity.class)));
            btnUpdate.setOnClickListener(v -> startActivity(new Intent(this, UpdateItemActivity.class)));
            btnDelete.setOnClickListener(v -> startActivity(new Intent(this, DeleteItemActivity.class)));
            btnBarcodeManagement.setOnClickListener(v -> startActivity(new Intent(this, BarcodeManagementActivity.class)));
            btnBarcodeTroubleshooting.setOnClickListener(v -> startActivity(new Intent(this, BarcodeTroubleshootingActivity.class)));
        }

        // Help button for employees
        Button btnHelp = findViewById(R.id.btn_help);
        if (btnHelp != null) {
            btnHelp.setOnClickListener(v -> showHelpDialog());
        }
    }

    private void setupUserMenu() {
        Button userMenuBtn = findViewById(R.id.btn_user_menu);
        userMenuBtn.setOnClickListener(v -> showUserMenu());
    }

    private void setupLowStockNotifications() {
        java.util.List<LowStockManager.LowStockItem> lowStockItems = lowStockManager.getLowStockItems();

        if (!lowStockItems.isEmpty()) {
            lowStockAlertTv.setVisibility(View.VISIBLE);
            lowStockDetailsBtn.setVisibility(View.VISIBLE);

            // Count by urgency
            int outOfStockCount = 0;
            int criticalCount = 0;
            StringBuilder itemNames = new StringBuilder();

            for (LowStockManager.LowStockItem item : lowStockItems) {
                if (item.isOutOfStock()) {
                    outOfStockCount++;
                    if (itemNames.length() < 50) { // Limit length for display
                        if (itemNames.length() > 0) itemNames.append(", ");
                        itemNames.append(item.name);
                    }
                } else if (item.isCriticallyLow()) {
                    criticalCount++;
                }
            }

            String alertMessage;
            if (outOfStockCount > 0) {
                alertMessage = "🚨 OUT OF STOCK: " + itemNames.toString();
                if (itemNames.length() >= 50) alertMessage += "...";
                if (lowStockItems.size() > outOfStockCount) {
                    alertMessage += "\n+ " + (lowStockItems.size() - outOfStockCount) + " more items need restocking";
                }
                lowStockAlertTv.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            } else if (criticalCount > 0) {
                alertMessage = "🔴 " + criticalCount + " items critically low!";
                if (lowStockItems.size() > criticalCount) {
                    alertMessage += "\n⚠️ " + (lowStockItems.size() - criticalCount) + " more below threshold";
                }
                lowStockAlertTv.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
            } else {
                // Show first few item names
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < Math.min(3, lowStockItems.size()); i++) {
                    if (i > 0) names.append(", ");
                    names.append(lowStockItems.get(i).name);
                }
                if (lowStockItems.size() > 3) names.append("...");

                alertMessage = "⚠️ Low Stock: " + names.toString();
                lowStockAlertTv.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
            }

            lowStockAlertTv.setText(alertMessage);
            lowStockDetailsBtn.setText("📋 View All (" + lowStockItems.size() + " items)");
            lowStockDetailsBtn.setOnClickListener(v -> showLowStockDetails());

        } else {
            lowStockAlertTv.setVisibility(View.GONE);
            lowStockDetailsBtn.setVisibility(View.GONE);
        }
    }

    private void showLowStockDetails() {
        java.util.List<LowStockManager.LowStockItem> lowStockItems = lowStockManager.getLowStockItems();

        if (lowStockItems.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("✅ All Good!")
                    .setMessage("No items need restocking at this time.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Create detailed list with item names
        StringBuilder details = new StringBuilder();
        details.append("📋 Items That Need Restocking:\n\n");

        // Group by urgency
        java.util.List<LowStockManager.LowStockItem> outOfStock = new java.util.ArrayList<>();
        java.util.List<LowStockManager.LowStockItem> criticallyLow = new java.util.ArrayList<>();
        java.util.List<LowStockManager.LowStockItem> lowStock = new java.util.ArrayList<>();

        for (LowStockManager.LowStockItem item : lowStockItems) {
            if (item.isOutOfStock()) {
                outOfStock.add(item);
            } else if (item.isCriticallyLow()) {
                criticallyLow.add(item);
            } else {
                lowStock.add(item);
            }
        }

        // Show out of stock items first (most urgent)
        if (!outOfStock.isEmpty()) {
            details.append("🚨 URGENT - OUT OF STOCK:\n");
            for (LowStockManager.LowStockItem item : outOfStock) {
                details.append("• ").append(item.name).append("\n");
                details.append("  Group: ").append(item.group).append("\n");
                details.append("  Current: 0 ").append(item.unit).append(" | Alert at: ").append(item.threshold).append("\n");
                details.append("  💡 Reorder: ").append(item.threshold * 2).append(" ").append(item.unit).append("\n\n");
            }
        }

        // Show critically low items
        if (!criticallyLow.isEmpty()) {
            details.append("🔴 CRITICALLY LOW:\n");
            for (LowStockManager.LowStockItem item : criticallyLow) {
                details.append("• ").append(item.name).append("\n");
                details.append("  Group: ").append(item.group).append("\n");
                details.append("  Current: ").append(item.currentQuantity).append(" ").append(item.unit);
                details.append(" | Alert at: ").append(item.threshold).append("\n");
                details.append("  💡 Reorder: ").append(item.threshold * 2).append(" ").append(item.unit).append("\n\n");
            }
        }

        // Show low stock items
        if (!lowStock.isEmpty()) {
            details.append("⚠️ LOW STOCK:\n");
            for (LowStockManager.LowStockItem item : lowStock) {
                details.append("• ").append(item.name).append("\n");
                details.append("  Group: ").append(item.group).append("\n");
                details.append("  Current: ").append(item.currentQuantity).append(" ").append(item.unit);
                details.append(" | Alert at: ").append(item.threshold).append("\n");
                details.append("  💡 Reorder: ").append(item.threshold * 2).append(" ").append(item.unit).append("\n\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("📊 Detailed Restock Report (" + lowStockItems.size() + " items)")
                .setMessage(details.toString())
                .setPositiveButton("View Quick Summary", (dialog, which) -> showQuickSummary(lowStockItems))
                .setNegativeButton("Close", null)
                .show();
    }

    // ADD THIS NEW METHOD after showLowStockDetails():
    private void showQuickSummary(java.util.List<LowStockManager.LowStockItem> items) {
        StringBuilder summary = new StringBuilder();
        summary.append("📝 Quick Reorder List:\n\n");

        for (LowStockManager.LowStockItem item : items) {
            String status = item.isOutOfStock() ? "🚨" : item.isCriticallyLow() ? "🔴" : "⚠️";
            summary.append(status).append(" ").append(item.name);
            summary.append(" - Order ").append(item.threshold * 2).append(" ").append(item.unit).append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("🛒 Shopping List")
                .setMessage(summary.toString())
                .setPositiveButton("Back to Details", (dialog, which) -> showLowStockDetails())
                .setNegativeButton("Close", null)
                .show();
    }

    private void showUserMenu() {
        String[] menuOptions;
        if (sessionManager.isAdmin()) {
            menuOptions = new String[]{"👤 Profile", "🛡️ Admin Panel", "🚪 Logout"};
        } else {
            menuOptions = new String[]{"👤 Profile", "🚪 Logout"};
        }

        new AlertDialog.Builder(this)
                .setTitle("User Menu")
                .setItems(menuOptions, (dialog, which) -> {
                    if (sessionManager.isAdmin()) {
                        switch (which) {
                            case 0: showProfileDialog(); break;
                            case 1: showAdminPanel(); break;
                            case 2: showLogoutConfirmation(); break;
                        }
                    } else {
                        switch (which) {
                            case 0: showProfileDialog(); break;
                            case 1: showLogoutConfirmation(); break;
                        }
                    }
                })
                .show();
    }

    private void showHelpDialog() {
        String helpMessage;

        if (sessionManager.isAdmin()) {
            helpMessage = "👑 Administrator Access\n\n" +
                    "You have full access to all inventory management features:\n\n" +
                    "📦 Inventory Management:\n" +
                    "• Add new items\n" +
                    "• Search and view items\n" +
                    "• Update item details\n" +
                    "• Delete items\n\n" +
                    "💰 Sales Operations:\n" +
                    "• Process sales transactions\n\n" +
                    "📊 Barcode Management:\n" +
                    "• Generate barcodes\n" +
                    "• Manage barcode settings\n" +
                    "• Troubleshoot scanning issues\n\n" +
                    "👥 User Management:\n" +
                    "• Create new user accounts\n" +
                    "• Change user roles and permissions\n" +
                    "• Reset user passwords\n" +
                    "• Delete user accounts";
        } else {
            helpMessage = "👤 Employee Access\n\n" +
                    "Your account has access to essential sales operations:\n\n" +
                    "🔍 Search Items:\n" +
                    "• Find items by name or barcode\n" +
                    "• View item details and stock levels\n" +
                    "• Scan barcodes to search\n" +
                    "• Copy/share barcode information\n\n" +
                    "💰 Sell Items:\n" +
                    "• Process sales transactions\n" +
                    "• Scan barcodes to find items\n" +
                    "• Update stock levels automatically\n\n" +
                    "⚠️ Note: Admin access required for adding, editing, or deleting inventory items.";
        }

        new AlertDialog.Builder(this)
                .setTitle("📱 App Guide")
                .setMessage(helpMessage)
                .setPositiveButton("Got it!", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Create menu items programmatically to avoid R.menu resource issues
        menu.add(0, 1, 1, "👤 Profile");

        if (sessionManager.isAdmin()) {
            menu.add(0, 2, 2, "🛡️ Admin Panel");
        }

        menu.add(0, 3, 3, "🚪 Logout");

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: // Profile
                showProfileDialog();
                return true;
            case 2: // Admin Panel
                if (sessionManager.isAdmin()) {
                    showAdminPanel();
                }
                return true;
            case 3: // Logout
                showLogoutConfirmation();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showProfileDialog() {
        String profileInfo = "👤 User Profile\n\n" +
                "Username: " + sessionManager.getCurrentUsername() + "\n" +
                "Full Name: " + sessionManager.getCurrentUserFullName() + "\n" +
                "Role: " + (sessionManager.isAdmin() ? "Administrator" : "Employee") + "\n" +
                "User ID: " + sessionManager.getCurrentUserId();

        new AlertDialog.Builder(this)
                .setTitle("Profile Information")
                .setMessage(profileInfo)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showAdminPanel() {
        String[] adminOptions = {
                "👥 User Management",
                "📊 System Statistics",
                "📧 Database Tools",
                "⚙️ System Settings"
        };

        new AlertDialog.Builder(this)
                .setTitle("🛡️ Admin Panel")
                .setItems(adminOptions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            // Launch User Management Activity
                            startActivity(new Intent(this, UserManagementActivity.class));
                            break;
                        case 1:
                            showSystemStats();
                            break;
                        case 2:
                            startActivity(new Intent(this, BarcodeManagementActivity.class));
                            break;
                        case 3:
                            showToast("⚙️ System Settings - Coming Soon");
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSystemStats() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);

        int totalItems = dbHelper.getTotalItemCount();
        int itemsWithBarcodes = dbHelper.getItemsWithBarcodesCount();
        int itemsWithoutBarcodes = totalItems - itemsWithBarcodes;

        // Get user statistics
        android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();

        android.database.Cursor totalUsersCursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_USERS, null);
        totalUsersCursor.moveToFirst();
        int totalUsers = totalUsersCursor.getInt(0);
        totalUsersCursor.close();

        android.database.Cursor adminsCursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_USERS +
                " WHERE " + DatabaseHelper.COL_ROLE + " = 'admin'", null);
        adminsCursor.moveToFirst();
        int adminCount = adminsCursor.getInt(0);
        adminsCursor.close();

        int employeeCount = totalUsers - adminCount;

        String stats = "📊 System Statistics\n\n" +
                "📦 INVENTORY:\n" +
                "Total Items: " + totalItems + "\n" +
                "Items with Barcodes: " + itemsWithBarcodes + "\n" +
                "Items without Barcodes: " + itemsWithoutBarcodes + "\n" +
                "Barcode Coverage: " + String.format("%.1f%%",
                totalItems > 0 ? (itemsWithBarcodes * 100.0 / totalItems) : 0) + "\n\n" +
                "👥 USERS:\n" +
                "Total Users: " + totalUsers + "\n" +
                "👑 Administrators: " + adminCount + "\n" +
                "👤 Employees: " + employeeCount + "\n\n" +
                "🔐 SESSION:\n" +
                "Current User: " + sessionManager.getCurrentUserFullName() + "\n" +
                "Active Session: " + (sessionManager.isLoggedIn() ? "✅ Yes" : "❌ No");

        new AlertDialog.Builder(this)
                .setTitle("System Overview")
                .setMessage(stats)
                .setPositiveButton("User Management", (d, w) -> startActivity(new Intent(this, UserManagementActivity.class)))
                .setNegativeButton("Close", null)
                .show();

        db.close();
        dbHelper.close();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout Confirmation")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    showToast("👋 Goodbye, " + sessionManager.getCurrentUserFullName() + "!");
                    sessionManager.logout();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showToast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if session is still valid
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
        }

        setupLowStockNotifications();
    }
}
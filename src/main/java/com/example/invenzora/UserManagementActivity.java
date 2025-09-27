package com.example.invenzora;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserManagementActivity extends AppCompatActivity {
    private DatabaseHelper dbHelper;
    private SessionManager sessionManager;
    private ListView usersListView;
    private SimpleAdapter usersAdapter;
    private List<Map<String, String>> usersData;
    private TextView statsTextView;
    private Button createUserBtn, refreshBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        // Check if user has admin access
        if (!AccessControlUtil.checkAdminAccess(this, sessionManager)) {
            return;
        }

        setContentView(R.layout.activity_user_management);

        dbHelper = new DatabaseHelper(this);
        initializeViews();
        setupClickListeners();
        loadUsers();
        updateStats();

        // Set title
        AccessControlUtil.updateActivityTitle(this, "User Management", sessionManager);
    }

    private void initializeViews() {
        usersListView = findViewById(R.id.lv_users);
        statsTextView = findViewById(R.id.tv_user_stats);
        createUserBtn = findViewById(R.id.btn_create_user);
        refreshBtn = findViewById(R.id.btn_refresh_users);

        usersData = new ArrayList<>();
        usersAdapter = new SimpleAdapter(this, usersData, android.R.layout.simple_list_item_2,
                new String[]{"display_name", "details"},
                new int[]{android.R.id.text1, android.R.id.text2});
        usersListView.setAdapter(usersAdapter);
    }

    private void setupClickListeners() {
        createUserBtn.setOnClickListener(v -> showCreateUserDialog());
        refreshBtn.setOnClickListener(v -> {
            loadUsers();
            updateStats();
            Toast.makeText(this, "✅ User list refreshed", Toast.LENGTH_SHORT).show();
        });

        usersListView.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, String> selectedUser = usersData.get(position);
            showUserOptionsDialog(selectedUser);
        });
    }

    private void loadUsers() {
        usersData.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                DatabaseHelper.TABLE_USERS,
                null, null, null, null, null,
                DatabaseHelper.COL_ROLE + " DESC, " + DatabaseHelper.COL_CREATED_AT + " ASC"
        );

        if (cursor.moveToFirst()) {
            do {
                Map<String, String> userMap = new HashMap<>();

                int userId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_ID));
                String username = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USERNAME));
                String fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FULL_NAME));
                String role = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ROLE));
                String createdAt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT));

                String roleIcon = role.equals("admin") ? "👑" : "👤";
                String displayName = fullName + " " + roleIcon;

                // Check if this is the current user
                if (userId == sessionManager.getCurrentUserId()) {
                    displayName += " (You)";
                }

                String details = String.format("@%s • %s • ID: %d • Created: %s",
                        username, role.toUpperCase(), userId, createdAt.split(" ")[0]);

                userMap.put("display_name", displayName);
                userMap.put("details", details);
                userMap.put("user_id", String.valueOf(userId));
                userMap.put("username", username);
                userMap.put("full_name", fullName);
                userMap.put("role", role);
                userMap.put("is_current_user", String.valueOf(userId == sessionManager.getCurrentUserId()));

                usersData.add(userMap);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        usersAdapter.notifyDataSetChanged();
    }

    private void updateStats() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Count total users
        Cursor totalCursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_USERS, null);
        totalCursor.moveToFirst();
        int totalUsers = totalCursor.getInt(0);
        totalCursor.close();

        // Count admins
        Cursor adminCursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_USERS +
                " WHERE " + DatabaseHelper.COL_ROLE + " = 'admin'", null);
        adminCursor.moveToFirst();
        int adminCount = adminCursor.getInt(0);
        adminCursor.close();

        // Count employees
        int employeeCount = totalUsers - adminCount;

        String statsText = String.format(
                "👥 User Statistics:\n\n" +
                        "Total Users: %d\n" +
                        "👑 Administrators: %d\n" +
                        "👤 Employees: %d\n\n" +
                        "Current User: %s (%s)",
                totalUsers, adminCount, employeeCount,
                sessionManager.getCurrentUserFullName(),
                sessionManager.isAdmin() ? "Administrator" : "Employee"
        );

        statsTextView.setText(statsText);
        db.close();
    }

    private void showUserOptionsDialog(Map<String, String> user) {
        String fullName = user.get("full_name");
        String username = user.get("username");
        String role = user.get("role");
        int userId = Integer.parseInt(user.get("user_id"));
        boolean isCurrentUser = Boolean.parseBoolean(user.get("is_current_user"));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("👤 " + fullName);

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        // Always show view profile
        options.add("📋 View Profile Details");
        actions.add(() -> showUserProfile(user));

        // Change role option (can't change own role)
        if (!isCurrentUser) {
            String newRole = role.equals("admin") ? "employee" : "admin";
            String roleIcon = newRole.equals("admin") ? "👑" : "👤";
            options.add(roleIcon + " Change Role to " + newRole.toUpperCase());
            actions.add(() -> showChangeRoleConfirmation(userId, fullName, newRole));
        }

        // Reset password option
        options.add("🔑 Reset Password");
        actions.add(() -> showResetPasswordDialog(userId, fullName, isCurrentUser));

        // Delete user option (can't delete yourself or if only admin)
        if (!isCurrentUser) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor adminCursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_USERS +
                    " WHERE " + DatabaseHelper.COL_ROLE + " = 'admin'", null);
            adminCursor.moveToFirst();
            int adminCount = adminCursor.getInt(0);
            adminCursor.close();
            db.close();

            // Don't allow deleting the last admin
            if (!(role.equals("admin") && adminCount <= 1)) {
                options.add("🗑️ Delete User");
                actions.add(() -> showDeleteUserConfirmation(userId, fullName, role));
            }
        }

        String[] optionsArray = options.toArray(new String[0]);

        builder.setItems(optionsArray, (dialog, which) -> {
            if (which < actions.size()) {
                actions.get(which).run();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showUserProfile(Map<String, String> user) {
        String profileInfo = String.format(
                "👤 User Profile\n\n" +
                        "Full Name: %s\n" +
                        "Username: %s\n" +
                        "Role: %s %s\n" +
                        "User ID: %s\n" +
                        "Account Type: %s\n\n" +
                        "Status: Active",
                user.get("full_name"),
                user.get("username"),
                user.get("role").equals("admin") ? "👑" : "👤",
                user.get("role").toUpperCase(),
                user.get("user_id"),
                Boolean.parseBoolean(user.get("is_current_user")) ? "Current User (You)" : "Other User"
        );

        new AlertDialog.Builder(this)
                .setTitle("User Details")
                .setMessage(profileInfo)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showChangeRoleConfirmation(int userId, String fullName, String newRole) {
        String currentRole = newRole.equals("admin") ? "employee" : "admin";
        String roleIcon = newRole.equals("admin") ? "👑" : "👤";

        String message = String.format(
                "Change user role?\n\n" +
                        "User: %s\n" +
                        "Current Role: %s\n" +
                        "New Role: %s %s\n\n" +
                        "%s",
                fullName,
                currentRole.toUpperCase(),
                roleIcon, newRole.toUpperCase(),
                newRole.equals("admin") ?
                        "⚠️ This will grant full administrative access!" :
                        "ℹ️ This will limit access to search and sales only."
        );

        new AlertDialog.Builder(this)
                .setTitle("🎭 Change User Role")
                .setMessage(message)
                .setPositiveButton("Change Role", (dialog, which) -> updateUserRole(userId, fullName, newRole))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateUserRole(int userId, String fullName, String newRole) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_ROLE, newRole);

        int rowsAffected = db.update(
                DatabaseHelper.TABLE_USERS,
                values,
                DatabaseHelper.COL_USER_ID + " = ?",
                new String[]{String.valueOf(userId)}
        );

        db.close();

        if (rowsAffected > 0) {
            String roleIcon = newRole.equals("admin") ? "👑" : "👤";
            String successMessage = String.format(
                    "✅ Role updated successfully!\n\n" +
                            "%s is now a %s %s\n\n" +
                            "Updated by: %s",
                    fullName, roleIcon, newRole.toUpperCase(),
                    sessionManager.getCurrentUserFullName()
            );

            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();

            // Refresh the list and stats
            loadUsers();
            updateStats();
        } else {
            Toast.makeText(this, "❌ Failed to update user role", Toast.LENGTH_LONG).show();
        }
    }

    private void showResetPasswordDialog(int userId, String fullName, boolean isCurrentUser) {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_reset_password, null);

        EditText newPasswordEt = dialogView.findViewById(R.id.et_new_password);
        EditText confirmPasswordEt = dialogView.findViewById(R.id.et_confirm_new_password);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("🔑 Reset Password - " + fullName)
                .setView(dialogView)
                .setPositiveButton("Reset Password", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button resetButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            resetButton.setOnClickListener(v -> {
                String newPassword = newPasswordEt.getText().toString().trim();
                String confirmPassword = confirmPasswordEt.getText().toString().trim();

                if (validatePasswordReset(newPassword, confirmPassword)) {
                    resetUserPassword(userId, fullName, newPassword, isCurrentUser);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }

    private boolean validatePasswordReset(String newPassword, String confirmPassword) {
        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all password fields", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (newPassword.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!newPassword.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private void resetUserPassword(int userId, String fullName, String newPassword, boolean isCurrentUser) {
        String message = String.format(
                "Reset password for %s?\n\n" +
                        "%s\n\n" +
                        "This action cannot be undone.",
                fullName,
                isCurrentUser ? "⚠️ This is your own account!" : "The user will need to use the new password to login."
        );

        new AlertDialog.Builder(this)
                .setTitle("🔑 Confirm Password Reset")
                .setMessage(message)
                .setPositiveButton("Reset", (dialog, which) -> performPasswordReset(userId, fullName, newPassword, isCurrentUser))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performPasswordReset(int userId, String fullName, String newPassword, boolean isCurrentUser) {
        // Use the same password hashing method as in DatabaseHelper
        String hashedPassword = hashPassword(newPassword);

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_PASSWORD_HASH, hashedPassword);

        int rowsAffected = db.update(
                DatabaseHelper.TABLE_USERS,
                values,
                DatabaseHelper.COL_USER_ID + " = ?",
                new String[]{String.valueOf(userId)}
        );

        db.close();

        if (rowsAffected > 0) {
            String successMessage = String.format(
                    "✅ Password reset successfully!\n\n" +
                            "User: %s\n" +
                            "Reset by: %s\n\n" +
                            "%s",
                    fullName,
                    sessionManager.getCurrentUserFullName(),
                    isCurrentUser ? "⚠️ You will need to use the new password next time you login." :
                            "The user can now login with the new password."
            );

            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();

            // If user reset their own password, show additional warning
            if (isCurrentUser) {
                new AlertDialog.Builder(this)
                        .setTitle("⚠️ Password Changed")
                        .setMessage("You have changed your own password. Please remember your new password for future logins.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        } else {
            Toast.makeText(this, "❌ Failed to reset password", Toast.LENGTH_LONG).show();
        }
    }

    private void showDeleteUserConfirmation(int userId, String fullName, String role) {
        String roleIcon = role.equals("admin") ? "👑" : "👤";

        String message = String.format(
                "⚠️ PERMANENT DELETE\n\n" +
                        "User: %s %s %s\n" +
                        "Role: %s\n\n" +
                        "This action CANNOT be undone!\n" +
                        "All user data will be permanently removed.\n\n" +
                        "Are you absolutely sure?",
                fullName, roleIcon, role.toUpperCase(),
                role.equals("admin") ? "Administrator (Full Access)" : "Employee (Limited Access)"
        );

        new AlertDialog.Builder(this)
                .setTitle("🗑️ Delete User Account")
                .setMessage(message)
                .setPositiveButton("DELETE", (dialog, which) -> {
                    // Second confirmation for safety
                    new AlertDialog.Builder(this)
                            .setTitle("⚠️ FINAL WARNING")
                            .setMessage("This is your FINAL chance to cancel.\n\nDelete " + fullName + " permanently?")
                            .setPositiveButton("YES, DELETE", (d, w) -> performUserDeletion(userId, fullName))
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performUserDeletion(int userId, String fullName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rowsAffected = db.delete(
                DatabaseHelper.TABLE_USERS,
                DatabaseHelper.COL_USER_ID + " = ?",
                new String[]{String.valueOf(userId)}
        );

        db.close();

        if (rowsAffected > 0) {
            String successMessage = String.format(
                    "✅ User deleted successfully\n\n" +
                            "Deleted: %s\n" +
                            "Deleted by: %s",
                    fullName,
                    sessionManager.getCurrentUserFullName()
            );

            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();

            // Refresh the list and stats
            loadUsers();
            updateStats();
        } else {
            Toast.makeText(this, "❌ Failed to delete user", Toast.LENGTH_LONG).show();
        }
    }

    private void showCreateUserDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_account, null);

        EditText usernameEt = dialogView.findViewById(R.id.et_new_username);
        EditText passwordEt = dialogView.findViewById(R.id.et_new_password);
        EditText confirmPasswordEt = dialogView.findViewById(R.id.et_confirm_password);
        EditText fullNameEt = dialogView.findViewById(R.id.et_full_name);
        Spinner roleSpinner = dialogView.findViewById(R.id.sp_role);

        // Setup role spinner
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"employee", "admin"});
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roleSpinner.setAdapter(roleAdapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("➕ Create New User Account")
                .setView(dialogView)
                .setPositiveButton("Create User", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            createButton.setOnClickListener(v -> {
                String username = usernameEt.getText().toString().trim();
                String password = passwordEt.getText().toString().trim();
                String confirmPassword = confirmPasswordEt.getText().toString().trim();
                String fullName = fullNameEt.getText().toString().trim();
                String role = roleSpinner.getSelectedItem().toString();

                if (validateNewUserAccount(username, password, confirmPassword, fullName)) {
                    boolean created = dbHelper.createUser(username, password, fullName, role);
                    if (created) {
                        String roleIcon = role.equals("admin") ? "👑" : "👤";
                        Toast.makeText(this, "✅ User account created successfully!\n" +
                                roleIcon + " " + fullName + " (" + role.toUpperCase() + ")", Toast.LENGTH_LONG).show();

                        loadUsers();
                        updateStats();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, "❌ Username already exists", Toast.LENGTH_LONG).show();
                    }
                }
            });
        });

        dialog.show();
    }

    private boolean validateNewUserAccount(String username, String password, String confirmPassword, String fullName) {
        if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (username.length() < 3) {
            Toast.makeText(this, "Username must be at least 3 characters", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    // Helper method to hash password (same as DatabaseHelper)
    private String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
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
        } catch (java.security.NoSuchAlgorithmException e) {
            return String.valueOf(password.hashCode());
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
package com.example.invenzora;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "InvenzoraPref";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ID = "current_user_id";
    private static final String KEY_USERNAME = "current_username";
    private static final String KEY_USER_ROLE = "current_user_role";
    private static final String KEY_USER_FULLNAME = "current_user_fullname";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Context context;

    public SessionManager(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Get current user ID
     */
    public int getCurrentUserId() {
        return Integer.parseInt(prefs.getString(KEY_USER_ID, "0"));
    }

    /**
     * Get current username
     */
    public String getCurrentUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    /**
     * Get current user role
     */
    public String getCurrentUserRole() {
        return prefs.getString(KEY_USER_ROLE, "employee");
    }

    /**
     * Get current user full name
     */
    public String getCurrentUserFullName() {
        return prefs.getString(KEY_USER_FULLNAME, "");
    }

    /**
     * Check if current user is admin
     */
    public boolean isAdmin() {
        return "admin".equals(getCurrentUserRole());
    }

    /**
     * Check if current user is employee
     */
    public boolean isEmployee() {
        return "employee".equals(getCurrentUserRole());
    }

    /**
     * Logout user and redirect to login
     */
    public void logout() {
        editor.clear();
        editor.apply();

        // Redirect to login activity
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    /**
     * Check access permission for admin-only features
     */
    public boolean hasAdminAccess() {
        return isLoggedIn() && isAdmin();
    }

    /**
     * Check access permission for employee features (search and sell)
     */
    public boolean hasEmployeeAccess() {
        return isLoggedIn() && (isAdmin() || isEmployee());
    }

    /**
     * Get user info as formatted string
     */
    public String getUserInfo() {
        if (!isLoggedIn()) {
            return "Not logged in";
        }

        return getCurrentUserFullName() + " (" +
                (isAdmin() ? "Administrator" : "Employee") + ")";
    }
}
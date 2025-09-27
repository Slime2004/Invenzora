package com.example.invenzora;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AlertDialog;

public class AccessControlUtil {

    /**
     * Check if user has admin access and show appropriate dialog if not
     * @param activity The activity calling this method
     * @param sessionManager The session manager instance
     * @return true if user has admin access, false otherwise
     */
    public static boolean checkAdminAccess(Activity activity, SessionManager sessionManager) {
        if (!sessionManager.hasAdminAccess()) {
            showAccessDeniedDialog(activity, sessionManager, "administrator");
            return false;
        }
        return true;
    }

    /**
     * Check if user has employee access (search and sell functions)
     * @param activity The activity calling this method
     * @param sessionManager The session manager instance
     * @return true if user has employee access, false otherwise
     */
    public static boolean checkEmployeeAccess(Activity activity, SessionManager sessionManager) {
        if (!sessionManager.hasEmployeeAccess()) {
            showAccessDeniedDialog(activity, sessionManager, "employee");
            return false;
        }
        return true;
    }

    /**
     * Show access denied dialog with appropriate message
     */
    private static void showAccessDeniedDialog(Activity activity, SessionManager sessionManager, String requiredRole) {
        String currentRole = sessionManager.isLoggedIn() ?
                (sessionManager.isAdmin() ? "Administrator" : "Employee") : "Not logged in";

        String message;
        if ("administrator".equals(requiredRole)) {
            message = "🚫 Administrator Access Required\n\n" +
                    "This function is restricted to administrators only.\n\n" +
                    "Your current role: " + currentRole + "\n\n" +
                    "Functions available to you:\n" +
                    "• Search Items\n" +
                    "• Sell Items\n\n" +
                    "Contact your administrator for elevated access.";
        } else {
            message = "🚫 Access Denied\n\n" +
                    "You must be logged in as an employee or administrator to access this function.\n\n" +
                    "Your current status: " + currentRole + "\n\n" +
                    "Please log in to continue.";
        }

        new AlertDialog.Builder(activity)
                .setTitle("Access Denied")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> activity.finish())
                .setCancelable(false)
                .show();
    }

    /**
     * Show a simple toast for quick access checks
     */
    public static void showAccessDeniedToast(Context context, String requiredRole) {
        String message = "🚫 " + requiredRole + " access required";
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show();
    }

    /**
     * Update activity title to show access level
     */
    public static void updateActivityTitle(Activity activity, String baseTitle, SessionManager sessionManager) {
        if (sessionManager.isAdmin()) {
            activity.setTitle(baseTitle + " - Admin Access");
        } else if (sessionManager.isEmployee()) {
            activity.setTitle(baseTitle + " - Employee Access");
        } else {
            activity.setTitle(baseTitle);
        }
    }

    /**
     * Get user info string for display
     */
    public static String getUserInfoString(SessionManager sessionManager) {
        if (!sessionManager.isLoggedIn()) {
            return "Not logged in";
        }

        return sessionManager.getCurrentUserFullName() + " (" +
                (sessionManager.isAdmin() ? "Administrator 👑" : "Employee 👤") + ")";
    }
}
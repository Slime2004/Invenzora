package com.example.invenzora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private EditText usernameEt, passwordEt;
    private CheckBox rememberMeCheckbox;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("InvenzoraPref", MODE_PRIVATE);

        // Check if user is already logged in
        if (prefs.getBoolean("is_logged_in", false)) {
            navigateToMain();
            return;
        }

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        usernameEt = findViewById(R.id.et_username);
        passwordEt = findViewById(R.id.et_password);
        rememberMeCheckbox = findViewById(R.id.cb_remember_me);
        Button loginBtn = findViewById(R.id.btn_login);
        Button createAccountBtn = findViewById(R.id.btn_create_account);

        // Load remembered username if exists
        String rememberedUsername = prefs.getString("remembered_username", "");
        if (!rememberedUsername.isEmpty()) {
            usernameEt.setText(rememberedUsername);
            rememberMeCheckbox.setChecked(true);
            passwordEt.requestFocus();
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.btn_login).setOnClickListener(v -> attemptLogin());
        findViewById(R.id.btn_create_account).setOnClickListener(v -> showCreateAccountDialog());

        // Quick login buttons for demo/testing
        findViewById(R.id.btn_admin_demo).setOnClickListener(v -> quickLogin("admin", "admin123"));
        findViewById(R.id.btn_employee_demo).setOnClickListener(v -> quickLogin("employee", "emp123"));
    }

    private void attemptLogin() {
        String username = usernameEt.getText().toString().trim();
        String password = passwordEt.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate credentials
        User user = dbHelper.validateLogin(username, password);

        if (user != null) {
            loginSuccess(user);
        } else {
            Toast.makeText(this, "❌ Invalid username or password", Toast.LENGTH_LONG).show();
            passwordEt.setText(""); // Clear password on failed attempt
        }
    }

    private void quickLogin(String username, String password) {
        usernameEt.setText(username);
        passwordEt.setText(password);
        attemptLogin();
    }

    private void loginSuccess(User user) {
        // Save login state
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("is_logged_in", true);
        editor.putString("current_user_id", String.valueOf(user.getId()));
        editor.putString("current_username", user.getUsername());
        editor.putString("current_user_role", user.getRole());
        editor.putString("current_user_fullname", user.getFullName());

        // Handle remember me
        if (rememberMeCheckbox.isChecked()) {
            editor.putString("remembered_username", user.getUsername());
        } else {
            editor.remove("remembered_username");
        }

        editor.apply();

        // Show welcome message
        String welcomeMessage = "✅ Welcome " + user.getFullName() + "!\nRole: " +
                (user.getRole().equals("admin") ? "Administrator" : "Employee");
        Toast.makeText(this, welcomeMessage, Toast.LENGTH_LONG).show();

        // Navigate to main activity
        navigateToMain();
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showCreateAccountDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_create_account, null);

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

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("👤 Create New Account")
                .setView(dialogView)
                .setPositiveButton("Create", null) // Set to null initially
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button createButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            createButton.setOnClickListener(v -> {
                String newUsername = usernameEt.getText().toString().trim();
                String newPassword = passwordEt.getText().toString().trim();
                String confirmPassword = confirmPasswordEt.getText().toString().trim();
                String fullName = fullNameEt.getText().toString().trim();
                String role = roleSpinner.getSelectedItem().toString();

                if (validateNewAccount(newUsername, newPassword, confirmPassword, fullName)) {
                    boolean created = dbHelper.createUser(newUsername, newPassword, fullName, role);
                    if (created) {
                        Toast.makeText(LoginActivity.this, "✅ Account created successfully!", Toast.LENGTH_LONG).show();
                        dialog.dismiss();

                        // Auto-fill login form
                        LoginActivity.this.usernameEt.setText(newUsername);
                        LoginActivity.this.passwordEt.setText(newPassword);
                    } else {
                        Toast.makeText(LoginActivity.this, "❌ Username already exists", Toast.LENGTH_LONG).show();
                    }
                }
            });
        });

        dialog.show();
    }

    private boolean validateNewAccount(String username, String password, String confirmPassword, String fullName) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
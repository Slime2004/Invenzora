package com.example.invenzora;

public class User {
    private int id;
    private String username;
    private String fullName;
    private String role; // "admin" or "employee"
    private String createdAt;

    public User() {}

    public User(int id, String username, String fullName, String role, String createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    // Utility methods
    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isEmployee() {
        return "employee".equals(role);
    }

    @Override
    public String toString() {
        return fullName + " (" + (isAdmin() ? "Administrator" : "Employee") + ")";
    }
}
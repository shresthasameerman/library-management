package com.library.model;

public class User {

    private int id;
    private String username;
    private String role;
    private Integer branchId;

    public User(int id, String username, String role, Integer branchId) {
        this.id       = id;
        this.username = username;
        this.role     = role;
        this.branchId = branchId;
    }

    public int    getId()       { return id; }
    public String getUsername() { return username; }
    public String getRole()     { return role; }
    public Integer getBranchId() { return branchId; }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(this.role)
            || "SUPERADMIN".equals(this.role);
    }

    public boolean isLibrarian() {
        return "ADMIN".equals(this.role)
            || "LIBRARIAN".equals(this.role);
    }

    @Override
    public String toString() {
        return username + " (" + role + ")" +
            (branchId != null ? " [branch=" + branchId + "]" : "");
    }
}
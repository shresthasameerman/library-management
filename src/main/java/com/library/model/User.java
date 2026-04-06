package com.library.model;

public class User {

    private int id;
    private String username;
    private String role;
    private Integer branchId;
    private Integer memberRecordId;

    public User(int id, String username, String role, Integer branchId) {
        this(id, username, role, branchId, null);
    }

    public User(int id, String username, String role, Integer branchId, Integer memberRecordId) {
        this.id       = id;
        this.username = username;
        this.role     = role;
        this.branchId = branchId;
        this.memberRecordId = memberRecordId;
    }

    public int    getId()       { return id; }
    public String getUsername() { return username; }
    public String getRole()     { return role; }
    public Integer getBranchId() { return branchId; }
    public Integer getMemberRecordId() { return memberRecordId; }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(this.role)
            || "SUPERADMIN".equals(this.role);
    }

    public boolean isLibrarian() {
        return "ADMIN".equals(this.role)
            || "LIBRARIAN".equals(this.role);
    }

    public boolean isStudent() {
        return "STUDENT".equals(this.role);
    }

    @Override
    public String toString() {
        return username + " (" + role + ")" +
            (branchId != null ? " [branch=" + branchId + "]" : "") +
            (memberRecordId != null ? " [member=" + memberRecordId + "]" : "");
    }
}
package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserAdminService {

    public List<User> getLibrarians() {
        List<User> rows = new ArrayList<>();
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("""
                    SELECT id, username, role, branch_id
                    FROM users
                    WHERE role = 'ADMIN' OR role = 'LIBRARIAN'
                    ORDER BY username ASC
                """);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getObject("branch_id") != null
                        ? rs.getInt("branch_id")
                        : null
                ));
            }
        } catch (SQLException e) {
            System.err.println("Load librarians failed: " + e.getMessage());
        }
        return rows;
    }

    public boolean createLibrarian(String username, String rawPassword, int branchId) {
        return createAdminUser(username, rawPassword, "ADMIN", branchId);
    }

    public boolean createAdminUser(String username, String rawPassword, String role, Integer branchId) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return false;
        }

        String normalizedRole = normalizeRole(role);
        if (normalizedRole == null) {
            return false;
        }

        String hashed = org.mindrot.jbcrypt.BCrypt.hashpw(
            rawPassword,
            org.mindrot.jbcrypt.BCrypt.gensalt()
        );

        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("""
                    INSERT INTO users (username, password_hash, role, branch_id)
                    VALUES (?, ?, ?, ?)
                """);
            stmt.setString(1, username.trim());
            stmt.setString(2, hashed);
            stmt.setString(3, normalizedRole);
            if (branchId == null) {
                stmt.setNull(4, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(4, branchId);
            }
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Create admin user failed: " + e.getMessage());
            return false;
        }
    }

    public boolean usernameExists(String username) {
        if (username == null || username.isBlank()) return false;
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)");
            stmt.setString(1, username.trim());
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean resetAdminPassword(int userId, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }

        String hashed = org.mindrot.jbcrypt.BCrypt.hashpw(
            rawPassword,
            org.mindrot.jbcrypt.BCrypt.gensalt()
        );

        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(
                    "UPDATE users SET password_hash = ? WHERE id = ? AND role IN ('ADMIN','LIBRARIAN')"
                );
            stmt.setString(1, hashed);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Reset password failed: " + e.getMessage());
            return false;
        }
    }

    private String normalizeRole(String role) {
        if (role == null) return null;
        String normalized = role.trim().toUpperCase();
        return ("ADMIN".equals(normalized) || "LIBRARIAN".equals(normalized))
            ? normalized
            : null;
    }

    public boolean reassignLibrarian(int userId, int branchId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET branch_id = ? WHERE id = ? " +
                "AND (role = 'ADMIN' OR role = 'LIBRARIAN')"
            );
            stmt.setInt(1, branchId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Reassign librarian failed: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteLibrarian(int userId) {
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("DELETE FROM users WHERE id = ? " +
                    "AND (role = 'ADMIN' OR role = 'LIBRARIAN')");
            stmt.setInt(1, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete librarian failed: " + e.getMessage());
            return false;
        }
    }
}

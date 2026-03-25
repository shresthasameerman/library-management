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
        String hashed = org.mindrot.jbcrypt.BCrypt.hashpw(
            rawPassword,
            org.mindrot.jbcrypt.BCrypt.gensalt()
        );

        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("""
                    INSERT INTO users (username, password_hash, role, branch_id)
                    VALUES (?, ?, 'ADMIN', ?)
                """);
            stmt.setString(1, username);
            stmt.setString(2, hashed);
            stmt.setInt(3, branchId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Create librarian failed: " + e.getMessage());
            return false;
        }
    }

    public boolean usernameExists(String username) {
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("SELECT COUNT(*) FROM users WHERE username = ?");
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
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

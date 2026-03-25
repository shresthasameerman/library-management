package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.Branch;
import com.library.model.BranchSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BranchService {

    public List<Branch> getAllBranches() {
        List<Branch> branches = new ArrayList<>();
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("""
                    SELECT id, name, department, code
                    FROM branches
                    WHERE active = 1
                    ORDER BY name ASC
                """);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                branches.add(new Branch(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("department"),
                    rs.getString("code")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Load branches failed: " + e.getMessage());
        }
        return branches;
    }

    public boolean addBranch(String name, String department, String code) {
        String sql = "INSERT INTO branches (name, department, code) VALUES (?, ?, ?)";
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(sql);
            stmt.setString(1, name);
            stmt.setString(2, department);
            stmt.setString(3, code);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Add branch failed: " + e.getMessage());
            return false;
        }
    }

    public boolean branchCodeExists(String code) {
        if (code == null || code.isBlank()) return false;
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("SELECT COUNT(*) FROM branches WHERE code = ?");
            stmt.setString(1, code);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public int getBranchBookCopies(int branchId) {
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("SELECT COALESCE(SUM(total_copies), 0) FROM books WHERE branch_id = ?");
            stmt.setInt(1, branchId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public int getBranchMembers(int branchId) {
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("SELECT COUNT(*) FROM members WHERE branch_id = ? AND active = 1");
            stmt.setInt(1, branchId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    public boolean assignUserToBranch(int userId, int branchId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET branch_id = ?, role = 'ADMIN' WHERE id = ?"
            );
            stmt.setInt(1, branchId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Assign user branch failed: " + e.getMessage());
            return false;
        }
    }

    public boolean deactivateBranch(int branchId) {
        // Prevent deactivation while branch still has active operational data.
        if (hasLinkedData(branchId)) return false;
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("UPDATE branches SET active = 0 WHERE id = ?");
            stmt.setInt(1, branchId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Deactivate branch failed: " + e.getMessage());
            return false;
        }
    }

    public List<BranchSummary> getBranchSummaries() {
        List<BranchSummary> rows = new ArrayList<>();
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement("""
                    SELECT
                        b.id,
                        b.name,
                        COALESCE(b.department, '') AS department,
                        COALESCE(b.code, '') AS code,
                        COALESCE((SELECT SUM(total_copies)
                                  FROM books bk
                                  WHERE bk.branch_id = b.id), 0) AS total_books,
                        COALESCE((SELECT COUNT(*)
                                  FROM members m
                                  WHERE m.branch_id = b.id AND m.active = 1), 0) AS total_members,
                        COALESCE((SELECT COUNT(*)
                                  FROM issue_records ir
                                  WHERE ir.branch_id = b.id AND ir.status = 'ISSUED'), 0) AS issued_books,
                        COALESCE((SELECT COUNT(*)
                                  FROM users u
                                  WHERE u.branch_id = b.id
                                  AND (u.role = 'ADMIN' OR u.role = 'LIBRARIAN')), 0) AS librarians
                    FROM branches b
                    WHERE b.active = 1
                    ORDER BY b.name ASC
                """);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new BranchSummary(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("department"),
                    rs.getString("code"),
                    rs.getInt("total_books"),
                    rs.getInt("total_members"),
                    rs.getInt("issued_books"),
                    rs.getInt("librarians")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Load branch summaries failed: " + e.getMessage());
        }
        return rows;
    }

    private boolean hasLinkedData(int branchId) {
        String[] checks = {
            "SELECT COUNT(*) FROM books WHERE branch_id = ?",
            "SELECT COUNT(*) FROM members WHERE branch_id = ? AND active = 1",
            "SELECT COUNT(*) FROM issue_records WHERE branch_id = ? AND status = 'ISSUED'",
            "SELECT COUNT(*) FROM users WHERE branch_id = ? AND (role = 'ADMIN' OR role = 'LIBRARIAN')"
        };
        try {
            Connection conn = DatabaseConnection.getConnection();
            for (String sql : checks) {
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, branchId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) return true;
            }
        } catch (SQLException e) {
            System.err.println("Branch linked-data check failed: " + e.getMessage());
        }
        return false;
    }
}

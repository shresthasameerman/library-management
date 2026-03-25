package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.DashboardStats;
import com.library.util.SessionManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DashboardService {

    public DashboardStats getStats() {
        String branchWhere = SessionManager.isBranchScopedUser()
            ? " WHERE branch_id = " + SessionManager.getCurrentBranchId()
            : "";
        String issueWhere = SessionManager.isBranchScopedUser()
            ? " AND branch_id = " + SessionManager.getCurrentBranchId()
            : "";

        try {
            Connection conn = DatabaseConnection.getConnection();
            Statement stmt = conn.createStatement();

            int totalBooks = count(stmt, "SELECT COUNT(*) FROM books" + branchWhere);
            int totalMembers = count(stmt,
                "SELECT COUNT(*) FROM members WHERE active = 1" +
                    (SessionManager.isBranchScopedUser()
                        ? " AND branch_id = " + SessionManager.getCurrentBranchId()
                        : "")
            );
            int issuedBooks = count(stmt,
                "SELECT COUNT(*) FROM issue_records WHERE status = 'ISSUED'" + issueWhere
            );
            int overdueBooks = count(stmt,
                "SELECT COUNT(*) FROM issue_records " +
                "WHERE status = 'ISSUED' AND due_date < DATE('now')" + issueWhere
            );
            int totalBranches = SessionManager.isSuperAdmin()
                ? count(stmt, "SELECT COUNT(*) FROM branches WHERE active = 1")
                : 1;

            return new DashboardStats(
                totalBooks,
                totalMembers,
                issuedBooks,
                overdueBooks,
                totalBranches
            );
        } catch (SQLException e) {
            System.err.println("Dashboard stats load failed: " + e.getMessage());
            return new DashboardStats(0, 0, 0, 0, 0);
        }
    }

    private int count(Statement stmt, String sql) throws SQLException {
        ResultSet rs = stmt.executeQuery(sql);
        return rs.next() ? rs.getInt(1) : 0;
    }
}

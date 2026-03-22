package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.Member;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MemberService {

    // ── Add New Member ────────────────────────────────────────────────
    public boolean addMember(Member member) {
        String sql = """
            INSERT INTO members
                (name, email, phone, member_id, department, member_type, active)
            VALUES (?, ?, ?, ?, ?, ?, 1)
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, member.getName());
            stmt.setString(2, member.getEmail());
            stmt.setString(3, member.getPhone());
            stmt.setString(4, member.getMemberId());
            stmt.setString(5, member.getDepartment());
            stmt.setString(6, member.getMemberType());
            stmt.executeUpdate();
            System.out.println("✓ Member added: " + member.getName());
            return true;
        } catch (SQLException e) {
            System.err.println("Add member failed: " + e.getMessage());
            return false;
        }
    }

    // ── Update Member ─────────────────────────────────────────────────
    public boolean updateMember(Member member) {
        String sql = """
            UPDATE members
            SET name = ?, email = ?, phone = ?,
                member_id = ?, department = ?, member_type = ?
            WHERE id = ?
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, member.getName());
            stmt.setString(2, member.getEmail());
            stmt.setString(3, member.getPhone());
            stmt.setString(4, member.getMemberId());
            stmt.setString(5, member.getDepartment());
            stmt.setString(6, member.getMemberType());
            stmt.setInt(7, member.getId());
            stmt.executeUpdate();
            System.out.println("✓ Member updated: " + member.getName());
            return true;
        } catch (SQLException e) {
            System.err.println("Update member failed: " + e.getMessage());
            return false;
        }
    }

    // ── Deactivate Member (soft delete) ───────────────────────────────
    public boolean deactivateMember(int memberId) {
        String sql = "UPDATE members SET active = 0 WHERE id = ?";
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, memberId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Deactivate failed: " + e.getMessage());
            return false;
        }
    }

    // ── Reactivate Member ─────────────────────────────────────────────
    public boolean reactivateMember(int memberId) {
        String sql = "UPDATE members SET active = 1 WHERE id = ?";
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, memberId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Reactivate failed: " + e.getMessage());
            return false;
        }
    }

    // ── Permanently Delete Member ─────────────────────────────────────
    public boolean deleteMember(int memberId) {
        String checkSql = """
            SELECT COUNT(*) FROM issue_records WHERE member_id = ?
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement check = conn.prepareStatement(checkSql);
            check.setInt(1, memberId);
            ResultSet rs = check.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) return false;

            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM members WHERE id = ?"
            );
            stmt.setInt(1, memberId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Delete failed: " + e.getMessage());
            return false;
        }
    }

    // ── Get All Members ───────────────────────────────────────────────
    public List<Member> getAllMembers() {
        return searchMembers("", true);
    }

    // ── Search Members ────────────────────────────────────────────────
    public List<Member> searchMembers(String keyword, boolean activeOnly) {
        List<Member> members = new ArrayList<>();
        String sql = """
                SELECT id, name, email, phone, member_id,
                       department, member_type, active
                FROM members
                WHERE (name       LIKE ?
                   OR  email      LIKE ?
                   OR  member_id  LIKE ?
                   OR  department LIKE ?
                   OR  member_type LIKE ?)
            """
            + (activeOnly ? " AND active = 1" : "")
            + " ORDER BY name ASC";
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            String p = "%" + keyword + "%";
            stmt.setString(1, p);
            stmt.setString(2, p);
            stmt.setString(3, p);
            stmt.setString(4, p);
            stmt.setString(5, p);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String type = rs.getString("member_type");
                members.add(new Member(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("member_id"),
                    rs.getString("department"),
                    type != null ? type : "Student",   // ← memberType added
                    rs.getInt("active") == 1
                ));
            }
        } catch (SQLException e) {
            System.err.println("Search failed: " + e.getMessage());
        }
        return members;
    }

    // ── Check Member ID exists ────────────────────────────────────────
    public boolean memberIdExists(String memberId, int excludeId) {
        String sql = "SELECT COUNT(*) FROM members WHERE member_id = ? AND id != ?";
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, memberId);
            stmt.setInt(2, excludeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Check active issues ───────────────────────────────────────────
    public boolean hasActiveIssues(int memberId) {
        String sql = """
            SELECT COUNT(*) FROM issue_records
            WHERE member_id = ? AND status = 'ISSUED'
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, memberId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Auto-generate Member ID ───────────────────────────────────────
    public String generateMemberId() {
        String sql = "SELECT COUNT(*) FROM members";
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(sql);
            if (rs.next()) {
                return String.format("TBC%04d", rs.getInt(1) + 1);
            }
        } catch (SQLException e) {
            System.err.println("ID gen failed: " + e.getMessage());
        }
        return "TBC0001";
    }
}
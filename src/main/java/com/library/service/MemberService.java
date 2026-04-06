package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.Member;
import com.library.util.BranchScope;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemberService {

    private static final Pattern MEMBER_ID_PATTERN = Pattern.compile("^(.*?)(\\d+)$");
    private String lastErrorMessage = "";

    // ── TBC Courses ───────────────────────────────────────────────────
    public static final String[] COURSES = {
        "BBA",
        "BSc (Hons) Computing",
        "BSc Data Science",
        "MBA (Graduate/Executive)",
        "MSc IT",
        "ACCA",
        "Staff"
    };

    // ── Intake Sessions (dynamic, auto-updating) ─────────────────────
    public static final String[] INTAKES = buildIntakes();

    private static String[] buildIntakes() {
        List<String> intakes = new ArrayList<>();

        int startYear = 2021;
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();

        // Past years: include both Jan/Feb and Sept/Oct intakes.
        for (int year = startYear; year < currentYear; year++) {
            intakes.add("Jan/Feb " + year);
            intakes.add("Sept/Oct " + year);
        }

        // Current year: Jan/Feb always visible, Sept/Oct visible from August onward.
        intakes.add("Jan/Feb " + currentYear);
        if (currentMonth >= 8) {
            intakes.add("Sept/Oct " + currentYear);
        }

        // December: show next year's Jan/Feb intake in advance.
        if (currentMonth == 12) {
            intakes.add("Jan/Feb " + (currentYear + 1));
        }

        intakes.add("N/A (Staff)");
        return intakes.toArray(new String[0]);
    }

    // ── Add New Member ────────────────────────────────────────────────
    public boolean addMember(Member member) {
        clearLastError();
        String sql = """
            INSERT INTO members
                (name, email, phone, member_id,
                 department, member_type, intake, active, branch_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
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
            stmt.setString(7, member.getIntake());
            stmt.setObject(8, BranchScope.branchId());
            stmt.executeUpdate();
            System.out.println("✓ Member added: " + member.getName());
            return true;
        } catch (SQLException e) {
            System.err.println("Add member failed: " + e.getMessage());
            setLastError(mapSqlError(e, "Failed to save member."));
            return false;
        }
    }

    // ── Update Member ─────────────────────────────────────────────────
    public boolean updateMember(Member member) {
        clearLastError();
        String sql = """
            UPDATE members
            SET name = ?, email = ?, phone = ?,
                member_id = ?, department = ?,
                member_type = ?, intake = ?
            WHERE id = ?
        """;
        try {
            sql += BranchScope.andClause("branch_id");
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, member.getName());
            stmt.setString(2, member.getEmail());
            stmt.setString(3, member.getPhone());
            stmt.setString(4, member.getMemberId());
            stmt.setString(5, member.getDepartment());
            stmt.setString(6, member.getMemberType());
            stmt.setString(7, member.getIntake());
            stmt.setInt(8, member.getId());
            BranchScope.bind(stmt, 9);
            stmt.executeUpdate();
            System.out.println("✓ Member updated: " + member.getName());
            return true;
        } catch (SQLException e) {
            System.err.println("Update failed: " + e.getMessage());
            setLastError(mapSqlError(e, "Failed to update member."));
            return false;
        }
    }

    // ── Deactivate ────────────────────────────────────────────────────
    public boolean deactivateMember(int memberId) {
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(
                    "UPDATE members SET active = 0 WHERE id = ?" +
                    BranchScope.andClause("branch_id")
                );
            stmt.setInt(1, memberId);
            BranchScope.bind(stmt, 2);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Deactivate failed: " + e.getMessage());
            return false;
        }
    }

    // ── Reactivate ────────────────────────────────────────────────────
    public boolean reactivateMember(int memberId) {
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(
                    "UPDATE members SET active = 1 WHERE id = ?" +
                    BranchScope.andClause("branch_id")
                );
            stmt.setInt(1, memberId);
            BranchScope.bind(stmt, 2);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Reactivate failed: " + e.getMessage());
            return false;
        }
    }

    // ── Permanently Delete Member ─────────────────────────────────────
    public boolean deleteMember(int memberId) {
        try {
            Connection conn = DatabaseConnection.getConnection();

            // Block if member CURRENTLY has books issued
            PreparedStatement check = conn.prepareStatement(
                "SELECT COUNT(*) FROM issue_records " +
                "WHERE member_id = ? AND status = 'ISSUED'" +
                BranchScope.andClause("branch_id")
            );
            check.setInt(1, memberId);
            BranchScope.bind(check, 2);
            ResultSet rs = check.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.err.println("Cannot delete — member has active issues.");
                return false;
            }

            // Delete their RETURNED issue history first
            // (removes FK constraint block)
            PreparedStatement deleteHistory = conn.prepareStatement(
                "DELETE FROM issue_records WHERE member_id = ?" +
                BranchScope.andClause("branch_id")
            );
            deleteHistory.setInt(1, memberId);
            BranchScope.bind(deleteHistory, 2);
            deleteHistory.executeUpdate();

            // Now safely delete the member
            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM members WHERE id = ?" +
                BranchScope.andClause("branch_id")
            );
            stmt.setInt(1, memberId);
            BranchScope.bind(stmt, 2);
            stmt.executeUpdate();
            System.out.println("✓ Member deleted: ID " + memberId);
            return true;

        } catch (SQLException e) {
            System.err.println("Delete failed: " + e.getMessage());
            return false;
        }
    }

    // ── Search Members ────────────────────────────────────────────────
    public List<Member> searchMembers(String keyword, boolean activeOnly) {
        return searchMembersFiltered(keyword, "All", "All", activeOnly);
    }

    // ── Search with Course + Intake Filter ───────────────────────────
    public List<Member> searchMembersFiltered(
            String keyword, String course,
            String intake, boolean activeOnly) {

        List<Member> members = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
            SELECT id, name, email, phone, member_id,
                   department, member_type, intake, active
            FROM members
             WHERE (name        LIKE ?
               OR  email       LIKE ?
               OR  member_id   LIKE ?
               OR  department  LIKE ?
               OR  intake      LIKE ?
               OR  member_type LIKE ?)
        """);

         if (BranchScope.isScoped()) sql.append(" AND branch_id = ?");

        if (activeOnly)            sql.append(" AND active = 1");
        if (!"All".equals(course)) sql.append(" AND department = ?");
        if (!"All".equals(intake)) sql.append(" AND intake = ?");

        sql.append(" ORDER BY name ASC");

        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql.toString());
            String p = "%" + keyword + "%";
            stmt.setString(1, p);
            stmt.setString(2, p);
            stmt.setString(3, p);
            stmt.setString(4, p);
            stmt.setString(5, p);
            stmt.setString(6, p);

            int idx = 7;
            if (BranchScope.isScoped()) idx = BranchScope.bind(stmt, idx);
            if (!"All".equals(course)) stmt.setString(idx++, course);
            if (!"All".equals(intake)) stmt.setString(idx,   intake);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String type      = rs.getString("member_type");
                String intakeVal = rs.getString("intake");
                members.add(new Member(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("member_id"),
                    rs.getString("department"),
                    type      != null ? type      : "Student",
                    intakeVal != null ? intakeVal : "",
                    rs.getInt("active") == 1
                ));
            }
        } catch (SQLException e) {
            System.err.println("Search failed: " + e.getMessage());
        }
        return members;
    }

    // ── Get All Members ───────────────────────────────────────────────
    public List<Member> getAllMembers() {
        return searchMembers("", false);
    }

    // ── Check Member ID exists ────────────────────────────────────────
    public boolean memberIdExists(String memberId, int excludeId) {
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(
                    "SELECT COUNT(*) FROM members " +
                    "WHERE member_id = ? AND id != ?"
                );
            stmt.setString(1, memberId);
            stmt.setInt(2, excludeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public Member getMemberByMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return null;
        }

        String sql = """
            SELECT id, name, email, phone, member_id,
                   department, member_type, intake, active
            FROM members
            WHERE LOWER(member_id) = LOWER(?)
            LIMIT 1
        """;

        try {
            PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql);
            stmt.setString(1, memberId.trim());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String type = rs.getString("member_type");
                String intakeVal = rs.getString("intake");
                return new Member(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("member_id"),
                    rs.getString("department"),
                    type != null ? type : "Student",
                    intakeVal != null ? intakeVal : "",
                    rs.getInt("active") == 1
                );
            }
        } catch (SQLException e) {
            System.err.println("Lookup member by member_id failed: " + e.getMessage());
        }

        return null;
    }

    public Member getMemberById(int memberId) {
        String sql = """
            SELECT id, name, email, phone, member_id,
                   department, member_type, intake, active
            FROM members
            WHERE id = ?
            LIMIT 1
        """;

        try {
            PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql);
            stmt.setInt(1, memberId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String type = rs.getString("member_type");
                String intakeVal = rs.getString("intake");
                return new Member(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("member_id"),
                    rs.getString("department"),
                    type != null ? type : "Student",
                    intakeVal != null ? intakeVal : "",
                    rs.getInt("active") == 1
                );
            }
        } catch (SQLException e) {
            System.err.println("Lookup member by id failed: " + e.getMessage());
        }

        return null;
    }

    // ── Has active issues ─────────────────────────────────────────────
    public boolean hasActiveIssues(int memberId) {
        try {
            String sql = "SELECT COUNT(*) FROM issue_records WHERE member_id = ? " +
                "AND status = 'ISSUED'" + BranchScope.andClause("branch_id");
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(sql);
            stmt.setInt(1, memberId);
            BranchScope.bind(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Auto-generate Member ID ───────────────────────────────────────
    public String generateMemberId() {
        String prefix = getCurrentBranchMemberPrefix();
        int next = 1;
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT member_id FROM members WHERE member_id LIKE ?"
            )) {
                stmt.setString(1, prefix + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String memberId = rs.getString("member_id");
                        Matcher matcher = MEMBER_ID_PATTERN.matcher(memberId == null ? "" : memberId);
                        if (matcher.matches() && prefix.equalsIgnoreCase(matcher.group(1))) {
                            int seq = Integer.parseInt(matcher.group(2));
                            if (seq >= next) {
                                next = seq + 1;
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("ID gen failed: " + e.getMessage());
        }
        return String.format("%s%04d", prefix, next);
    }

    public String getCurrentBranchMemberPrefix() {
        if (!BranchScope.isScoped() || BranchScope.branchId() == null) {
            return "TBC";
        }

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT code FROM branches WHERE id = ?"
            )) {
                stmt.setInt(1, BranchScope.branchId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String code = rs.getString("code");
                        return prefixFromBranchCode(code);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Branch prefix lookup failed: " + e.getMessage());
        }

        return "TBC";
    }

    public String getLastErrorMessage() {
        return (lastErrorMessage == null || lastErrorMessage.isBlank())
            ? "Failed to save. Try again."
            : lastErrorMessage;
    }

    private void clearLastError() {
        lastErrorMessage = "";
    }

    private void setLastError(String message) {
        lastErrorMessage = message;
    }

    private String mapSqlError(SQLException e, String fallback) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (msg.contains("unique") && msg.contains("members.member_id")) {
            return "Member ID already exists. Use a different ID.";
        }
        if (msg.contains("not null") && msg.contains("member_id")) {
            return "Member ID is required.";
        }
        return fallback;
    }

    private String prefixFromBranchCode(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return "TBC";
        }

        String normalized = branchCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || "MAIN".equals(normalized)) {
            return "TBC";
        }

        if (normalized.length() >= 4) {
            return normalized.substring(0, 4);
        }

        return normalized;
    }
}
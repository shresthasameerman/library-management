package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.IssueRecord;
import com.library.util.BranchScope;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class IssueService {

    // Fine per day in Rupees
    private static final double FINE_PER_DAY = 5.0;

    // Default loan period in days
    private static final int LOAN_DAYS = 14;

    // ── Issue a Book ──────────────────────────────────────────────────
    public boolean issueBook(int bookId, int memberId) {
        // Backward-compatible entry point: pick the first available copy.
        Connection conn = DatabaseConnection.getConnection();
        try {
            PreparedStatement pick = conn.prepareStatement("""
                SELECT id, accession_number
                FROM book_copies
                WHERE book_id = ? AND status = 'AVAILABLE'
                %s
                ORDER BY accession_number ASC
                LIMIT 1
            """.formatted(BranchScope.isScoped() ? "AND branch_id = ?" : ""));
            pick.setInt(1, bookId);
            BranchScope.bind(pick, 2);
            ResultSet rs = pick.executeQuery();
            if (!rs.next()) {
                System.err.println("No available copy found.");
                return false;
            }
            return issueBook(
                bookId,
                memberId,
                rs.getInt("id"),
                rs.getString("accession_number")
            );
        } catch (SQLException e) {
            System.err.println("Issue failed: " + e.getMessage());
            return false;
        }
    }

    public boolean issueBook(int bookId, int memberId,
                             int bookCopyId, String accessionNumber) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            // ── Check book is available ───────────────────────────────
            PreparedStatement check = conn.prepareStatement(
                "SELECT available_copies, branch_id FROM books WHERE id = ?" +
                BranchScope.andClause("branch_id")
            );
            check.setInt(1, bookId);
            BranchScope.bind(check, 2);
            ResultSet rs = check.executeQuery();
            if (!rs.next() || rs.getInt(1) <= 0) {
                System.err.println("No copies available.");
                return false;
            }
            Integer branchId = (Integer) rs.getObject("branch_id");

            // ── Check member doesn't already have this book ───────────
            PreparedStatement dupCheck = conn.prepareStatement("""
                SELECT COUNT(*) FROM issue_records
                WHERE book_id = ? AND member_id = ? AND status = 'ISSUED'
                %s
            """.formatted(BranchScope.isScoped() ? "AND branch_id = ?" : ""));
            dupCheck.setInt(1, bookId);
            dupCheck.setInt(2, memberId);
            BranchScope.bind(dupCheck, 3);
            ResultSet dupRs = dupCheck.executeQuery();
            if (dupRs.next() && dupRs.getInt(1) > 0) {
                System.err.println("Member already has this book.");
                return false;
            }

            LocalDate today   = LocalDate.now();
            LocalDate dueDate = today.plusDays(LOAN_DAYS);

            // ── Insert issue record ───────────────────────────────────
            PreparedStatement insert = conn.prepareStatement("""
                INSERT INTO issue_records
                    (book_id, book_copy_id, member_id, branch_id, accession_number,
                     issue_date, due_date, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ISSUED')
            """);
            insert.setInt(1, bookId);
            insert.setInt(2, bookCopyId);
            insert.setInt(3, memberId);
            insert.setObject(4, branchId != null ? branchId : BranchScope.branchId());
            insert.setString(5, accessionNumber);
            insert.setString(6, today.toString());
            insert.setString(7, dueDate.toString());
            insert.executeUpdate();

            // Mark selected copy issued
            PreparedStatement updateCopy = conn.prepareStatement(
                "UPDATE book_copies SET status = 'ISSUED' WHERE id = ?" +
                BranchScope.andClause("branch_id")
            );
            updateCopy.setInt(1, bookCopyId);
            BranchScope.bind(updateCopy, 2);
            updateCopy.executeUpdate();

            // ── Decrease available copies ─────────────────────────────
            PreparedStatement updateBook = conn.prepareStatement(
                "UPDATE books SET available_copies = available_copies - 1 WHERE id = ?" +
                BranchScope.andClause("branch_id")
            );
            updateBook.setInt(1, bookId);
            BranchScope.bind(updateBook, 2);
            updateBook.executeUpdate();

            System.out.println("✓ Book issued. Due: " + dueDate);
            return true;

        } catch (SQLException e) {
            System.err.println("Issue failed: " + e.getMessage());
            return false;
        }
    }

    // ── Return a Book ─────────────────────────────────────────────────
    public double returnBook(int issueId) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            // Get issue record
            PreparedStatement get = conn.prepareStatement("""
                SELECT book_id, book_copy_id, due_date, branch_id FROM issue_records
                WHERE id = ? AND status = 'ISSUED'
                %s
            """.formatted(BranchScope.isScoped() ? "AND branch_id = ?" : ""));
            get.setInt(1, issueId);
            BranchScope.bind(get, 2);
            ResultSet rs = get.executeQuery();

            if (!rs.next()) {
                System.err.println("Issue record not found.");
                return -1;
            }

            int       bookId   = rs.getInt("book_id");
            int       copyId   = rs.getInt("book_copy_id");
            LocalDate dueDate  = LocalDate.parse(rs.getString("due_date"));
            LocalDate today    = LocalDate.now();
            Integer branchId   = (Integer) rs.getObject("branch_id");

            // ── Calculate fine ────────────────────────────────────────
            double fine = 0;
            if (today.isAfter(dueDate)) {
                long daysLate = ChronoUnit.DAYS.between(dueDate, today);
                fine = daysLate * FINE_PER_DAY;
            }

            // ── Update issue record ───────────────────────────────────
            PreparedStatement update = conn.prepareStatement("""
                UPDATE issue_records
                SET return_date = ?, fine_amount = ?, status = 'RETURNED'
                WHERE id = ?
                %s
            """.formatted(BranchScope.isScoped() ? "AND branch_id = ?" : ""));
            update.setString(1, today.toString());
            update.setDouble(2, fine);
            update.setInt(3, issueId);
            BranchScope.bind(update, 4);
            update.executeUpdate();

            if (copyId > 0) {
                PreparedStatement updateCopy = conn.prepareStatement(
                    "UPDATE book_copies SET status = 'AVAILABLE' WHERE id = ?" +
                    (BranchScope.isScoped() ? " AND branch_id = ?" : "")
                );
                updateCopy.setInt(1, copyId);
                if (BranchScope.isScoped()) {
                    updateCopy.setObject(2, branchId != null ? branchId : BranchScope.branchId());
                }
                updateCopy.executeUpdate();
            }

            // ── Increase available copies ─────────────────────────────
            PreparedStatement updateBook = conn.prepareStatement(
                "UPDATE books SET available_copies = available_copies + 1 WHERE id = ?" +
                (BranchScope.isScoped() ? " AND branch_id = ?" : "")
            );
            updateBook.setInt(1, bookId);
            if (BranchScope.isScoped()) {
                updateBook.setObject(2, branchId != null ? branchId : BranchScope.branchId());
            }
            updateBook.executeUpdate();

            System.out.println("✓ Book returned. Fine: Rs." + fine);
            return fine;

        } catch (SQLException e) {
            System.err.println("Return failed: " + e.getMessage());
            return -1;
        }
    }

    // ── Renew an issued book (extend due date) ───────────────────────
    public boolean renewIssue(int issueId, int extendDays) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            PreparedStatement stmt = conn.prepareStatement("""
                UPDATE issue_records
                                SET due_date = DATE('now', ?),
                    status = 'ISSUED'
                WHERE id = ?
                  AND status IN ('ISSUED', 'OVERDUE')
                                    AND due_date <= DATE('now')
                %s
            """.formatted(BranchScope.isScoped() ? "AND branch_id = ?" : ""));
            stmt.setString(1, "+" + extendDays + " day");
            stmt.setInt(2, issueId);
            BranchScope.bind(stmt, 3);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Renew failed: " + e.getMessage());
            return false;
        }
    }

    // ── Get All Currently Issued Records ──────────────────────────────
    public List<IssueRecord> getIssuedBooks(String keyword) {
        return getRecords(keyword, "ISSUED");
    }

    // ── Get All Returned Records ──────────────────────────────────────
    public List<IssueRecord> getReturnedBooks(String keyword) {
        return getRecords(keyword, "RETURNED");
    }

    public List<IssueRecord> getIssuedBooksByMember(int memberId) {
        return getRecordsByMember(memberId, "ISSUED");
    }

    public List<IssueRecord> getIssuedBooksByAccession(String accessionNumber) {
        List<IssueRecord> records = new ArrayList<>();
        String sql = """
            SELECT ir.id, ir.book_id, ir.member_id,
                   b.title     AS book_title,
                   m.name      AS member_name,
                   m.member_id AS member_code,
                   ir.accession_number,
                   ir.issue_date, ir.due_date,
                   ir.return_date, ir.fine_amount, ir.status
            FROM issue_records ir
            JOIN books   b ON ir.book_id   = b.id
            JOIN members m ON ir.member_id = m.id
            WHERE ir.status = 'ISSUED'
            AND ir.accession_number LIKE ?
            %s
            ORDER BY ir.issue_date DESC
        """;
        sql = sql.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");

        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(sql);
            stmt.setString(1, "%" + accessionNumber + "%");
            BranchScope.bind(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) records.add(mapRecord(rs));
        } catch (SQLException e) {
            System.err.println("getIssuedBooksByAccession failed: " + e.getMessage());
        }
        return records;
    }

    public List<IssueRecord> getReturnedBooksByMember(int memberId) {
        return getRecordsByMember(memberId, "RETURNED");
    }

    // ── Get Overdue Records ───────────────────────────────────────────
    public List<IssueRecord> getOverdueBooks() {
        List<IssueRecord> records = new ArrayList<>();
        String sql = """
            SELECT ir.id, ir.book_id, ir.member_id,
                   b.title  AS book_title,
                   m.name   AS member_name,
                   m.member_id AS member_code,
                     ir.accession_number,
                   ir.issue_date, ir.due_date,
                   ir.return_date, ir.fine_amount, ir.status
            FROM issue_records ir
            JOIN books   b ON ir.book_id   = b.id
            JOIN members m ON ir.member_id = m.id
            WHERE ir.status = 'ISSUED'
            AND   ir.due_date < DATE('now')
            %s
            ORDER BY ir.due_date ASC
        """;
        sql = sql.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(sql);
            BranchScope.bind(stmt, 1);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) records.add(mapRecord(rs));
        } catch (SQLException e) {
            System.err.println("Overdue query failed: " + e.getMessage());
        }
        return records;
    }

    // ── Calculate current fine for a record ──────────────────────────
    public double calculateCurrentFine(String dueDateStr) {
        try {
            LocalDate dueDate = LocalDate.parse(dueDateStr);
            LocalDate today   = LocalDate.now();
            if (today.isAfter(dueDate)) {
                long days = ChronoUnit.DAYS.between(dueDate, today);
                return days * FINE_PER_DAY;
            }
        } catch (Exception e) {
            System.err.println("Fine calc error: " + e.getMessage());
        }
        return 0;
    }

    // ── Private: generic search ───────────────────────────────────────
    private List<IssueRecord> getRecords(String keyword, String status) {
        List<IssueRecord> records = new ArrayList<>();
        String sql = """
            SELECT ir.id, ir.book_id, ir.member_id,
                   b.title     AS book_title,
                   m.name      AS member_name,
                   m.member_id AS member_code,
                     ir.accession_number,
                   ir.issue_date, ir.due_date,
                   ir.return_date, ir.fine_amount, ir.status
            FROM issue_records ir
            JOIN books   b ON ir.book_id   = b.id
            JOIN members m ON ir.member_id = m.id
            WHERE ir.status = ?
            AND  (b.title   LIKE ?
               OR m.name    LIKE ?
               OR m.member_id LIKE ?)
            %s
            ORDER BY ir.issue_date DESC
        """;
        sql = sql.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                                     .prepareStatement(sql);
            String p = "%" + keyword + "%";
            stmt.setString(1, status);
            stmt.setString(2, p);
            stmt.setString(3, p);
            stmt.setString(4, p);
            BranchScope.bind(stmt, 5);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) records.add(mapRecord(rs));
        } catch (SQLException e) {
            System.err.println("getRecords failed: " + e.getMessage());
        }
        return records;
    }

    private List<IssueRecord> getRecordsByMember(int memberId, String status) {
        List<IssueRecord> records = new ArrayList<>();
        String sql = """
            SELECT ir.id, ir.book_id, ir.member_id,
                   b.title     AS book_title,
                   m.name      AS member_name,
                   m.member_id AS member_code,
                   ir.accession_number,
                   ir.issue_date, ir.due_date,
                   ir.return_date, ir.fine_amount, ir.status
            FROM issue_records ir
            JOIN books   b ON ir.book_id   = b.id
            JOIN members m ON ir.member_id = m.id
            WHERE ir.member_id = ?
            AND ir.status = ?
            %s
            ORDER BY ir.issue_date DESC
        """;
        sql = sql.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");

        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(sql);
            stmt.setInt(1, memberId);
            stmt.setString(2, status);
            BranchScope.bind(stmt, 3);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                records.add(mapRecord(rs));
            }
        } catch (SQLException e) {
            System.err.println("getRecordsByMember failed: " + e.getMessage());
        }

        return records;
    }

    // ── Map ResultSet → IssueRecord ───────────────────────────────────
    private IssueRecord mapRecord(ResultSet rs) throws SQLException {
        return new IssueRecord(
            rs.getInt("id"),
            rs.getInt("book_id"),
            rs.getInt("member_id"),
            rs.getString("book_title"),
            rs.getString("member_name"),
            rs.getString("member_code"),
            rs.getString("accession_number"),
            rs.getString("issue_date"),
            rs.getString("due_date"),
            rs.getString("return_date"),
            rs.getDouble("fine_amount"),
            rs.getString("status")
        );
    }

    public int getLoanDays() { return LOAN_DAYS; }
    public double getFinePerDay() { return FINE_PER_DAY; }
}
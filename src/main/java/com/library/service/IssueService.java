package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.IssueRecord;

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
                ORDER BY accession_number ASC
                LIMIT 1
            """);
            pick.setInt(1, bookId);
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
                "SELECT available_copies FROM books WHERE id = ?"
            );
            check.setInt(1, bookId);
            ResultSet rs = check.executeQuery();
            if (!rs.next() || rs.getInt(1) <= 0) {
                System.err.println("No copies available.");
                return false;
            }

            // ── Check member doesn't already have this book ───────────
            PreparedStatement dupCheck = conn.prepareStatement("""
                SELECT COUNT(*) FROM issue_records
                WHERE book_id = ? AND member_id = ? AND status = 'ISSUED'
            """);
            dupCheck.setInt(1, bookId);
            dupCheck.setInt(2, memberId);
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
                    (book_id, book_copy_id, member_id, accession_number,
                     issue_date, due_date, status)
                VALUES (?, ?, ?, ?, ?, ?, 'ISSUED')
            """);
            insert.setInt(1, bookId);
            insert.setInt(2, bookCopyId);
            insert.setInt(3, memberId);
            insert.setString(4, accessionNumber);
            insert.setString(5, today.toString());
            insert.setString(6, dueDate.toString());
            insert.executeUpdate();

            // Mark selected copy issued
            PreparedStatement updateCopy = conn.prepareStatement(
                "UPDATE book_copies SET status = 'ISSUED' WHERE id = ?"
            );
            updateCopy.setInt(1, bookCopyId);
            updateCopy.executeUpdate();

            // ── Decrease available copies ─────────────────────────────
            PreparedStatement updateBook = conn.prepareStatement(
                "UPDATE books SET available_copies = available_copies - 1 WHERE id = ?"
            );
            updateBook.setInt(1, bookId);
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
                SELECT book_id, book_copy_id, due_date FROM issue_records
                WHERE id = ? AND status = 'ISSUED'
            """);
            get.setInt(1, issueId);
            ResultSet rs = get.executeQuery();

            if (!rs.next()) {
                System.err.println("Issue record not found.");
                return -1;
            }

            int       bookId   = rs.getInt("book_id");
            int       copyId   = rs.getInt("book_copy_id");
            LocalDate dueDate  = LocalDate.parse(rs.getString("due_date"));
            LocalDate today    = LocalDate.now();

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
            """);
            update.setString(1, today.toString());
            update.setDouble(2, fine);
            update.setInt(3, issueId);
            update.executeUpdate();

            if (copyId > 0) {
                PreparedStatement updateCopy = conn.prepareStatement(
                    "UPDATE book_copies SET status = 'AVAILABLE' WHERE id = ?"
                );
                updateCopy.setInt(1, copyId);
                updateCopy.executeUpdate();
            }

            // ── Increase available copies ─────────────────────────────
            PreparedStatement updateBook = conn.prepareStatement(
                "UPDATE books SET available_copies = available_copies + 1 WHERE id = ?"
            );
            updateBook.setInt(1, bookId);
            updateBook.executeUpdate();

            System.out.println("✓ Book returned. Fine: Rs." + fine);
            return fine;

        } catch (SQLException e) {
            System.err.println("Return failed: " + e.getMessage());
            return -1;
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

    // ── Get Overdue Records ───────────────────────────────────────────
    public List<IssueRecord> getOverdueBooks() {
        List<IssueRecord> records = new ArrayList<>();
        String sql = """
            SELECT ir.id, ir.book_id, ir.member_id,
                   b.title  AS book_title,
                   m.name   AS member_name,
                   m.member_id AS member_code,
                   ir.issue_date, ir.due_date,
                   ir.return_date, ir.fine_amount, ir.status
            FROM issue_records ir
            JOIN books   b ON ir.book_id   = b.id
            JOIN members m ON ir.member_id = m.id
            WHERE ir.status = 'ISSUED'
            AND   ir.due_date < DATE('now')
            ORDER BY ir.due_date ASC
        """;
        try {
            ResultSet rs = DatabaseConnection.getConnection()
                           .createStatement().executeQuery(sql);
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
                   ir.issue_date, ir.due_date,
                   ir.return_date, ir.fine_amount, ir.status
            FROM issue_records ir
            JOIN books   b ON ir.book_id   = b.id
            JOIN members m ON ir.member_id = m.id
            WHERE ir.status = ?
            AND  (b.title   LIKE ?
               OR m.name    LIKE ?
               OR m.member_id LIKE ?)
            ORDER BY ir.issue_date DESC
        """;
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                                     .prepareStatement(sql);
            String p = "%" + keyword + "%";
            stmt.setString(1, status);
            stmt.setString(2, p);
            stmt.setString(3, p);
            stmt.setString(4, p);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) records.add(mapRecord(rs));
        } catch (SQLException e) {
            System.err.println("getRecords failed: " + e.getMessage());
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
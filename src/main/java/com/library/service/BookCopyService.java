package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.BookCopy;
import com.library.model.BookCopyDetail;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookCopyService {

    // ── Add a single copy ─────────────────────────────────────────────
    public boolean addCopy(int bookId, String accessionNumber) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO book_copies (book_id, accession_number) " +
                "VALUES (?, ?)"
            );
            stmt.setInt(1, bookId);
            stmt.setString(2, accessionNumber);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Add copy failed: " + e.getMessage());
            return false;
        }
    }

    // ── Add multiple copies at once ───────────────────────────────────
    public boolean addCopies(int bookId, List<String> accessionNumbers) {
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO book_copies (book_id, accession_number) " +
                "VALUES (?, ?)"
            );
            for (String acc : accessionNumbers) {
                stmt.setInt(1, bookId);
                stmt.setString(2, acc.trim());
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);

            // Update total_copies count in books table
            updateBookCopyCount(bookId, conn);
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); conn.setAutoCommit(true); }
            catch (SQLException ignored) {}
            System.err.println("Add copies failed: " + e.getMessage());
            return false;
        }
    }

    // ── Delete a copy ─────────────────────────────────────────────────
    public boolean deleteCopy(int copyId) {
        try {
            Connection conn = DatabaseConnection.getConnection();

            // Check not issued
            PreparedStatement check = conn.prepareStatement(
                "SELECT status FROM book_copies WHERE id = ?"
            );
            check.setInt(1, copyId);
            ResultSet rs = check.executeQuery();
            if (rs.next() && "ISSUED".equals(rs.getString("status"))) {
                System.err.println("Cannot delete — copy is issued.");
                return false;
            }

            // Get book_id before deleting
            PreparedStatement getBook = conn.prepareStatement(
                "SELECT book_id FROM book_copies WHERE id = ?"
            );
            getBook.setInt(1, copyId);
            rs = getBook.executeQuery();
            int bookId = rs.next() ? rs.getInt("book_id") : -1;

            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM book_copies WHERE id = ?"
            );
            stmt.setInt(1, copyId);
            stmt.executeUpdate();

            if (bookId > 0) updateBookCopyCount(bookId, conn);
            return true;
        } catch (SQLException e) {
            System.err.println("Delete copy failed: " + e.getMessage());
            return false;
        }
    }

    // ── Get all copies for a book ─────────────────────────────────────
    public List<BookCopy> getCopiesForBook(int bookId) {
        List<BookCopy> copies = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, book_id, accession_number, status " +
                "FROM book_copies WHERE book_id = ? " +
                "ORDER BY accession_number ASC"
            );
            stmt.setInt(1, bookId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                copies.add(new BookCopy(
                    rs.getInt("id"),
                    rs.getInt("book_id"),
                    rs.getString("accession_number"),
                    rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Get copies failed: " + e.getMessage());
        }
        return copies;
    }

    // ── Get detailed copy sheet for a book ───────────────────────────
    public List<BookCopyDetail> getCopyDetailsForBook(int bookId) {
        List<BookCopyDetail> rows = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT bc.accession_number,
                       bc.status,
                       m.name AS issued_to,
                       ir.due_date
                FROM book_copies bc
                LEFT JOIN issue_records ir
                    ON ir.book_copy_id = bc.id
                   AND ir.status = 'ISSUED'
                LEFT JOIN members m
                    ON m.id = ir.member_id
                WHERE bc.book_id = ?
                ORDER BY bc.accession_number ASC
            """);
            stmt.setInt(1, bookId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String issuedTo = rs.getString("issued_to");
                String dueDate = rs.getString("due_date");

                rows.add(new BookCopyDetail(
                    rs.getString("accession_number"),
                    rs.getString("status"),
                    issuedTo != null ? issuedTo : "-",
                    dueDate != null ? dueDate : "-"
                ));
            }
        } catch (SQLException e) {
            System.err.println("Get copy detail sheet failed: " + e.getMessage());
        }
        return rows;
    }

    // ── Get available copies for a book ───────────────────────────────
    public List<BookCopy> getAvailableCopies(int bookId) {
        List<BookCopy> copies = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, book_id, accession_number, status " +
                "FROM book_copies " +
                "WHERE book_id = ? AND status = 'AVAILABLE' " +
                "ORDER BY accession_number ASC"
            );
            stmt.setInt(1, bookId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                copies.add(new BookCopy(
                    rs.getInt("id"),
                    rs.getInt("book_id"),
                    rs.getString("accession_number"),
                    rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Get available copies failed: " + e.getMessage());
        }
        return copies;
    }

    // ── Check if accession number exists ──────────────────────────────
    public boolean accessionExists(String accessionNumber, int excludeCopyId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM book_copies " +
                "WHERE accession_number = ? AND id != ?"
            );
            stmt.setString(1, accessionNumber);
            stmt.setInt(2, excludeCopyId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Mark copy as issued ───────────────────────────────────────────
    public boolean markIssued(int copyId) {
        return updateStatus(copyId, "ISSUED");
    }

    // ── Mark copy as available ────────────────────────────────────────
    public boolean markAvailable(int copyId) {
        return updateStatus(copyId, "AVAILABLE");
    }

    // ── Get copy by accession number ──────────────────────────────────
    public BookCopy getCopyByAccession(String accessionNumber) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, book_id, accession_number, status " +
                "FROM book_copies WHERE accession_number = ?"
            );
            stmt.setString(1, accessionNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new BookCopy(
                    rs.getInt("id"),
                    rs.getInt("book_id"),
                    rs.getString("accession_number"),
                    rs.getString("status")
                );
            }
        } catch (SQLException e) {
            System.err.println("Get copy failed: " + e.getMessage());
        }
        return null;
    }

    // ── Private: update copy status ───────────────────────────────────
    private boolean updateStatus(int copyId, String status) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE book_copies SET status = ? WHERE id = ?"
            );
            stmt.setString(1, status);
            stmt.setInt(2, copyId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Status update failed: " + e.getMessage());
            return false;
        }
    }

    // ── Private: sync total_copies in books table ─────────────────────
    private void updateBookCopyCount(int bookId, Connection conn)
            throws SQLException {
        // Total copies
        PreparedStatement total = conn.prepareStatement(
            "SELECT COUNT(*) FROM book_copies WHERE book_id = ?"
        );
        total.setInt(1, bookId);
        ResultSet rs = total.executeQuery();
        int totalCount = rs.next() ? rs.getInt(1) : 0;

        // Available copies
        PreparedStatement avail = conn.prepareStatement(
            "SELECT COUNT(*) FROM book_copies " +
            "WHERE book_id = ? AND status = 'AVAILABLE'"
        );
        avail.setInt(1, bookId);
        rs = avail.executeQuery();
        int availCount = rs.next() ? rs.getInt(1) : 0;

        // Update books table
        PreparedStatement update = conn.prepareStatement(
            "UPDATE books SET total_copies = ?, available_copies = ? " +
            "WHERE id = ?"
        );
        update.setInt(1, totalCount);
        update.setInt(2, availCount);
        update.setInt(3, bookId);
        update.executeUpdate();
    }
}
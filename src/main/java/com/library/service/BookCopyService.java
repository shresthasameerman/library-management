package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.BookCopy;
import com.library.model.BookCopyDetail;
import com.library.util.BranchScope;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BookCopyService {

    private String lastErrorMessage = "";

    // ── Add a single copy ─────────────────────────────────────────────
    public boolean addCopy(int bookId, String accessionNumber) {
        clearLastError();
        try {
            Connection conn = DatabaseConnection.getConnection();
            Integer branchId = resolveBookBranchId(conn, bookId);
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO book_copies (book_id, accession_number, spine_level, branch_id) " +
                "VALUES (?, ?, ?, ?)"
            );
            stmt.setInt(1, bookId);
            stmt.setString(2, accessionNumber);
            stmt.setString(3, "");
            stmt.setObject(4, branchId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Add copy failed: " + e.getMessage());
            setLastError(mapSqlError(e, "Could not add copy."));
            return false;
        }
    }

    // ── Add multiple copies at once ───────────────────────────────────
    public boolean addCopies(int bookId, List<String> accessionNumbers) {
        List<String> emptySpineLevels = new ArrayList<>();
        for (int i = 0; i < accessionNumbers.size(); i++) {
            emptySpineLevels.add("");
        }
        return addCopies(bookId, accessionNumbers, emptySpineLevels);
    }

    public boolean addCopies(int bookId, List<String> accessionNumbers,
                             List<String> spineLevels) {
        clearLastError();
        Connection conn = DatabaseConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            Integer branchId = resolveBookBranchId(conn, bookId);
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO book_copies (book_id, accession_number, spine_level, branch_id) " +
                "VALUES (?, ?, ?, ?)"
            );
            for (int i = 0; i < accessionNumbers.size(); i++) {
                String acc = accessionNumbers.get(i);
                String spineLevel = (spineLevels != null && i < spineLevels.size())
                    ? spineLevels.get(i)
                    : "";
                stmt.setInt(1, bookId);
                stmt.setString(2, acc.trim());
                stmt.setString(3, spineLevel == null ? "" : spineLevel.trim());
                stmt.setObject(4, branchId);
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
            setLastError(mapSqlError(e, "Could not save copy records."));
            return false;
        }
    }

    // ── Delete a copy ─────────────────────────────────────────────────
    public boolean deleteCopy(int copyId) {
        try {
            Connection conn = DatabaseConnection.getConnection();

            // Check not issued
            PreparedStatement check = conn.prepareStatement(
                "SELECT status FROM book_copies WHERE id = ?" +
                BranchScope.andClause("branch_id")
            );
            check.setInt(1, copyId);
            BranchScope.bind(check, 2);
            ResultSet rs = check.executeQuery();
            if (rs.next() && "ISSUED".equals(rs.getString("status"))) {
                System.err.println("Cannot delete — copy is issued.");
                return false;
            }

            // Get book_id before deleting
            PreparedStatement getBook = conn.prepareStatement(
                "SELECT book_id FROM book_copies WHERE id = ?" +
                BranchScope.andClause("branch_id")
            );
            getBook.setInt(1, copyId);
            BranchScope.bind(getBook, 2);
            rs = getBook.executeQuery();
            int bookId = rs.next() ? rs.getInt("book_id") : -1;

            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM book_copies WHERE id = ?" +
                BranchScope.andClause("branch_id")
            );
            stmt.setInt(1, copyId);
            BranchScope.bind(stmt, 2);
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
                "SELECT id, book_id, accession_number, spine_level, status " +
                "FROM book_copies WHERE book_id = ? " +
                (BranchScope.isScoped() ? "AND branch_id = ? " : "") +
                "ORDER BY accession_number ASC"
            );
            stmt.setInt(1, bookId);
            BranchScope.bind(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                copies.add(new BookCopy(
                    rs.getInt("id"),
                    rs.getInt("book_id"),
                    rs.getString("accession_number"),
                    rs.getString("spine_level"),
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
            ensureCopyRowsForBook(bookId, conn);
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT bc.accession_number,
                      bc.spine_level,
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
                %s
                ORDER BY bc.accession_number ASC
            """.formatted(BranchScope.isScoped() ? "AND bc.branch_id = ?" : ""));
            stmt.setInt(1, bookId);
            BranchScope.bind(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String issuedTo = rs.getString("issued_to");
                String dueDate = rs.getString("due_date");

                rows.add(new BookCopyDetail(
                    rs.getString("accession_number"),
                    rs.getString("spine_level"),
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
                "SELECT id, book_id, accession_number, spine_level, status " +
                "FROM book_copies " +
                "WHERE book_id = ? AND status = 'AVAILABLE' " +
                (BranchScope.isScoped() ? "AND branch_id = ? " : "") +
                "ORDER BY accession_number ASC"
            );
            stmt.setInt(1, bookId);
            BranchScope.bind(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                copies.add(new BookCopy(
                    rs.getInt("id"),
                    rs.getInt("book_id"),
                    rs.getString("accession_number"),
                    rs.getString("spine_level"),
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

    public String getLastErrorMessage() {
        return (lastErrorMessage == null || lastErrorMessage.isBlank())
            ? "Could not save copy records."
            : lastErrorMessage;
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
                "SELECT id, book_id, accession_number, spine_level, status " +
                "FROM book_copies WHERE accession_number = ?" +
                BranchScope.andClause("branch_id")
            );
            stmt.setString(1, accessionNumber);
            BranchScope.bind(stmt, 2);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new BookCopy(
                    rs.getInt("id"),
                    rs.getInt("book_id"),
                    rs.getString("accession_number"),
                    rs.getString("spine_level"),
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
                "UPDATE book_copies SET status = ? WHERE id = ?" +
                BranchScope.andClause("branch_id")
            );
            stmt.setString(1, status);
            stmt.setInt(2, copyId);
            BranchScope.bind(stmt, 3);
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

    private Integer resolveBookBranchId(Connection conn, int bookId)
            throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT branch_id FROM books WHERE id = ?"
        );
        stmt.setInt(1, bookId);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) return (Integer) rs.getObject("branch_id");
        return BranchScope.branchId();
    }

    private void ensureCopyRowsForBook(int bookId, Connection conn)
            throws SQLException {
        int existingCopies = 0;
        try (PreparedStatement countStmt = conn.prepareStatement(
            "SELECT COUNT(*) AS cnt FROM book_copies WHERE book_id = ?"
        )) {
            countStmt.setInt(1, bookId);
            try (ResultSet rs = countStmt.executeQuery()) {
                if (rs.next()) existingCopies = rs.getInt("cnt");
            }
        }
        if (existingCopies > 0) return;

        String baseAccession = "";
        int totalCopies = 0;
        Integer branchId = null;
        try (PreparedStatement bookStmt = conn.prepareStatement(
            "SELECT accession_number, total_copies, branch_id FROM books WHERE id = ?"
        )) {
            bookStmt.setInt(1, bookId);
            try (ResultSet rs = bookStmt.executeQuery()) {
                if (!rs.next()) return;
                baseAccession = rs.getString("accession_number");
                totalCopies = Math.max(1, rs.getInt("total_copies"));
                branchId = (Integer) rs.getObject("branch_id");
            }
        }

        List<String> accessions = buildLegacyAccessions(baseAccession, totalCopies);

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement insertStmt = conn.prepareStatement(
            "INSERT INTO book_copies (book_id, accession_number, spine_level, branch_id, status) VALUES (?, ?, ?, ?, 'AVAILABLE')"
        )) {
            for (String accession : accessions) {
                String candidate = accession;
                int suffix = 1;
                while (accessionExists(candidate, 0)) {
                    candidate = accession + "-" + suffix++;
                }
                insertStmt.setInt(1, bookId);
                insertStmt.setString(2, candidate);
                insertStmt.setString(3, "");
                insertStmt.setObject(4, branchId);
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
            updateBookCopyCount(bookId, conn);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private List<String> buildLegacyAccessions(String baseAccession, int totalCopies) {
        List<String> rows = new ArrayList<>();
        String base = (baseAccession == null || baseAccession.isBlank()) ? "ACC" : baseAccession.trim();
        String digits = base.replaceAll("[^0-9]", "");
        String prefix = base.replaceAll("[0-9]", "");

        if (!digits.isBlank()) {
            int width = digits.length();
            int start;
            try {
                start = Integer.parseInt(digits);
            } catch (NumberFormatException ex) {
                start = 1;
            }
            for (int i = 0; i < totalCopies; i++) {
                rows.add(prefix + String.format(Locale.ROOT, "%0" + width + "d", start + i));
            }
            return rows;
        }

        if (totalCopies == 1) {
            rows.add(base);
            return rows;
        }

        for (int i = 1; i <= totalCopies; i++) {
            rows.add(base + "-" + i);
        }
        return rows;
    }

    private void clearLastError() {
        lastErrorMessage = "";
    }

    private void setLastError(String message) {
        lastErrorMessage = message;
    }

    private String mapSqlError(SQLException e, String fallback) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (msg.contains("unique") && msg.contains("book_copies.accession_number")) {
            return "Accession number already exists. Use a unique accession sequence for this branch.";
        }
        return fallback;
    }
}
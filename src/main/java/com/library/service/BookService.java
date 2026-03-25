package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.Book;
import com.library.util.BranchScope;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookService {

    // ── Add Book ──────────────────────────────────────────────────────
    public boolean addBook(Book book) {
        String sql = """
            INSERT INTO books (
                title, author, isbn, category,
                total_copies, available_copies,
                accession_number, classification_number,
                cutter_number, edition, publisher,
                place_of_publication, year_of_publication,
                number_of_pages
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();
            String scopedSql = sql.replace(") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ", branch_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            PreparedStatement stmt = conn.prepareStatement(scopedSql);
            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getAuthor());
            stmt.setString(3, book.getIsbn());
            stmt.setString(4, book.getCategory());
            stmt.setInt(5, book.getTotalCopies());
            stmt.setInt(6, book.getTotalCopies());
            stmt.setString(7, book.getAccessionNumber());
            stmt.setString(8, book.getClassificationNumber());
            stmt.setString(9, book.getCutterNumber());
            stmt.setString(10, book.getEdition());
            stmt.setString(11, book.getPublisher());
            stmt.setString(12, book.getPlaceOfPublication());
            stmt.setInt(13, book.getYearOfPublication());
            stmt.setInt(14, book.getNumberOfPages());
            stmt.setObject(15, BranchScope.branchId());
            stmt.executeUpdate();
            System.out.println("✓ Book added: " + book.getTitle());
            return true;
        } catch (SQLException e) {
            System.err.println("Add book failed: " + e.getMessage());
            return false;
        }
    }

    // ── Update Book ───────────────────────────────────────────────────
    public boolean updateBook(Book book) {
        String sql = """
            UPDATE books SET
                title = ?, author = ?, isbn = ?,
                category = ?, total_copies = ?,
                accession_number = ?, classification_number = ?,
                cutter_number = ?, edition = ?, publisher = ?,
                place_of_publication = ?, year_of_publication = ?,
                number_of_pages = ?
            WHERE id = ?
        """;
        try {
            sql += BranchScope.andClause("branch_id");
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getAuthor());
            stmt.setString(3, book.getIsbn());
            stmt.setString(4, book.getCategory());
            stmt.setInt(5, book.getTotalCopies());
            stmt.setString(6, book.getAccessionNumber());
            stmt.setString(7, book.getClassificationNumber());
            stmt.setString(8, book.getCutterNumber());
            stmt.setString(9, book.getEdition());
            stmt.setString(10, book.getPublisher());
            stmt.setString(11, book.getPlaceOfPublication());
            stmt.setInt(12, book.getYearOfPublication());
            stmt.setInt(13, book.getNumberOfPages());
            stmt.setInt(14, book.getId());
            BranchScope.bind(stmt, 15);
            stmt.executeUpdate();
            System.out.println("✓ Book updated: " + book.getTitle());
            return true;
        } catch (SQLException e) {
            System.err.println("Update book failed: " + e.getMessage());
            return false;
        }
    }

    // ── Delete Book ───────────────────────────────────────────────────
    public boolean deleteBook(int bookId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement check = conn.prepareStatement("""
                SELECT COUNT(*) FROM issue_records
                WHERE book_id = ? AND status = 'ISSUED'
            """);
            check.setInt(1, bookId);
            ResultSet rs = check.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) return false;

            String deleteSql = "DELETE FROM books WHERE id = ?" +
                BranchScope.andClause("branch_id");
            PreparedStatement stmt = conn.prepareStatement(deleteSql);
            stmt.setInt(1, bookId);
            BranchScope.bind(stmt, 2);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Delete failed: " + e.getMessage());
            return false;
        }
    }

    // ── Get All Books ─────────────────────────────────────────────────
    public List<Book> getAllBooks() {
        return searchBooks("");
    }

    // ── Search Books (with range-based accession search) ──────────────
    public List<Book> searchBooks(String keyword) {
        List<Book> books = new ArrayList<>();

        String sql = """
            SELECT id, title, author, isbn, category,
                   total_copies, available_copies,
                   accession_number, classification_number,
                   cutter_number, edition, publisher,
                   place_of_publication, year_of_publication,
                   number_of_pages
            FROM books
            WHERE (
                    title                 LIKE ?
                OR  author                LIKE ?
                OR  isbn                  LIKE ?
                OR  category              LIKE ?
                OR  accession_number      LIKE ?
                OR  classification_number LIKE ?
                OR  publisher             LIKE ?
            )
            %s
            ORDER BY title ASC
        """;
        String branchFilter = BranchScope.isScoped() ? "AND branch_id = ?" : "";
        sql = sql.formatted(branchFilter);

        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            String p = "%" + keyword + "%";
            for (int i = 1; i <= 7; i++) stmt.setString(i, p);
            BranchScope.bind(stmt, 8);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) books.add(mapBook(rs));

            // ── Range-based accession search ──────────────────────────
            // If keyword looks numeric, check if it falls
            // within any book's accession range
            if (keyword != null && !keyword.isBlank()) {
                try {
                    String cleanKeyword = keyword.trim()
                        .replaceAll("[^0-9]", "");
                    if (!cleanKeyword.isEmpty()) {
                        int searchNum = Integer.parseInt(cleanKeyword);

                        String rangeSql = """
                            SELECT id, title, author, isbn, category,
                                   total_copies, available_copies,
                                   accession_number, classification_number,
                                   cutter_number, edition, publisher,
                                   place_of_publication, year_of_publication,
                                   number_of_pages
                            FROM books
                            WHERE accession_number IS NOT NULL
                            AND   accession_number != ''
                            %s
                        """;

                        String rangeBranchFilter = BranchScope.isScoped()
                            ? "AND branch_id = ?"
                            : "";
                        rangeSql = rangeSql.formatted(rangeBranchFilter);

                        PreparedStatement rangeStmt =
                            conn.prepareStatement(rangeSql);
                        BranchScope.bind(rangeStmt, 1);
                        ResultSet rangeRs = rangeStmt.executeQuery();

                        while (rangeRs.next()) {
                            int bookDbId = rangeRs.getInt("id");

                            // Skip if already found in text search
                            final int fId = bookDbId;
                            boolean alreadyFound = books.stream()
                                .anyMatch(b -> b.getId() == fId);
                            if (alreadyFound) continue;

                            String accStr = rangeRs.getString(
                                "accession_number");
                            int copies = rangeRs.getInt("total_copies");

                            try {
                                int startNum = Integer.parseInt(
                                    accStr.replaceAll("[^0-9]", "")
                                );
                                int endNum = startNum + copies - 1;

                                // Check if searched number is in range
                                if (searchNum >= startNum
                                        && searchNum <= endNum) {
                                    books.add(mapBook(rangeRs));
                                }
                            } catch (NumberFormatException ignored) {
                                // Non-numeric accession — skip
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // Keyword not numeric — skip range search
                }
            }

        } catch (SQLException e) {
            System.err.println("Search failed: " + e.getMessage());
        }

        return books;
    }

    // ── Check ISBN exists ─────────────────────────────────────────────
    public boolean isbnExists(String isbn, int excludeId) {
        if (isbn == null || isbn.isBlank()) return false;
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(
                    "SELECT COUNT(*) FROM books WHERE isbn = ? AND id != ?" +
                    BranchScope.andClause("branch_id")
                );
            stmt.setString(1, isbn);
            stmt.setInt(2, excludeId);
            BranchScope.bind(stmt, 3);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Check Accession Number exists ─────────────────────────────────
    public boolean accessionExists(String accession, int excludeId) {
        if (accession == null || accession.isBlank()) return false;
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(
                    "SELECT COUNT(*) FROM books " +
                    "WHERE accession_number = ? AND id != ?" +
                    BranchScope.andClause("branch_id")
                );
            stmt.setString(1, accession);
            stmt.setInt(2, excludeId);
            BranchScope.bind(stmt, 3);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Check if accession range is available ─────────────────────────
    public boolean isAccessionRangeAvailable(int start, int copies,
                                              int excludeId) {
        int end = start + copies - 1;
        try {
            Connection conn = DatabaseConnection.getConnection();
            String branchFilter = BranchScope.isScoped() ? "AND branch_id = ?" : "";
            String sql = """
                SELECT accession_number, total_copies
                FROM books
                WHERE accession_number IS NOT NULL
                AND   accession_number != ''
                AND   id != ?
                %s
            """.formatted(branchFilter);
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, excludeId);
            BranchScope.bind(stmt, 2);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String accStr  = rs.getString("accession_number");
                int    copies2 = rs.getInt("total_copies");
                try {
                    int existStart = Integer.parseInt(
                        accStr.replaceAll("[^0-9]", "")
                    );
                    int existEnd = existStart + copies2 - 1;

                    // Check overlap
                    if (start <= existEnd && end >= existStart) {
                        System.err.println("Range conflict: " +
                            start + "-" + end +
                            " overlaps " + existStart + "-" + existEnd);
                        return false;
                    }
                } catch (NumberFormatException ignored) {}
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Range check failed: " + e.getMessage());
            return true;
        }
    }

    // ── Get accession range display string ────────────────────────────
    public static String getAccessionRange(String start, int copies) {
        if (start == null || start.isBlank()) return "";
        try {
            String numericPart = start.replaceAll("[^0-9]", "");
            String prefix      = start.replaceAll("[0-9]", "");
            if (numericPart.isEmpty()) return start;

            int startNum = Integer.parseInt(numericPart);
            int endNum   = startNum + copies - 1;

            if (copies == 1) return start;
            return prefix + startNum + " → " + prefix + endNum;
        } catch (NumberFormatException e) {
            return start;
        }
    }

    // ── Get latest inserted book id ───────────────────────────────────
    public int getLastInsertedId() {
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(
                    "SELECT id FROM books" +
                    (BranchScope.isScoped() ? " WHERE branch_id = ?" : "") +
                    " ORDER BY id DESC LIMIT 1"
                );
            BranchScope.bind(stmt, 1);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : 0;
        } catch (SQLException e) {
            System.err.println("Fetch last book id failed: " + e.getMessage());
            return 0;
        }
    }

    // ── Map ResultSet → Book ──────────────────────────────────────────
    private Book mapBook(ResultSet rs) throws SQLException {
        return new Book(
            rs.getInt("id"),
            rs.getString("title"),
            rs.getString("author"),
            rs.getString("isbn"),
            rs.getString("category"),
            rs.getInt("total_copies"),
            rs.getInt("available_copies"),
            rs.getString("accession_number"),
            rs.getString("classification_number"),
            rs.getString("cutter_number"),
            rs.getString("edition"),
            rs.getString("publisher"),
            rs.getString("place_of_publication"),
            rs.getInt("year_of_publication"),
            rs.getInt("number_of_pages")
        );
    }
}
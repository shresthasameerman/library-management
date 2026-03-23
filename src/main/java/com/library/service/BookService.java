package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.Book;

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
            PreparedStatement stmt = conn.prepareStatement(sql);
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

            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM books WHERE id = ?"
            );
            stmt.setInt(1, bookId);
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

    // ── Search Books ──────────────────────────────────────────────────
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
            WHERE title               LIKE ?
               OR author              LIKE ?
               OR isbn                LIKE ?
               OR category            LIKE ?
               OR accession_number    LIKE ?
               OR classification_number LIKE ?
            ORDER BY title ASC
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            String p = "%" + keyword + "%";
            for (int i = 1; i <= 6; i++) stmt.setString(i, p);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) books.add(mapBook(rs));
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
                    "SELECT COUNT(*) FROM books WHERE isbn = ? AND id != ?"
                );
            stmt.setString(1, isbn);
            stmt.setInt(2, excludeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { return false; }
    }

    // ── Check Accession Number exists ─────────────────────────────────
    public boolean accessionExists(String accession, int excludeId) {
        if (accession == null || accession.isBlank()) return false;
        try {
            PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(
                    "SELECT COUNT(*) FROM books " +
                    "WHERE accession_number = ? AND id != ?"
                );
            stmt.setString(1, accession);
            stmt.setInt(2, excludeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { return false; }
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
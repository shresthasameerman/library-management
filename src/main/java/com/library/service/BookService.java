package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.model.Book;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BookService {

    // ── Add New Book ──────────────────────────────────────────────────
    public boolean addBook(Book book) {
        String sql = """
            INSERT INTO books (title, author, isbn, category,
                               total_copies, available_copies)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, book.getTitle());
            stmt.setString(2, book.getAuthor());
            stmt.setString(3, book.getIsbn());
            stmt.setString(4, book.getCategory());
            stmt.setInt(5, book.getTotalCopies());
            stmt.setInt(6, book.getTotalCopies()); // available = total on add
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
            UPDATE books SET title = ?, author = ?, isbn = ?,
                category = ?, total_copies = ?
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
            stmt.setInt(6, book.getId());
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
        // Prevent delete if book is currently issued
        String checkSql = """
            SELECT COUNT(*) FROM issue_records
            WHERE book_id = ? AND status = 'ISSUED'
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();

            PreparedStatement check = conn.prepareStatement(checkSql);
            check.setInt(1, bookId);
            ResultSet rs = check.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.err.println("Cannot delete — book is currently issued.");
                return false;
            }

            PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM books WHERE id = ?"
            );
            stmt.setInt(1, bookId);
            stmt.executeUpdate();
            System.out.println("✓ Book deleted: ID " + bookId);
            return true;

        } catch (SQLException e) {
            System.err.println("Delete book failed: " + e.getMessage());
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
                   total_copies, available_copies
            FROM books
            WHERE title    LIKE ?
               OR author   LIKE ?
               OR isbn     LIKE ?
               OR category LIKE ?
            ORDER BY title ASC
        """;
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            String pattern = "%" + keyword + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setString(4, pattern);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                books.add(new Book(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("author"),
                    rs.getString("isbn"),
                    rs.getString("category"),
                    rs.getInt("total_copies"),
                    rs.getInt("available_copies")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Search failed: " + e.getMessage());
        }
        return books;
    }

    // ── Check if ISBN already exists ──────────────────────────────────
    public boolean isbnExists(String isbn, int excludeId) {
        String sql = "SELECT COUNT(*) FROM books WHERE isbn = ? AND id != ?";
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, isbn);
            stmt.setInt(2, excludeId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }
}

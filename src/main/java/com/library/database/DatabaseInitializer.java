package com.library.database;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

public class DatabaseInitializer {

    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // ── Users table ──────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role          TEXT NOT NULL
                                  CHECK(role IN ('ADMIN','LIBRARIAN')),
                    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ── Books table ───────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    title            TEXT NOT NULL,
                    author           TEXT NOT NULL,
                    isbn             TEXT UNIQUE,
                    category         TEXT,
                    total_copies     INTEGER DEFAULT 1,
                    available_copies INTEGER DEFAULT 1,
                    added_at         DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ── Members table ─────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS members (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    name       TEXT NOT NULL,
                    email      TEXT,
                    phone      TEXT,
                    member_id  TEXT UNIQUE NOT NULL,
                    department TEXT,
                    active     INTEGER DEFAULT 1,
                    joined_at  DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Safe migration: add member_type if missing
            try {
                stmt.execute(
                    "ALTER TABLE members ADD COLUMN member_type TEXT DEFAULT 'Student'"
                );
                System.out.println("✓ member_type column added.");
            } catch (SQLException ignored) {
                // Column already exists — safe to ignore
            }

            // ── Issue Records table ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS issue_records (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id     INTEGER NOT NULL,
                    member_id   INTEGER NOT NULL,
                    issue_date  DATE NOT NULL,
                    due_date    DATE NOT NULL,
                    return_date DATE,
                    fine_amount REAL DEFAULT 0,
                    status      TEXT DEFAULT 'ISSUED'
                                CHECK(status IN ('ISSUED','RETURNED','OVERDUE')),
                    FOREIGN KEY(book_id)   REFERENCES books(id),

                    FOREIGN KEY(member_id) REFERENCES members(id)
                )
            """);


            // ── Seed default admin account ────────────────────────────
            seedDefaultAdmin(conn);

            System.out.println("✓ Database initialized successfully.");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database: "
                                       + e.getMessage(), e);
        }
    }

    private static void seedDefaultAdmin(Connection conn) throws SQLException {
        // Check if admin already exists
        var check = conn.prepareStatement(
            "SELECT COUNT(*) FROM users WHERE username = 'admin'"
        );
        var rs = check.executeQuery();

        if (rs.next() && rs.getInt(1) == 0) {
            // Hash the default password using BCrypt
            String hash = org.mindrot.jbcrypt.BCrypt.hashpw(
                "admin123",
                org.mindrot.jbcrypt.BCrypt.gensalt()
            );

            var insert = conn.prepareStatement(
                "INSERT INTO users (username, password_hash, role) VALUES (?, ?, 'ADMIN')"
            );
            insert.setString(1, "admin");
            insert.setString(2, hash);
            insert.executeUpdate();

            System.out.println("✓ Default admin created → username: admin | password: admin123");
        }
    }
}

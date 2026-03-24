package com.library.database;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

public class DatabaseInitializer {

    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // ── Users table ───────────────────────────────────────────
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
                    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
                    title                 TEXT NOT NULL,
                    author                TEXT NOT NULL,
                    isbn                  TEXT UNIQUE,
                    category              TEXT,
                    total_copies          INTEGER DEFAULT 1,
                    available_copies      INTEGER DEFAULT 1,
                    accession_number      TEXT,
                    classification_number TEXT,
                    cutter_number         TEXT,
                    edition               TEXT,
                    publisher             TEXT,
                    place_of_publication  TEXT,
                    year_of_publication   INTEGER DEFAULT 0,
                    number_of_pages       INTEGER DEFAULT 0,
                    added_at              DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ── Members table ─────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS members (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    name        TEXT NOT NULL,
                    email       TEXT,
                    phone       TEXT,
                    member_id   TEXT UNIQUE NOT NULL,
                    department  TEXT,
                    member_type TEXT DEFAULT 'Student',
                    intake      TEXT DEFAULT '',
                    active      INTEGER DEFAULT 1,
                    joined_at   DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ── Book Copies table ────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS book_copies (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id          INTEGER NOT NULL,
                    accession_number TEXT UNIQUE NOT NULL,
                    status           TEXT DEFAULT 'AVAILABLE'
                                     CHECK(status IN ('AVAILABLE','ISSUED','LOST')),
                    FOREIGN KEY(book_id) REFERENCES books(id)
                )
            """);

            // ── Issue Records table ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS issue_records (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id          INTEGER NOT NULL,
                    book_copy_id     INTEGER,
                    member_id        INTEGER NOT NULL,
                    accession_number TEXT,
                    issue_date       DATE NOT NULL,
                    due_date         DATE NOT NULL,
                    return_date      DATE,
                    fine_amount      REAL DEFAULT 0,
                    status           TEXT DEFAULT 'ISSUED'
                                     CHECK(status IN ('ISSUED','RETURNED','OVERDUE')),
                    FOREIGN KEY(book_id)      REFERENCES books(id),
                    FOREIGN KEY(book_copy_id) REFERENCES book_copies(id),
                    FOREIGN KEY(member_id)    REFERENCES members(id)
                )
            """);

            // ── Safe migrations (existing DB) ─────────────────────────

            // Members — member_type
            try {
                stmt.execute(
                    "ALTER TABLE members ADD COLUMN " +
                    "member_type TEXT DEFAULT 'Student'"
                );
                System.out.println("✓ member_type column added.");
            } catch (SQLException ignored) {}

            // Members — intake
            try {
                stmt.execute(
                    "ALTER TABLE members ADD COLUMN intake TEXT DEFAULT ''"
                );
                System.out.println("✓ intake column added.");
            } catch (SQLException ignored) {}

            // Books — new columns
            String[] bookColumns = {
                "ALTER TABLE books ADD COLUMN accession_number TEXT",
                "ALTER TABLE books ADD COLUMN classification_number TEXT",
                "ALTER TABLE books ADD COLUMN cutter_number TEXT",
                "ALTER TABLE books ADD COLUMN edition TEXT",
                "ALTER TABLE books ADD COLUMN publisher TEXT",
                "ALTER TABLE books ADD COLUMN place_of_publication TEXT",
                "ALTER TABLE books ADD COLUMN year_of_publication INTEGER DEFAULT 0",
                "ALTER TABLE books ADD COLUMN number_of_pages INTEGER DEFAULT 0"
            };

            for (String sql : bookColumns) {
                try {
                    stmt.execute(sql);
                } catch (SQLException ignored) {}
            }

            // Issue records — new columns
            String[] issueColumns = {
                "ALTER TABLE issue_records ADD COLUMN book_copy_id INTEGER",
                "ALTER TABLE issue_records ADD COLUMN accession_number TEXT"
            };

            for (String sql : issueColumns) {
                try {
                    stmt.execute(sql);
                } catch (SQLException ignored) {}
            }

            // Unique index for accession_number
            try {
                stmt.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_accession
                    ON books(accession_number)
                    WHERE accession_number IS NOT NULL
                """);
            } catch (SQLException ignored) {}

            System.out.println("✓ Book columns migrated.");

            // ── Seed default admin ────────────────────────────────────
            seedDefaultAdmin(conn);

            System.out.println("✓ Database initialized successfully.");

        } catch (SQLException e) {
            throw new RuntimeException(
                "Failed to initialize database: " + e.getMessage(), e);
        }
    }

    private static void seedDefaultAdmin(Connection conn)
            throws SQLException {
        var check = conn.prepareStatement(
            "SELECT COUNT(*) FROM users WHERE username = 'admin'"
        );
        var rs = check.executeQuery();

        if (rs.next() && rs.getInt(1) == 0) {
            String hash = org.mindrot.jbcrypt.BCrypt.hashpw(
                "admin123",
                org.mindrot.jbcrypt.BCrypt.gensalt()
            );
            var insert = conn.prepareStatement(
                "INSERT INTO users (username, password_hash, role) " +
                "VALUES (?, ?, 'ADMIN')"
            );
            insert.setString(1, "admin");
            insert.setString(2, hash);
            insert.executeUpdate();
            System.out.println(
                "✓ Default admin created → username: admin | password: admin123"
            );
        }
    }
}
package com.library.database;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DatabaseInitializer {

    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // ── Branches table ────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS branches (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    name       TEXT NOT NULL,
                    department TEXT,
                    code       TEXT UNIQUE,
                    active     INTEGER DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // ── Users table ───────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role          TEXT NOT NULL
                                  CHECK(role IN ('SUPER_ADMIN','SUPERADMIN','ADMIN','LIBRARIAN','STUDENT')),
                    branch_id     INTEGER,
                    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(branch_id) REFERENCES branches(id)
                )
            """);

            // ── Books table ───────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS books (
                    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
                    title                 TEXT NOT NULL,
                    author                TEXT NOT NULL,
                    isbn                  TEXT UNIQUE NOT NULL,
                    branch_id             INTEGER,
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
                    added_at              DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(branch_id) REFERENCES branches(id)
                )
            """);

            // ── Members table ─────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS members (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    name        TEXT NOT NULL,
                    email       TEXT UNIQUE,
                    phone       TEXT UNIQUE,
                    member_id   TEXT UNIQUE NOT NULL,
                    branch_id   INTEGER,
                    department  TEXT,
                    member_type TEXT DEFAULT 'Student',
                    intake_date DATE,
                    active      INTEGER DEFAULT 1,
                    joined_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(branch_id) REFERENCES branches(id)
                )
            """);

            // ── Book Copies table ────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS book_copies (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id          INTEGER NOT NULL,
                    accession_number TEXT UNIQUE NOT NULL,
                    spine_level      TEXT,
                    branch_id        INTEGER,
                    status           TEXT DEFAULT 'AVAILABLE'
                                     CHECK(status IN ('AVAILABLE','ISSUED','LOST')),
                    FOREIGN KEY(book_id) REFERENCES books(id),
                    FOREIGN KEY(branch_id) REFERENCES branches(id)
                )
            """);

            // ── Issue Records table ───────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS issue_records (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    book_id          INTEGER NOT NULL,
                    book_copy_id     INTEGER,
                    member_id        INTEGER NOT NULL,
                    branch_id        INTEGER,
                    accession_number TEXT,
                    issue_date       DATE NOT NULL,
                    due_date         DATE NOT NULL,
                    return_date      DATE,
                    fine_amount      REAL DEFAULT 0,
                    status           TEXT DEFAULT 'ISSUED'
                                     CHECK(status IN ('ISSUED','RETURNED','OVERDUE')),
                    FOREIGN KEY(book_id)      REFERENCES books(id),
                    FOREIGN KEY(book_copy_id) REFERENCES book_copies(id),
                    FOREIGN KEY(member_id)    REFERENCES members(id),
                    FOREIGN KEY(branch_id)    REFERENCES branches(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS app_license (
                    id INTEGER PRIMARY KEY CHECK(id = 1), license_key TEXT NOT NULL,
                    tier TEXT NOT NULL, machine_id TEXT NOT NULL, expires_at INTEGER NOT NULL,
                    activated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            migrateUsersRoleCheckIfNeeded(conn);

            // ── Safe migrations (existing DB) ─────────────────────────

            // Members — member_type
            try {
                stmt.execute(
                    "ALTER TABLE members ADD COLUMN " +
                    "member_type TEXT DEFAULT 'Student'"
                );
                System.out.println("✓ member_type column added.");
            } catch (SQLException ignored) {}

            // Branch columns for existing DBs
            String[] branchColumns = {
                "ALTER TABLE users ADD COLUMN branch_id INTEGER",
                "ALTER TABLE books ADD COLUMN branch_id INTEGER",
                "ALTER TABLE members ADD COLUMN branch_id INTEGER",
                "ALTER TABLE book_copies ADD COLUMN branch_id INTEGER",
                "ALTER TABLE issue_records ADD COLUMN branch_id INTEGER"
            };

            for (String sql : branchColumns) {
                try {
                    stmt.execute(sql);
                } catch (SQLException ignored) {}
            }

            // Book copies — spine level per accession copy
            try {
                stmt.execute("ALTER TABLE book_copies ADD COLUMN spine_level TEXT");
            } catch (SQLException ignored) {}

            // Members — intake
            try {
                stmt.execute(
                    "ALTER TABLE members ADD COLUMN intake_date DATE"
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
                stmt.execute("DROP INDEX IF EXISTS idx_accession");
                stmt.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_accession
                    ON books(branch_id, accession_number)
                    WHERE accession_number IS NOT NULL
                    AND accession_number != ''
                """);
            } catch (SQLException ignored) {}

            System.out.println("✓ Book columns migrated.");

            int defaultBranchId = seedDefaultBranch(conn);
            backfillBranchData(conn, defaultBranchId);
            normalizeEarlyRenewedActiveIssues(conn);

            // ── Seed default users ────────────────────────────────────
            seedDefaultUsers(conn, defaultBranchId);
            seedStudentUsersFromExistingMembers(conn);

            System.out.println("✓ Database initialized successfully.");

        } catch (SQLException e) {
            throw new RuntimeException(
                "Failed to initialize database: " + e.getMessage(), e);
        }
    }

    private static void migrateUsersRoleCheckIfNeeded(Connection conn)
            throws SQLException {
        String tableSql = null;
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'users'"
        )) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) tableSql = rs.getString(1);
        }

        if (tableSql == null || tableSql.contains("STUDENT")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                    "UPDATE users SET role = 'SUPER_ADMIN' WHERE role = 'SUPERADMIN'"
                );
            } catch (SQLException ignored) {}
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE users_new (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    username      TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    role          TEXT NOT NULL
                                  CHECK(role IN ('SUPER_ADMIN','SUPERADMIN','ADMIN','LIBRARIAN','STUDENT')),
                    branch_id     INTEGER,
                    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(branch_id) REFERENCES branches(id)
                )
            """);

            stmt.execute("""
                INSERT INTO users_new (id, username, password_hash, role, branch_id, created_at)
                SELECT id, username, password_hash, role, NULL, created_at
                FROM users
            """);

            stmt.execute("DROP TABLE users");
            stmt.execute("ALTER TABLE users_new RENAME TO users");
            System.out.println("✓ users role check migrated for SUPERADMIN.");
        }
    }

    private static int seedDefaultBranch(Connection conn) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement(
            "SELECT id FROM branches WHERE code = 'MAIN' LIMIT 1"
        )) {
            ResultSet rs = check.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }

        try (PreparedStatement insert = conn.prepareStatement(
            "INSERT INTO branches (name, department, code) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        )) {
            insert.setString(1, "Main Library");
            insert.setString(2, "General");
            insert.setString(3, "MAIN");
            insert.executeUpdate();

            ResultSet keys = insert.getGeneratedKeys();
            if (keys.next()) {
                System.out.println("✓ Default branch created: Main Library");
                return keys.getInt(1);
            }
        }

        throw new SQLException("Failed to create default branch.");
    }

    private static void backfillBranchData(Connection conn, int branchId)
            throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE books SET branch_id = " + branchId + " WHERE branch_id IS NULL");
            stmt.executeUpdate("UPDATE members SET branch_id = " + branchId + " WHERE branch_id IS NULL");
            stmt.executeUpdate("UPDATE book_copies SET branch_id = " + branchId + " WHERE branch_id IS NULL");
            stmt.executeUpdate("UPDATE issue_records SET branch_id = " + branchId + " WHERE branch_id IS NULL");

            // Backfill issue branch using book branch if possible.
            stmt.executeUpdate("""
                UPDATE issue_records
                SET branch_id = (
                    SELECT b.branch_id
                    FROM books b
                    WHERE b.id = issue_records.book_id
                )
                WHERE branch_id IS NULL
            """);

            // Backfill copy branch using related book branch.
            stmt.executeUpdate("""
                UPDATE book_copies
                SET branch_id = (
                    SELECT b.branch_id
                    FROM books b
                    WHERE b.id = book_copies.book_id
                )
                WHERE branch_id IS NULL
            """);
        }
    }

    private static void seedDefaultUsers(Connection conn, int defaultBranchId)
            throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next() && rs.getInt(1) == 0) return;
        }
        String superHash = org.mindrot.jbcrypt.BCrypt.hashpw(
            "superadmin123",
            org.mindrot.jbcrypt.BCrypt.gensalt()
        );
        String librarianHash = org.mindrot.jbcrypt.BCrypt.hashpw(
            "admin123",
            org.mindrot.jbcrypt.BCrypt.gensalt()
        );

        try (PreparedStatement check = conn.prepareStatement(
            "SELECT COUNT(*) FROM users WHERE username = ?"
        )) {
            check.setString(1, "superadmin");
            ResultSet rs = check.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, role, branch_id) VALUES (?, ?, 'SUPER_ADMIN', NULL)"
                )) {
                    insert.setString(1, "superadmin");
                    insert.setString(2, superHash);
                    insert.executeUpdate();
                    System.out.println("✓ Default superadmin created → username: superadmin | password: superadmin123");
                }
            }
        }

        try (PreparedStatement check = conn.prepareStatement(
            "SELECT id, role, branch_id FROM users WHERE username = ? LIMIT 1"
        )) {
            check.setString(1, "admin");
            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                int adminId = rs.getInt("id");
                String role = rs.getString("role");
                Integer branchId = rs.getObject("branch_id") != null
                    ? rs.getInt("branch_id")
                    : null;

                if ("ADMIN".equals(role) || "LIBRARIAN".equals(role)) {
                    try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE users SET role = 'ADMIN', branch_id = ? WHERE id = ?"
                    )) {
                        update.setInt(1, branchId != null ? branchId : defaultBranchId);
                        update.setInt(2, adminId);
                        update.executeUpdate();
                    }
                }
            } else {
                try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, role, branch_id) VALUES (?, ?, 'ADMIN', ?)"
                )) {
                    insert.setString(1, "admin");
                    insert.setString(2, librarianHash);
                    insert.setInt(3, defaultBranchId);
                    insert.executeUpdate();
                    System.out.println("✓ Default librarian created → username: admin | password: admin123");
                }
            }
        }
    }

    private static void seedStudentUsersFromExistingMembers(Connection conn)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            """
                SELECT m.id, m.member_id, m.branch_id
                FROM members m
                LEFT JOIN users u ON LOWER(u.username) = LOWER(m.member_id)
                   AND u.role = 'STUDENT'
                WHERE LOWER(COALESCE(m.member_type, 'Student')) = 'student'
                  AND m.member_id IS NOT NULL
                  AND TRIM(m.member_id) != ''
                  AND u.id IS NULL
            """
        )) {
            ResultSet rs = stmt.executeQuery();
            int created = 0;
            while (rs.next()) {
                String memberId = rs.getString("member_id");
                String tempPassword = memberId;
                String hashed = org.mindrot.jbcrypt.BCrypt.hashpw(
                    tempPassword,
                    org.mindrot.jbcrypt.BCrypt.gensalt()
                );

                try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, role, branch_id) VALUES (?, ?, 'STUDENT', ?)"
                )) {
                    insert.setString(1, memberId);
                    insert.setString(2, hashed);
                    if (rs.getObject("branch_id") != null) {
                        insert.setInt(3, rs.getInt("branch_id"));
                    } else {
                        insert.setNull(3, java.sql.Types.INTEGER);
                    }
                    insert.executeUpdate();
                    created++;
                }
            }

            if (created > 0) {
                System.out.println("✓ Student login accounts seeded for existing members: " + created);
                System.out.println("✓ Default student password for migrated accounts is the Member ID.");
            }
        }
    }

    private static void normalizeEarlyRenewedActiveIssues(Connection conn)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
            """
                UPDATE issue_records
                SET due_date = DATE(issue_date, '+14 day')
                WHERE status IN ('ISSUED', 'OVERDUE')
                  AND due_date > DATE(issue_date, '+14 day')
                  AND DATE('now') < DATE(issue_date, '+14 day')
            """
        )) {
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                System.out.println("✓ Normalized early-renewed active issues: " + updated);
            }
        }
    }
}
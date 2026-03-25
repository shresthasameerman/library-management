package com.library.service;

import com.library.database.DatabaseConnection;
import com.library.util.BranchScope;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ReportService {

    // Save all reports in ~/LibraryApp/Reports/
    private static final String REPORTS_DIR = Paths.get(
        System.getProperty("user.home"), "LibraryApp", "Reports"
    ).toString();

    public ReportService() {
        // Create reports directory if it doesn't exist
        try {
            Files.createDirectories(Path.of(REPORTS_DIR));
        } catch (IOException e) {
            System.err.println("Could not create reports dir: " + e.getMessage());
        }
    }

    // ── 1. Currently Issued Books ─────────────────────────────────────
    public String generateIssuedBooksReport() {
        String filename = "Issued_Books_" + today() + ".csv";
        String path     = REPORTS_DIR + "/" + filename;

        String sql = """
            SELECT
                ir.id          AS issue_id,
                m.name         AS member_name,
                m.member_id    AS member_code,
                m.member_type,
                b.title        AS book_title,
                b.author,
                ir.issue_date,
                ir.due_date,
                CASE
                    WHEN ir.due_date < DATE('now')
                    THEN CAST(julianday('now') - julianday(ir.due_date) AS INTEGER)
                    ELSE 0
                END AS days_overdue,
                CASE
                    WHEN ir.due_date < DATE('now')
                    THEN CAST((julianday('now') - julianday(ir.due_date)) * 5 AS INTEGER)
                    ELSE 0
                END AS fine_rs
            FROM issue_records ir
            JOIN books   b ON ir.book_id   = b.id
            JOIN members m ON ir.member_id = m.id
            WHERE ir.status = 'ISSUED'
            %s
            ORDER BY ir.due_date ASC
        """;
        sql = sql.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");

        try (FileWriter writer = new FileWriter(path)) {
            // Header
            writer.write("The British College — Library Management System\n");
            writer.write("Report: Currently Issued Books\n");
            writer.write("Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n");
            writer.write("\n");
            writer.write("Issue ID,Member Name,Member ID,Type," +
                         "Book Title,Author,Issue Date,Due Date," +
                         "Days Overdue,Fine (Rs.)\n");

            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            BranchScope.bind(stmt, 1);
            ResultSet rs = stmt.executeQuery();

            int count = 0;
            double totalFine = 0;
            while (rs.next()) {
                writer.write(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%d,%d\n",
                    rs.getInt("issue_id"),
                    escapeCsv(rs.getString("member_name")),
                    rs.getString("member_code"),
                    rs.getString("member_type"),
                    escapeCsv(rs.getString("book_title")),
                    escapeCsv(rs.getString("author")),
                    rs.getString("issue_date"),
                    rs.getString("due_date"),
                    rs.getInt("days_overdue"),
                    rs.getInt("fine_rs")
                ));
                totalFine += rs.getInt("fine_rs");
                count++;
            }

            writer.write("\n");
            writer.write("Total Records," + count + "\n");
            writer.write("Total Pending Fines,Rs. " + (int)totalFine + "\n");

            System.out.println("✓ Issued report saved: " + path);
            return path;

        } catch (Exception e) {
            System.err.println("Report failed: " + e.getMessage());
            return null;
        }
    }

    // ── 2. Overdue Books Report ───────────────────────────────────────
    public String generateOverdueReport() {
        String filename = "Overdue_Books_" + today() + ".csv";
        String path     = REPORTS_DIR + "/" + filename;

        String sql = """
            SELECT
                m.name         AS member_name,
                m.member_id    AS member_code,
                m.phone,
                m.member_type,
                b.title        AS book_title,
                ir.issue_date,
                ir.due_date,
                CAST(julianday('now') - julianday(ir.due_date) AS INTEGER)
                               AS days_overdue,
                CAST((julianday('now') - julianday(ir.due_date)) * 5 AS INTEGER)
                               AS fine_rs
            FROM issue_records ir
            JOIN books   b ON ir.book_id   = b.id
            JOIN members m ON ir.member_id = m.id
            WHERE ir.status = 'ISSUED'
            AND   ir.due_date < DATE('now')
            %s
            ORDER BY days_overdue DESC
        """;
        sql = sql.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");

        try (FileWriter writer = new FileWriter(path)) {
            writer.write("The British College — Library Management System\n");
            writer.write("Report: Overdue Books\n");
            writer.write("Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n");
            writer.write("\n");
            writer.write("Member Name,Member ID,Phone,Type," +
                         "Book Title,Issue Date,Due Date," +
                         "Days Overdue,Fine (Rs.)\n");

            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            BranchScope.bind(stmt, 1);
            ResultSet rs = stmt.executeQuery();

            int count = 0;
            double totalFine = 0;
            while (rs.next()) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%s,%d,%d\n",
                    escapeCsv(rs.getString("member_name")),
                    rs.getString("member_code"),
                    rs.getString("phone"),
                    rs.getString("member_type"),
                    escapeCsv(rs.getString("book_title")),
                    rs.getString("issue_date"),
                    rs.getString("due_date"),
                    rs.getInt("days_overdue"),
                    rs.getInt("fine_rs")
                ));
                totalFine += rs.getInt("fine_rs");
                count++;
            }

            writer.write("\n");
            writer.write("Total Overdue," + count + "\n");
            writer.write("Total Fines Pending,Rs. " + (int)totalFine + "\n");

            System.out.println("✓ Overdue report saved: " + path);
            return path;

        } catch (Exception e) {
            System.err.println("Overdue report failed: " + e.getMessage());
            return null;
        }
    }

    // ── 3. Book Inventory Report ──────────────────────────────────────
    public String generateInventoryReport() {
        String filename = "Book_Inventory_" + today() + ".csv";
        String path     = REPORTS_DIR + "/" + filename;

        String sql = """
            SELECT
                b.id, b.title, b.author, b.isbn, b.category,
                b.total_copies, b.available_copies,
                (b.total_copies - b.available_copies) AS issued_copies,
                b.added_at
            FROM books b
            %s
            ORDER BY b.category, b.title ASC
        """;
        sql = sql.formatted(BranchScope.isScoped() ? "WHERE b.branch_id = ?" : "");

        try (FileWriter writer = new FileWriter(path)) {
            writer.write("The British College — Library Management System\n");
            writer.write("Report: Book Inventory\n");
            writer.write("Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n");
            writer.write("\n");
            writer.write("ID,Title,Author,ISBN,Category," +
                         "Total Copies,Available,Issued,Added Date\n");

            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            BranchScope.bind(stmt, 1);
            ResultSet rs = stmt.executeQuery();

            int count = 0;
            int totalCopies = 0;
            while (rs.next()) {
                writer.write(String.format("%d,%s,%s,%s,%s,%d,%d,%d,%s\n",
                    rs.getInt("id"),
                    escapeCsv(rs.getString("title")),
                    escapeCsv(rs.getString("author")),
                    rs.getString("isbn") != null ? rs.getString("isbn") : "",
                    rs.getString("category") != null ? rs.getString("category") : "",
                    rs.getInt("total_copies"),
                    rs.getInt("available_copies"),
                    rs.getInt("issued_copies"),
                    rs.getString("added_at")
                ));
                totalCopies += rs.getInt("total_copies");
                count++;
            }

            writer.write("\n");
            writer.write("Total Books (Titles)," + count + "\n");
            writer.write("Total Copies," + totalCopies + "\n");

            System.out.println("✓ Inventory report saved: " + path);
            return path;

        } catch (Exception e) {
            System.err.println("Inventory report failed: " + e.getMessage());
            return null;
        }
    }

    // ── 4. Member List Report ─────────────────────────────────────────
    public String generateMemberReport() {
        String filename = "Member_List_" + today() + ".csv";
        String path     = REPORTS_DIR + "/" + filename;

        String sql = """
            SELECT
                m.id, m.name, m.member_id, m.member_type,
                m.department, m.phone, m.email,
                CASE WHEN m.active = 1 THEN 'Active' ELSE 'Inactive' END AS status,
                m.joined_at,
                COUNT(ir.id) AS total_borrowed,
                SUM(CASE WHEN ir.status = 'ISSUED' THEN 1 ELSE 0 END) AS currently_holding
            FROM members m
            LEFT JOIN issue_records ir ON m.id = ir.member_id
            %s
            GROUP BY m.id
            ORDER BY m.member_type, m.name ASC
        """;
        sql = sql.formatted(BranchScope.isScoped() ? "WHERE m.branch_id = ?" : "");

        try (FileWriter writer = new FileWriter(path)) {
            writer.write("The British College — Library Management System\n");
            writer.write("Report: Member List\n");
            writer.write("Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n");
            writer.write("\n");
            writer.write("ID,Name,Member ID,Type,Department,Phone,Email," +
                         "Status,Joined Date,Total Borrowed,Currently Holding\n");

            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            BranchScope.bind(stmt, 1);
            ResultSet rs = stmt.executeQuery();

            int students = 0, staff = 0;
            while (rs.next()) {
                writer.write(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s,%d,%d\n",
                    rs.getInt("id"),
                    escapeCsv(rs.getString("name")),
                    rs.getString("member_id"),
                    rs.getString("member_type"),
                    escapeCsv(rs.getString("department")),
                    rs.getString("phone") != null ? rs.getString("phone") : "",
                    rs.getString("email") != null ? rs.getString("email") : "",
                    rs.getString("status"),
                    rs.getString("joined_at"),
                    rs.getInt("total_borrowed"),
                    rs.getInt("currently_holding")
                ));
                if ("Student".equals(rs.getString("member_type"))) students++;
                else staff++;
            }

            writer.write("\n");
            writer.write("Total Students," + students + "\n");
            writer.write("Total Staff," + staff + "\n");
            writer.write("Grand Total," + (students + staff) + "\n");

            System.out.println("✓ Member report saved: " + path);
            return path;

        } catch (Exception e) {
            System.err.println("Member report failed: " + e.getMessage());
            return null;
        }
    }

    // ── 5. Monthly Summary Report ─────────────────────────────────────
    public String generateMonthlySummary() {
        String filename = "Monthly_Summary_" + today() + ".csv";
        String path     = REPORTS_DIR + "/" + filename;

        String month = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM"));

        try (FileWriter writer = new FileWriter(path)) {
            Connection conn = DatabaseConnection.getConnection();

            writer.write("The British College — Library Management System\n");
            writer.write("Report: Monthly Summary — " +
                LocalDate.now().format(
                    DateTimeFormatter.ofPattern("MMMM yyyy")) + "\n");
            writer.write("Generated: " + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\n");
            writer.write("\n");

            // ── Books issued this month
            String scopedIssueWhere = BranchScope.isScoped()
                ? " AND branch_id = " + BranchScope.branchId()
                : "";

            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM issue_records " +
                "WHERE strftime('%Y-%m', issue_date) = '" + month + "'" +
                scopedIssueWhere
            );
            writer.write("MONTHLY STATISTICS\n");
            writer.write("Books Issued This Month," +
                (rs.next() ? rs.getInt(1) : 0) + "\n");

            // ── Books returned this month
            rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM issue_records " +
                "WHERE strftime('%Y-%m', return_date) = '" + month + "'" +
                scopedIssueWhere
            );
            writer.write("Books Returned This Month," +
                (rs.next() ? rs.getInt(1) : 0) + "\n");

            // ── Fines collected this month
            rs = conn.createStatement().executeQuery(
                "SELECT COALESCE(SUM(fine_amount), 0) FROM issue_records " +
                "WHERE strftime('%Y-%m', return_date) = '" + month + "'" +
                scopedIssueWhere
            );
            writer.write("Fines Collected (Rs.)," +
                (rs.next() ? (int)rs.getDouble(1) : 0) + "\n");

            // ── Total active members
            rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM members WHERE active = 1" +
                (BranchScope.isScoped()
                    ? " AND branch_id = " + BranchScope.branchId()
                    : "")
            );
            writer.write("Total Active Members," +
                (rs.next() ? rs.getInt(1) : 0) + "\n");

            // ── Currently issued
            rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM issue_records WHERE status = 'ISSUED'" +
                scopedIssueWhere
            );
            writer.write("Currently Issued Books," +
                (rs.next() ? rs.getInt(1) : 0) + "\n");

            // ── Overdue
            rs = conn.createStatement().executeQuery(
                "SELECT COUNT(*) FROM issue_records " +
                "WHERE status = 'ISSUED' AND due_date < DATE('now')" +
                scopedIssueWhere
            );
            writer.write("Overdue Books," +
                (rs.next() ? rs.getInt(1) : 0) + "\n");

            // ── Total fines pending
            String pendingFineSql = """
                SELECT COALESCE(
                    SUM(CAST((julianday('now') - julianday(due_date)) * 5 AS INTEGER))
                , 0)
                FROM issue_records
                WHERE status = 'ISSUED' AND due_date < DATE('now')
                %s
            """;
            pendingFineSql = pendingFineSql.formatted(
                BranchScope.isScoped() ? "AND branch_id = " + BranchScope.branchId() : ""
            );
            rs = conn.createStatement().executeQuery(pendingFineSql);
            writer.write("Total Pending Fines (Rs.)," +
                (rs.next() ? rs.getInt(1) : 0) + "\n");

            writer.write("\n");

            // ── Top borrowed books this month
            writer.write("TOP BORROWED BOOKS THIS MONTH\n");
            writer.write("Book Title,Author,Times Borrowed\n");
            rs = conn.createStatement().executeQuery("""
                SELECT b.title, b.author, COUNT(*) AS times
                FROM issue_records ir
                JOIN books b ON ir.book_id = b.id
                WHERE strftime('%Y-%m', ir.issue_date) = '""" + month + """
                '
                %s
                GROUP BY ir.book_id
                ORDER BY times DESC
                LIMIT 10
            """.formatted(BranchScope.isScoped()
                ? "AND ir.branch_id = " + BranchScope.branchId()
                : ""));
            while (rs.next()) {
                writer.write(String.format("%s,%s,%d\n",
                    escapeCsv(rs.getString("title")),
                    escapeCsv(rs.getString("author")),
                    rs.getInt("times")
                ));
            }

            System.out.println("✓ Monthly summary saved: " + path);
            return path;

        } catch (Exception e) {
            System.err.println("Monthly summary failed: " + e.getMessage());
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private String today() {
        return LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") ||
            value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public String getReportsDirectory() {
        return REPORTS_DIR;
    }
}
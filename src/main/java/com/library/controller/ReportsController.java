package com.library.controller;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.library.database.DatabaseConnection;
import com.library.model.ReportRecord;
import com.library.model.User;
import com.library.util.BranchScope;
import com.library.util.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Button;
import javafx.scene.control.cell.PropertyValueFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Cell;
// POI Font removed — use fully qualified org.apache.poi.ss.usermodel.Font inline

import javax.swing.*;
import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

public class ReportsController {

    // ── UI Components ─────────────────────────────────────────────────
    @FXML private ComboBox<String> reportTypeCombo;
    @FXML private ComboBox<String> reportBranchCombo;
    @FXML private ComboBox<String> reportFormatCombo;
    @FXML private DatePicker reportFromDate;
    @FXML private DatePicker reportToDate;

    @FXML private TableView<ObservableList<String>> reportPreviewTable;
    @FXML private Label rowCountLabel;

    @FXML private TableView<ReportRecord> reportHistoryTable;
    @FXML private TableColumn<ReportRecord, String> reportNameColumn;
    @FXML private TableColumn<ReportRecord, String> reportTypeColumn;
    @FXML private TableColumn<ReportRecord, String> generatedByColumn;
    @FXML private TableColumn<ReportRecord, String> generatedDateColumn;
    @FXML private TableColumn<ReportRecord, String> fileSizeColumn;
    @FXML private TableColumn<ReportRecord, String> downloadColumn;

    @FXML private Label totalReportsLabel;
    @FXML private Label monthlyReportsLabel;
    @FXML private Label totalDownloadsLabel;

    // ── State ─────────────────────────────────────────────────────────
    private final ObservableList<ReportRecord> recentReports =
        FXCollections.observableArrayList();
    private int reportCount = 0;
    private int monthReportCount = 0;

    private static final String REPORTS_HOME = System.getProperty("user.home");
    private static final String REPORTS_BASE = REPORTS_HOME + "/LibraryApp/reports";

    // ── Initialize ────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        try {
            setupDatePickers();
            loadBranches();
            setupReportTypes();
            setupFormats();
            setupHistoryTable();

            if (reportPreviewTable != null) {
                reportPreviewTable.setPlaceholder(
                    new Label("Generate a report to see data here")
                );
            }
            setRowCountLabel("");
            updateStatistics();
        } catch (Exception e) {
            System.err.println("ReportsController init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupDatePickers() {
        if (reportFromDate != null) {
            reportFromDate.setValue(LocalDate.now().minusDays(7));
        }
        if (reportToDate != null) {
            reportToDate.setValue(LocalDate.now());
        }
    }

    private void loadBranches() {
        Task<ObservableList<String>> task = new Task<>() {
            @Override
            protected ObservableList<String> call() throws SQLException {
                ObservableList<String> branches = FXCollections.observableArrayList();
                branches.add("All Branches");

                String sql = "SELECT id, name FROM branches WHERE active = 1 ORDER BY name";
                try (Connection conn = DatabaseConnection.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        branches.add(rs.getString("name"));
                    }
                } catch (SQLException e) {
                    System.err.println("Load branches failed: " + e.getMessage());
                }
                return branches;
            }
        };
        task.setOnSucceeded(e -> {
            if (reportBranchCombo != null) {
                reportBranchCombo.setItems(task.getValue());
                reportBranchCombo.setValue("All Branches");
            }
        });
        task.setOnFailed(e -> showError("Could not load branches"));
        new Thread(task).start();
    }

    private void setupReportTypes() {
        ObservableList<String> types = FXCollections.observableArrayList(
            "Issue/Return Summary",
            "Overdue Alert",
            "Member Statistics",
            "Inventory Report",
            "Fine Collection Report",
            "Admin Activity Report"
        );
        if (reportTypeCombo != null) {
            reportTypeCombo.setItems(types);
            reportTypeCombo.setValue("Issue/Return Summary");
        }
    }

    private void setupFormats() {
        ObservableList<String> formats = FXCollections.observableArrayList(
            "Excel", "CSV", "PDF"
        );
        if (reportFormatCombo != null) {
            reportFormatCombo.setItems(formats);
            reportFormatCombo.setValue("Excel");
        }
    }

    private void setupHistoryTable() {
        if (reportHistoryTable == null) {
            return;
        }

        if (reportNameColumn != null) {
            reportNameColumn.setCellValueFactory(
                new PropertyValueFactory<>("reportName"));
        }
        if (reportTypeColumn != null) {
            reportTypeColumn.setCellValueFactory(
                new PropertyValueFactory<>("reportType"));
        }
        if (generatedByColumn != null) {
            generatedByColumn.setCellValueFactory(
                new PropertyValueFactory<>("generatedBy"));
        }
        if (generatedDateColumn != null) {
            generatedDateColumn.setCellValueFactory(
                new PropertyValueFactory<>("generatedDate"));
        }
        if (fileSizeColumn != null) {
            fileSizeColumn.setCellValueFactory(
                new PropertyValueFactory<>("rowCount"));
        }

        // Open file action in existing Action column
        TableColumn<ReportRecord, Void> actionCol = new TableColumn<>("Action");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button openBtn = new Button("📂 Open");
            {
                openBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                openBtn.setOnAction(e -> {
                    ReportRecord record = getTableView().getItems().get(getIndex());
                    openFile(new File(record.getFilePath()));
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : openBtn);
            }
        });
        if (downloadColumn != null) {
            downloadColumn.setCellFactory(col -> new TableCell<>() {
                private final Button openBtn = new Button("📂 Open");
                {
                    openBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
                    openBtn.setOnAction(e -> {
                        ReportRecord record = getTableView().getItems().get(getIndex());
                        openFile(new File(record.getFilePath()));
                    });
                }
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : openBtn);
                    setText(null);
                }
            });
        }
        reportHistoryTable.setItems(recentReports);
    }

    // ── Quick Report Buttons ──────────────────────────────────────────
    private void todaysIssues() {
        reportTypeCombo.setValue("Issue/Return Summary");
        if (reportFromDate != null) reportFromDate.setValue(LocalDate.now());
        if (reportToDate != null) reportToDate.setValue(LocalDate.now());
        if (reportBranchCombo != null && reportBranchCombo.getItems().contains("All Branches")) {
            reportBranchCombo.setValue("All Branches");
        }
        generateReport();
    }

    private void thisWeek() {
        reportTypeCombo.setValue("Issue/Return Summary");
        if (reportFromDate != null) reportFromDate.setValue(LocalDate.now().minusDays(7));
        if (reportToDate != null) reportToDate.setValue(LocalDate.now());
        if (reportBranchCombo != null && reportBranchCombo.getItems().contains("All Branches")) {
            reportBranchCombo.setValue("All Branches");
        }
        generateReport();
    }

    private void thisMonth() {
        reportTypeCombo.setValue("Issue/Return Summary");
        if (reportFromDate != null) reportFromDate.setValue(LocalDate.now().withDayOfMonth(1));
        if (reportToDate != null) reportToDate.setValue(LocalDate.now());
        if (reportBranchCombo != null && reportBranchCombo.getItems().contains("All Branches")) {
            reportBranchCombo.setValue("All Branches");
        }
        generateReport();
    }

    private void overdueAlert() {
        reportTypeCombo.setValue("Overdue Alert");
        if (reportFromDate != null) reportFromDate.setValue(LocalDate.now().minusYears(1));
        if (reportToDate != null) reportToDate.setValue(LocalDate.now());
        if (reportBranchCombo != null && reportBranchCombo.getItems().contains("All Branches")) {
            reportBranchCombo.setValue("All Branches");
        }
        generateReport();
    }

    private void fineCollection() {
        reportTypeCombo.setValue("Fine Collection Report");
        if (reportFromDate != null) reportFromDate.setValue(LocalDate.now().withDayOfMonth(1));
        if (reportToDate != null) reportToDate.setValue(LocalDate.now());
        if (reportBranchCombo != null && reportBranchCombo.getItems().contains("All Branches")) {
            reportBranchCombo.setValue("All Branches");
        }
        generateReport();
    }

    private void memberStats() {
        reportTypeCombo.setValue("Member Statistics");
        if (reportBranchCombo != null && reportBranchCombo.getItems().contains("All Branches")) {
            reportBranchCombo.setValue("All Branches");
        }
        generateReport();
    }

    private void inventory() {
        reportTypeCombo.setValue("Inventory Report");
        if (reportBranchCombo != null && reportBranchCombo.getItems().contains("All Branches")) {
            reportBranchCombo.setValue("All Branches");
        }
        generateReport();
    }

    private void adminActivity() {
        reportTypeCombo.setValue("Admin Activity Report");
        if (reportBranchCombo != null && reportBranchCombo.getItems().contains("All Branches")) {
            reportBranchCombo.setValue("All Branches");
        }
        generateReport();
    }

    @FXML private void previewReport() { generateReport(); }
    @FXML private void generateTodayIssues() { todaysIssues(); }
    @FXML private void generateWeekSummary() { thisWeek(); }
    @FXML private void generateMonthSummary() { thisMonth(); }
    @FXML private void generateOverdueAlert() { overdueAlert(); }
    @FXML private void generateFineReport() { fineCollection(); }
    @FXML private void generateAdminReport() { adminActivity(); }
    @FXML private void generateMemberStats() { memberStats(); }
    @FXML private void generateInventoryReport() { inventory(); }

    // ── Main Report Generation ────────────────────────────────────────
    @FXML
    private void generateReport() {
        // Validate inputs
        if (reportTypeCombo == null || reportTypeCombo.getValue() == null) {
            showError("Please select a report type");
            return;
        }

        LocalDate fromDate = reportFromDate != null && reportFromDate.getValue() != null
            ? reportFromDate.getValue()
            : LocalDate.now().minusDays(7);

        LocalDate toDate = reportToDate != null && reportToDate.getValue() != null
            ? reportToDate.getValue()
            : LocalDate.now();

        if (fromDate.isAfter(toDate)) {
            showError("From date must be before To date");
            return;
        }

        String reportType = reportTypeCombo.getValue();
        String branch = reportBranchCombo != null && reportBranchCombo.getValue() != null
            ? reportBranchCombo.getValue()
            : "All Branches";
        String format = reportFormatCombo != null && reportFormatCombo.getValue() != null
            ? reportFormatCombo.getValue()
            : "Excel";

        // Run in background thread
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Query database
                    String[] columnNames = new String[]{};
                    ObservableList<ObservableList<String>> data =
                        FXCollections.observableArrayList();

                    switch (reportType) {
                        case "Issue/Return Summary" ->
                            data.addAll(getIssueReturnSummary(fromDate, toDate, branch));
                        case "Overdue Alert" ->
                            data.addAll(getOverdueAlert(branch));
                        case "Member Statistics" ->
                            data.addAll(getMemberStatistics(branch));
                        case "Inventory Report" ->
                            data.addAll(getInventoryReport(branch));
                        case "Fine Collection Report" ->
                            data.addAll(getFineCollectionReport(fromDate, toDate, branch));
                        case "Admin Activity Report" ->
                            data.addAll(getAdminActivityReport(branch));
                    }

                    final String[] finalColumnNames = getColumnNames(reportType);

                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        try {
                            displayPreview(data, finalColumnNames);

                            if (!data.isEmpty()) {
                                String filePath = saveReport(reportType, format, finalColumnNames, data);
                                if (filePath != null && new File(filePath).exists()) {
                                    addToHistory(reportType, format, data.size(), filePath);
                                    showSuccessDialog(reportType, format, data.size(), filePath);
                                } else {
                                    showError("Report file could not be saved");
                                }
                            } else {
                                setRowCountLabel("No records found for selected filters");
                            }
                        } catch (Exception e) {
                            showError("Error displaying report: " + e.getMessage());
                        }
                    });
                } catch (SQLException e) {
                    Platform.runLater(() ->
                        showError("Database error: " + e.getMessage()));
                } catch (Exception e) {
                    Platform.runLater(() ->
                        showError("Unexpected error: " + e.getMessage()));
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private String[] getColumnNames(String reportType) {
        return switch (reportType) {
            case "Issue/Return Summary" -> new String[]{"Accession", "Book Title", "Member", "Branch", "Issue Date", "Due Date", "Return Date", "Fine (Rs.)", "Status"};
            case "Overdue Alert" -> new String[]{"Accession", "Book Title", "Member", "Phone", "Branch", "Due Date", "Days Overdue", "Fine (Rs.)"};
            case "Member Statistics" -> new String[]{"Member ID", "Name", "Type", "Department", "Branch", "Total Issues", "Joined Date"};
            case "Inventory Report" -> new String[]{"Accession No.", "Title", "Author", "ISBN", "Category", "Branch", "Total Copies", "Available", "Publisher", "Year"};
            case "Fine Collection Report" -> new String[]{"Member", "Book Title", "Accession", "Due Date", "Return Date", "Fine (Rs.)", "Branch", "Status"};
            case "Admin Activity Report" -> new String[]{"User ID", "Username", "Role", "Branch", "Created Date"};
            default -> new String[]{};
        };
    }

    // ── Display Preview ───────────────────────────────────────────────
    private void displayPreview(ObservableList<ObservableList<String>> data,
                                String[] columnNames) {
        reportPreviewTable.getColumns().clear();
        reportPreviewTable.getItems().clear();

        for (int i = 0; i < columnNames.length; i++) {
            final int idx = i;
            TableColumn<ObservableList<String>, String> col =
                new TableColumn<>(columnNames[i]);
            col.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                    cellData.getValue().size() > idx
                        ? cellData.getValue().get(idx)
                        : "—"
                )
            );
            col.setPrefWidth(140);
            reportPreviewTable.getColumns().add(col);
        }

        reportPreviewTable.setItems(data);
        setRowCountLabel("Showing " + data.size() + " records");
    }

    // ── DB Queries ────────────────────────────────────────────────────
    private ObservableList<ObservableList<String>> getIssueReturnSummary(
            LocalDate fromDate, LocalDate toDate, String branch) throws SQLException {

        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

        String sql = """
            SELECT ir.accession_number, b.title, m.name, br.name,
                   ir.issue_date, ir.due_date, 
                   COALESCE(ir.return_date, '—'),
                   CAST(ROUND(ir.fine_amount, 2) AS TEXT), 
                   ir.status
            FROM issue_records ir
            JOIN books b ON b.id = ir.book_id
            JOIN members m ON m.id = ir.member_id
            LEFT JOIN branches br ON br.id = ir.branch_id
            WHERE ir.issue_date BETWEEN ? AND ?
            AND (br.name = ? OR ? = 'All Branches')
            %s
            ORDER BY ir.issue_date DESC
            """.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fromDate.toString());
            stmt.setString(2, toDate.toString());
            stmt.setString(3, branch);
            stmt.setString(4, branch);
            BranchScope.bind(stmt, 5);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList(
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7), rs.getString(8), rs.getString(9)
                );
                data.add(row);
            }
        }
        return data;
    }

    private ObservableList<ObservableList<String>> getOverdueAlert(
            String branch) throws SQLException {

        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

        String sql = """
            SELECT ir.accession_number, b.title, m.name, 
                   COALESCE(m.phone,'—'), br.name, ir.due_date,
                   CAST(CAST(julianday('now') - 
                        julianday(ir.due_date) AS INTEGER) AS TEXT),
                   CAST(ROUND(ir.fine_amount,2) AS TEXT)
            FROM issue_records ir
            JOIN books b ON b.id = ir.book_id
            JOIN members m ON m.id = ir.member_id
            LEFT JOIN branches br ON br.id = ir.branch_id
            WHERE ir.status = 'ISSUED'
            AND ir.due_date < DATE('now')
            AND (br.name = ? OR ? = 'All Branches')
            %s
            ORDER BY ir.due_date ASC
            """.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, branch);
            stmt.setString(2, branch);
            BranchScope.bind(stmt, 3);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList(
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7), rs.getString(8)
                );
                data.add(row);
            }
        }
        return data;
    }

    private ObservableList<ObservableList<String>> getMemberStatistics(
            String branch) throws SQLException {

        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

        String sql = """
            SELECT m.member_id, m.name, 
                   COALESCE(m.member_type,'Student'),
                   COALESCE(m.department,'—'), 
                   COALESCE(br.name,'—'),
                   CAST(COUNT(ir.id) AS TEXT),
                   COALESCE(m.joined_at, '—')
            FROM members m
            LEFT JOIN issue_records ir ON ir.member_id = m.id
            LEFT JOIN branches br ON br.id = m.branch_id
            WHERE m.active = 1
            AND (br.name = ? OR ? = 'All Branches')
            %s
            GROUP BY m.id
            ORDER BY COUNT(ir.id) DESC
            """.formatted(BranchScope.isScoped() ? "AND m.branch_id = ?" : "");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, branch);
            stmt.setString(2, branch);
            BranchScope.bind(stmt, 3);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList(
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7)
                );
                data.add(row);
            }
        }
        return data;
    }

    private ObservableList<ObservableList<String>> getInventoryReport(
            String branch) throws SQLException {

        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

        String sql = """
            SELECT COALESCE(b.accession_number,'—'), 
                   b.title, b.author,
                   COALESCE(b.isbn,'—'), 
                   COALESCE(b.category,'—'),
                   COALESCE(br.name,'—'),
                   CAST(b.total_copies AS TEXT),
                   CAST(b.available_copies AS TEXT),
                   COALESCE(b.publisher,'—'),
                   CAST(b.year_of_publication AS TEXT)
            FROM books b
            LEFT JOIN branches br ON br.id = b.branch_id
            WHERE (br.name = ? OR ? = 'All Branches')
            %s
            ORDER BY b.title
            """.formatted(BranchScope.isScoped() ? "AND b.branch_id = ?" : "");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, branch);
            stmt.setString(2, branch);
            BranchScope.bind(stmt, 3);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList(
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7), rs.getString(8), rs.getString(9),
                    rs.getString(10)
                );
                data.add(row);
            }
        }
        return data;
    }

    private ObservableList<ObservableList<String>> getFineCollectionReport(
            LocalDate fromDate, LocalDate toDate, String branch)
            throws SQLException {

        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

        String sql = """
            SELECT m.name, b.title, 
                   COALESCE(ir.accession_number,'—'),
                   ir.due_date, 
                   COALESCE(ir.return_date,'—'),
                   CAST(ROUND(ir.fine_amount,2) AS TEXT),
                   COALESCE(br.name,'—'), ir.status
            FROM issue_records ir
            JOIN members m ON m.id = ir.member_id
            JOIN books b ON b.id = ir.book_id
            LEFT JOIN branches br ON br.id = ir.branch_id
            WHERE ir.fine_amount > 0
            AND ir.issue_date BETWEEN ? AND ?
            AND (br.name = ? OR ? = 'All Branches')
            %s
            ORDER BY ir.fine_amount DESC
            """.formatted(BranchScope.isScoped() ? "AND ir.branch_id = ?" : "");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fromDate.toString());
            stmt.setString(2, toDate.toString());
            stmt.setString(3, branch);
            stmt.setString(4, branch);
            BranchScope.bind(stmt, 5);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList(
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7), rs.getString(8)
                );
                data.add(row);
            }
        }
        return data;
    }

    private ObservableList<ObservableList<String>> getAdminActivityReport(
            String branch) throws SQLException {

        ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();

        String sql = """
            SELECT CAST(u.id AS TEXT), u.username, u.role,
                   COALESCE(br.name,'—'), COALESCE(u.created_at, '—')
            FROM users u
            LEFT JOIN branches br ON br.id = u.branch_id
            WHERE u.role IN ('ADMIN','LIBRARIAN')
            AND (br.name = ? OR ? = 'All Branches')
            %s
            ORDER BY u.created_at DESC
            """.formatted(BranchScope.isScoped() ? "AND u.branch_id = ?" : "");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, branch);
            stmt.setString(2, branch);
            BranchScope.bind(stmt, 3);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ObservableList<String> row = FXCollections.observableArrayList(
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5)
                );
                data.add(row);
            }
        }
        return data;
    }

    // ── File Generation ──────────────────────────────────────────────
    private String saveReport(String reportType, String format,
                              String[] columnNames,
                              ObservableList<ObservableList<String>> data) {
        try {
            String safeName = reportType.replaceAll("[^a-zA-Z0-9]", "_");
            String dateStr = LocalDate.now().toString();
            String filename = safeName + "_" + dateStr;

            return switch (format) {
                case "Excel" -> saveExcel(filename, columnNames, data);
                case "CSV" -> saveCsv(filename, columnNames, data);
                case "PDF" -> savePdf(reportType, filename, columnNames, data);
                default -> null;
            };
        } catch (IOException | com.lowagie.text.DocumentException e) {
            showError("Could not save file: " + e.getMessage());
            return null;
        }
    }

    private String saveExcel(String filename, String[] columnNames,
                             ObservableList<ObservableList<String>> data)
            throws IOException, com.lowagie.text.DocumentException {
        String filePath = REPORTS_BASE + "/excel/" + filename + ".xlsx";
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Report");

        // Header row styling
        CellStyle headerStyle = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columnNames.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnNames[i]);
            cell.setCellStyle(headerStyle);
            sheet.autoSizeColumn(i);
        }

        // Data rows
        for (int r = 0; r < data.size(); r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < data.get(r).size(); c++) {
                row.createCell(c).setCellValue(data.get(r).get(c));
            }
        }

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            wb.write(fos);
        }
        wb.close();
        return filePath;
    }

    private String saveCsv(String filename, String[] columnNames,
                           ObservableList<ObservableList<String>> data)
            throws IOException, com.lowagie.text.DocumentException {
        String filePath = REPORTS_BASE + "/csv/" + filename + ".csv";
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(
                        new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            pw.println(String.join(",", columnNames));
            for (ObservableList<String> row : data) {
                pw.println(row.stream()
                        .map(v -> "\"" + v.replace("\"", "\"\"") + "\"")
                        .collect(Collectors.joining(",")));
            }
        }
        return filePath;
    }

    private String savePdf(String reportType, String filename, String[] columnNames,
                           ObservableList<ObservableList<String>> data)
            throws IOException, com.lowagie.text.DocumentException {
        String filePath = REPORTS_BASE + "/pdf/" + filename + ".pdf";
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, new FileOutputStream(filePath));
        doc.open();

        // Title
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        doc.add(new Paragraph(reportType + " — " + LocalDate.now(), titleFont));
        doc.add(new Paragraph("Branch: " + reportBranchCombo.getValue() +
            "  |  From: " + reportFromDate.getValue() +
            "  To: " + reportToDate.getValue()));
        doc.add(Chunk.NEWLINE);

        // Table
        PdfPTable table = new PdfPTable(columnNames.length);
        table.setWidthPercentage(100);

        // Header cells
        for (String col : columnNames) {
            PdfPCell cell = new PdfPCell(new Phrase(col,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            cell.setBackgroundColor(new java.awt.Color(79, 70, 229));
            cell.setPadding(3);
            table.addCell(cell);
        }

        // Data cells
        for (ObservableList<String> row : data) {
            for (String val : row) {
                table.addCell(new Phrase(val,
                        FontFactory.getFont(FontFactory.HELVETICA, 8)));
            }
        }

        doc.add(table);
        doc.add(new Paragraph("\nTotal Records: " + data.size()));
        doc.close();
        return filePath;
    }

    // ── History & Success ────────────────────────────────────────────
    private void addToHistory(String reportType, String format, int rowCount,
                              String filePath) {
        String username = "Unknown";
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser != null) {
            username = currentUser.getUsername();
        }
        ReportRecord record = new ReportRecord(
                reportType + " (" + LocalDate.now() + ")",
                reportType,
                format,
                username,
                LocalDateTime.now(),
                rowCount + " rows",
                filePath
        );
        recentReports.add(0, record);
        reportCount++;
        monthReportCount++;
        updateStatistics();
    }

    private void showSuccessDialog(String reportType, String format, int rowCount,
                                   String filePath) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("✓ Report Generated");
        alert.setHeaderText(reportType + " — " + rowCount + " records");
        alert.setContentText("Saved to:\n" + filePath);

        ButtonType openFileBtn = new ButtonType("📂 Open File");
        ButtonType openFolderBtn = new ButtonType("📁 Open Folder");
        ButtonType okBtn = new ButtonType("OK", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(openFileBtn, openFolderBtn, okBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == openFileBtn) {
                openFile(new File(filePath));
            } else if (result.get() == openFolderBtn) {
                openFile(new File(filePath).getParentFile());
            }
        }
    }

    private void openFile(File file) {
        if (file == null || !file.exists()) return;
        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            showError("Could not open file: " + e.getMessage());
        }
    }

    private void updateStatistics() {
        totalReportsLabel.setText(String.valueOf(reportCount));

        YearMonth now = YearMonth.now();
        long monthCount = recentReports.stream()
                .filter(r -> {
                    YearMonth rMonth = YearMonth.from(r.getTimestamp());
                    return rMonth.equals(now);
                })
                .count();
        monthlyReportsLabel.setText(String.valueOf(monthCount));
        totalDownloadsLabel.setText(String.valueOf(reportCount));
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("⚠ Report Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void setRowCountLabel(String text) {
        if (rowCountLabel != null) {
            rowCountLabel.setText(text);
        }
    }
}

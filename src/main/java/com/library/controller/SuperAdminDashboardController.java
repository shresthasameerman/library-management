package com.library.controller;

import com.library.database.DatabaseConnection;
import com.library.util.SessionManager;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class SuperAdminDashboardController {

    @FXML private Label lastUpdatedLabel;
    @FXML private Label statusLabel;
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Label loggedInUserLabel;

    @FXML private Button menuDashboard;
    @FXML private Button menuIssueReturn;
    @FXML private Button menuBranchAnalytics;
    @FXML private Button menuUserManagement;
    @FXML private Button menuBranchManagement;
    @FXML private Button menuReports;
    @FXML private Button menuSystemManagement;
    @FXML private Button menuNotifications;

    @FXML private StackPane dashboardPane;
    @FXML private StackPane issueReturnPane;
    @FXML private StackPane branchAnalyticsPane;
    @FXML private StackPane userManagementPane;
    @FXML private StackPane branchManagementPane;
    @FXML private StackPane reportsPane;
    @FXML private StackPane systemManagementPane;
    @FXML private StackPane notificationsPane;

    @FXML private ProgressIndicator loadingIndicator;

    @FXML private Label totalBooksValueLabel;
    @FXML private Label booksAvailableValueLabel;
    @FXML private Label totalMembersValueLabel;
    @FXML private Label activeIssuesValueLabel;
    @FXML private Label overdueBooksValueLabel;
    @FXML private Label totalFinesValueLabel;
    @FXML private Label totalBranchesValueLabel;
    @FXML private Label totalUsersValueLabel;

    @FXML private TextField issueSearchField;
    @FXML private ComboBox<String> issueBranchFilterCombo;
    @FXML private TableView<IssueRow> issueReturnTable;
    @FXML private TableColumn<IssueRow, String> accessionColumn;
    @FXML private TableColumn<IssueRow, String> bookTitleColumn;
    @FXML private TableColumn<IssueRow, String> memberNameColumn;
    @FXML private TableColumn<IssueRow, String> branchNameColumn;
    @FXML private TableColumn<IssueRow, String> issueDateColumn;
    @FXML private TableColumn<IssueRow, String> dueDateColumn;
    @FXML private TableColumn<IssueRow, String> returnDateColumn;
    @FXML private TableColumn<IssueRow, String> fineAmountColumn;
    @FXML private TableColumn<IssueRow, String> statusColumn;
    @FXML private Label issueEmptyLabel;

    private final ObservableList<IssueRow> issueRows = FXCollections.observableArrayList();
    private final Map<StackPane, Boolean> loadedPanes = new HashMap<>();

    @FXML
    public void initialize() {
        setupIssueTable();
        setupUserBadge();
        setupLoadedMap();
        loadBranchesForFilter();
        showDashboard();
        refreshAllData();
    }

    private void setupLoadedMap() {
        loadedPanes.put(branchAnalyticsPane, false);
        loadedPanes.put(userManagementPane, false);
        loadedPanes.put(branchManagementPane, false);
        loadedPanes.put(reportsPane, false);
        loadedPanes.put(systemManagementPane, false);
        loadedPanes.put(notificationsPane, false);
    }

    private void setupUserBadge() {
        if (SessionManager.getCurrentUser() != null) {
            loggedInUserLabel.setText(SessionManager.getCurrentUser().getUsername() + " (SUPERADMIN)");
        }
    }

    private void setupIssueTable() {
        accessionColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().accessionNumber));
        bookTitleColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().bookTitle));
        memberNameColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().memberName));
        branchNameColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().branchName));
        issueDateColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().issueDate));
        dueDateColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().dueDate));
        returnDateColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().returnDate));
        fineAmountColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().fineAmount));
        statusColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().status));

        fineAmountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(item);
                try {
                    double value = Double.parseDouble(item.replace("Rs.", "").trim());
                    if (value > 0) {
                        setStyle("-fx-text-fill: #f97316; -fx-font-weight: 700;");
                    } else {
                        setStyle("");
                    }
                } catch (NumberFormatException e) {
                    setStyle("");
                }
            }
        });

        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label pill = new Label(item);
                pill.getStyleClass().addAll("status-pill");
                if ("RETURNED".equalsIgnoreCase(item)) {
                    pill.getStyleClass().add("status-returned");
                } else if ("OVERDUE".equalsIgnoreCase(item)) {
                    pill.getStyleClass().add("status-overdue");
                } else {
                    pill.getStyleClass().add("status-issued");
                }

                setText(null);
                setGraphic(pill);
            }
        });

        issueReturnTable.setItems(issueRows);
    }

    @FXML
    private void refreshAllData() {
        setStatusMessage("Refreshing dashboard...");
        runWithLoading(() -> {
            reloadLazyPanes();
            loadStatsAndIssues();
        }, "Data refreshed");
    }

    private void reloadLazyPanes() {
        Map<StackPane, String> pathByPane = new HashMap<>();
        pathByPane.put(branchAnalyticsPane, "/com/library/fxml/BranchAnalyticsView.fxml");
        pathByPane.put(userManagementPane, "/com/library/fxml/UserOversightView.fxml");
        pathByPane.put(branchManagementPane, "/com/library/fxml/BranchManagementView.fxml");
        pathByPane.put(reportsPane, "/com/library/fxml/ReportsView.fxml");
        pathByPane.put(systemManagementPane, "/com/library/fxml/SystemManagementView.fxml");
        pathByPane.put(notificationsPane, "/com/library/fxml/NotificationsView.fxml");

        for (Map.Entry<StackPane, String> entry : pathByPane.entrySet()) {
            StackPane pane = entry.getKey();
            String path = entry.getValue();
            if (!Boolean.TRUE.equals(loadedPanes.get(pane))) {
                continue;
            }

            try {
                Node content = FXMLLoader.load(getClass().getResource(path));
                pane.getChildren().setAll(content);
            } catch (IOException ignored) {
                // Keep previous content if reload fails.
            }
        }
    }

    @FXML
    private void showDashboard() {
        setPageMeta("Dashboard", "System-wide insights across all branches");
        showPane(dashboardPane, menuDashboard);
    }

    @FXML
    private void showIssueReturn() {
        setPageMeta("Issue/Return", "Track all circulation records across branches");
        showPane(issueReturnPane, menuIssueReturn);
        if (issueRows.isEmpty()) {
            runWithLoading(this::loadIssueRowsWithCurrentFilters, "Issue records loaded");
        }
    }

    @FXML
    private void showBranchAnalytics() {
        setPageMeta("Branch Analytics", "Analyze branch-wise trends and performance");
        showPane(branchAnalyticsPane, menuBranchAnalytics);
        ensurePaneLoaded(branchAnalyticsPane, "/com/library/fxml/BranchAnalyticsView.fxml");
    }

    @FXML
    private void showUserManagement() {
        setPageMeta("User Management", "Oversee admins and librarian activity");
        showPane(userManagementPane, menuUserManagement);
        ensurePaneLoaded(userManagementPane, "/com/library/fxml/UserOversightView.fxml");
    }

    @FXML
    private void showBranchManagement() {
        setPageMeta("Branch Management", "Configure branches and assignments");
        showPane(branchManagementPane, menuBranchManagement);
        ensurePaneLoaded(branchManagementPane, "/com/library/fxml/BranchManagementView.fxml");
    }

    @FXML
    private void showReports() {
        setPageMeta("Reports", "Generate and export system reports");
        showPane(reportsPane, menuReports);
        ensurePaneLoaded(reportsPane, "/com/library/fxml/ReportsView.fxml");
    }

    @FXML
    private void showSystemManagement() {
        setPageMeta("System Management", "Monitor system health and backups");
        showPane(systemManagementPane, menuSystemManagement);
        ensurePaneLoaded(systemManagementPane, "/com/library/fxml/SystemManagementView.fxml");
    }

    @FXML
    private void showNotifications() {
        setPageMeta("Notifications", "Monitor alerts and overdue thresholds");
        showPane(notificationsPane, menuNotifications);
        ensurePaneLoaded(notificationsPane, "/com/library/fxml/NotificationsView.fxml");
    }

    private void showPane(StackPane pane, Button activeButton) {
        StackPane[] panes = {
            dashboardPane, issueReturnPane, branchAnalyticsPane,
            userManagementPane, branchManagementPane,
            reportsPane, systemManagementPane, notificationsPane
        };

        Button[] buttons = {
            menuDashboard, menuIssueReturn, menuBranchAnalytics,
            menuUserManagement, menuBranchManagement,
            menuReports, menuSystemManagement, menuNotifications
        };

        for (StackPane current : panes) {
            current.setVisible(current == pane);
            current.setManaged(current == pane);
        }

        for (Button button : buttons) {
            button.getStyleClass().remove("sidebar-item-active");
        }
        activeButton.getStyleClass().add("sidebar-item-active");
    }

    private void ensurePaneLoaded(StackPane pane, String fxmlPath) {
        if (Boolean.TRUE.equals(loadedPanes.get(pane))) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node content = loader.load();
            pane.getChildren().setAll(content);
            loadedPanes.put(pane, true);
            setStatusMessage("Loaded section");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load: " + fxmlPath);
            System.err.println("Cause: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Root cause: " + e.getCause().getMessage());
                e.getCause().printStackTrace();
            }
            setStatusMessage("Failed to load section: " + e.getMessage());

            Label errorLabel = new Label(
                "⚠ Failed to load Reports\n\n" +
                "Reason: " + (e.getCause() != null
                    ? e.getCause().getMessage()
                    : e.getMessage())
            );
            errorLabel.setStyle(
                "-fx-text-fill: #ef4444; -fx-font-size: 14px;"
            );
            pane.getChildren().setAll(errorLabel);
        }
    }

    private void setPageMeta(String title, String subtitle) {
        pageTitleLabel.setText(title);
        pageSubtitleLabel.setText(subtitle);
    }

    @FXML
    private void applyIssueFilters() {
        runWithLoading(this::loadIssueRowsWithCurrentFilters, "Issue records filtered");
    }

    @FXML
    private void resetIssueFilters() {
        issueSearchField.clear();
        issueBranchFilterCombo.setValue("All Branches");
        runWithLoading(this::loadIssueRowsWithCurrentFilters, "Filters reset");
    }

    @FXML
    private void exportCsv() {
        try (PrintWriter writer = new PrintWriter("superadmin_issue_return.csv")) {
            writer.println("Accession Number,Book Title,Member,Branch,Issue Date,Due Date,Return Date,Fine,Status");
            for (IssueRow row : issueRows) {
                writer.println(String.join(",",
                    csv(row.accessionNumber),
                    csv(row.bookTitle),
                    csv(row.memberName),
                    csv(row.branchName),
                    csv(row.issueDate),
                    csv(row.dueDate),
                    csv(row.returnDate),
                    csv(row.fineAmount),
                    csv(row.status)
                ));
            }
            setStatusMessage("CSV exported");
        } catch (Exception e) {
            showInfo("Export failed", "Could not export CSV: " + e.getMessage());
        }
    }

    @FXML
    private void exportPdf() {
        showInfo("PDF Export", "PDF export is available in the Reports section.");
    }

    @FXML
    private void printIssueTable() {
        showInfo("Print", "Print command sent for issue table.");
    }

    private String csv(String text) {
        if (text == null) return "";
        if (text.contains(",") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    @FXML
    private void logout() {
        try {
            SessionManager.logout();
            Stage stage = (Stage) lastUpdatedLabel.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            setStatusMessage("Logout failed: " + e.getMessage());
        }
    }

    private void runWithLoading(Runnable dbTask, String successMessage) {
        loadingIndicator.setVisible(true);
        loadingIndicator.setManaged(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                dbTask.run();
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);
            updateLastUpdatedTime();
            setStatusMessage(successMessage);
        });

        task.setOnFailed(event -> {
            loadingIndicator.setVisible(false);
            loadingIndicator.setManaged(false);
            setStatusMessage("Failed to load data");
            showInfo("Load Error", task.getException() != null
                ? task.getException().getMessage()
                : "Unknown error");
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void loadStatsAndIssues() {
        DashboardStats stats = queryDashboardStats();
        ObservableList<IssueRow> rows = queryIssueRows(
            issueSearchField != null ? issueSearchField.getText().trim() : "",
            issueBranchFilterCombo != null ? issueBranchFilterCombo.getValue() : "All Branches"
        );

        Platform.runLater(() -> {
            totalBooksValueLabel.setText(String.valueOf(stats.totalBooks));
            booksAvailableValueLabel.setText(String.valueOf(stats.booksAvailable));
            totalMembersValueLabel.setText(String.valueOf(stats.totalMembers));
            activeIssuesValueLabel.setText(String.valueOf(stats.activeIssues));
            overdueBooksValueLabel.setText(String.valueOf(stats.overdueBooks));
            totalFinesValueLabel.setText("Rs. " + String.format("%.2f", stats.totalFines));
            totalBranchesValueLabel.setText(String.valueOf(stats.totalBranches));
            totalUsersValueLabel.setText(String.valueOf(stats.totalUsers));

            issueRows.setAll(rows);
            issueEmptyLabel.setText(rows.isEmpty() ? "No data" : "");
        });
    }

    private void loadIssueRowsWithCurrentFilters() {
        ObservableList<IssueRow> rows = queryIssueRows(
            issueSearchField.getText().trim(),
            issueBranchFilterCombo.getValue()
        );

        Platform.runLater(() -> {
            issueRows.setAll(rows);
            issueEmptyLabel.setText(rows.isEmpty() ? "No data" : "");
        });
    }

    private DashboardStats queryDashboardStats() {
        DashboardStats stats = new DashboardStats();

        try {
            Connection conn = DatabaseConnection.getConnection();
            stats.totalBooks = queryLong(conn, "SELECT COALESCE(SUM(total_copies),0) FROM books");
            stats.booksAvailable = queryLong(conn, "SELECT COALESCE(SUM(available_copies),0) FROM books");
            stats.totalMembers = queryLong(conn, "SELECT COUNT(*) FROM members WHERE active=1");
            stats.activeIssues = queryLong(conn, "SELECT COUNT(*) FROM issue_records WHERE status='ISSUED'");
            stats.overdueBooks = queryLong(conn, "SELECT COUNT(*) FROM issue_records WHERE status='OVERDUE'");
            stats.totalFines = queryDouble(conn, "SELECT COALESCE(SUM(fine_amount),0) FROM issue_records WHERE status='OVERDUE'");
            stats.totalBranches = queryLong(conn, "SELECT COUNT(*) FROM branches WHERE active=1");
            stats.totalUsers = queryLong(conn, "SELECT COUNT(*) FROM users");
        } catch (Exception ignored) {
            // Graceful empty state.
        }

        return stats;
    }

    private long queryLong(Connection conn, String sql) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private double queryDouble(Connection conn, String sql) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    private ObservableList<IssueRow> queryIssueRows(String search, String branchName) {
        ObservableList<IssueRow> rows = FXCollections.observableArrayList();

        StringBuilder sql = new StringBuilder("""
                        SELECT ir.accession_number,
                   b.title,
                   m.name,
                   COALESCE(br.name, 'N/A') AS branch_name,
                   ir.issue_date,
                   ir.due_date,
                   ir.return_date,
                   ir.fine_amount,
                   ir.status
            FROM issue_records ir
            JOIN books b ON b.id = ir.book_id
            JOIN members m ON m.id = ir.member_id
            LEFT JOIN branches br ON br.id = ir.branch_id
            WHERE 1=1
        """);

        boolean hasSearch = search != null && !search.isBlank();
        boolean hasBranch = branchName != null && !branchName.isBlank() && !"All Branches".equals(branchName);

        if (hasSearch) {
            sql.append(" AND (");
            sql.append("ir.accession_number LIKE ? OR ");
            sql.append("b.title LIKE ? OR ");
            sql.append("m.name LIKE ?");
            sql.append(") ");
        }

        if (hasBranch) {
            sql.append(" AND br.name = ? ");
        }

        sql.append(" ORDER BY ir.issue_date DESC");

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int index = 1;
                if (hasSearch) {
                    String like = "%" + search + "%";
                    stmt.setString(index++, like);
                    stmt.setString(index++, like);
                    stmt.setString(index++, like);
                }
                if (hasBranch) {
                    stmt.setString(index, branchName);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new IssueRow(
                            valueOf(rs.getString("accession_number")),
                            valueOf(rs.getString("title")),
                            valueOf(rs.getString("name")),
                            valueOf(rs.getString("branch_name")),
                            valueOf(rs.getString("issue_date")),
                            valueOf(rs.getString("due_date")),
                            valueOf(rs.getString("return_date")),
                            String.format("%.2f", rs.getDouble("fine_amount")),
                            valueOf(rs.getString("status"))
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
            // Graceful empty state.
        }

        return rows;
    }

    private void loadBranchesForFilter() {
        ObservableList<String> branches = FXCollections.observableArrayList("All Branches");
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT name FROM branches WHERE active=1 ORDER BY name"
            ); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    branches.add(valueOf(rs.getString("name")));
                }
            }
        } catch (Exception ignored) {
            // Fallback keeps only All Branches.
        }

        issueBranchFilterCombo.setItems(branches);
        issueBranchFilterCombo.setValue("All Branches");
    }

    private String valueOf(String text) {
        return text == null ? "" : text;
    }

    private void updateLastUpdatedTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        lastUpdatedLabel.setText("Last Updated: " + now.format(formatter));
    }

    private void setStatusMessage(String message) {
        statusLabel.setText(message);
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void onDashboardFocused() {
        refreshAllData();
    }

    private static class DashboardStats {
        long totalBooks;
        long booksAvailable;
        long totalMembers;
        long activeIssues;
        long overdueBooks;
        long totalBranches;
        long totalUsers;
        double totalFines;
    }

    public static class IssueRow {
        final String accessionNumber;
        final String bookTitle;
        final String memberName;
        final String branchName;
        final String issueDate;
        final String dueDate;
        final String returnDate;
        final String fineAmount;
        final String status;

        public IssueRow(
            String accessionNumber,
            String bookTitle,
            String memberName,
            String branchName,
            String issueDate,
            String dueDate,
            String returnDate,
            String fineAmount,
            String status
        ) {
            this.accessionNumber = accessionNumber;
            this.bookTitle = bookTitle;
            this.memberName = memberName;
            this.branchName = branchName;
            this.issueDate = issueDate;
            this.dueDate = dueDate;
            this.returnDate = returnDate;
            this.fineAmount = fineAmount;
            this.status = status;
        }
    }
}

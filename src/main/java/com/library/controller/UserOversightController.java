package com.library.controller;

import com.library.database.DatabaseConnection;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserOversightController {

    @FXML private ComboBox<String> branchFilterCombo;
    @FXML private ComboBox<String> roleFilterCombo;
    @FXML private ComboBox<String> statusFilterCombo;

    @FXML private Label totalAdminsLabel;
    @FXML private Label activeAdminsLabel;
    @FXML private Label inactiveAdminsLabel;

    @FXML private TableView<AdminRecord> adminTable;
    @FXML private TableColumn<AdminRecord, String> userIdColumn;
    @FXML private TableColumn<AdminRecord, String> usernameColumn;
    @FXML private TableColumn<AdminRecord, String> roleColumn;
    @FXML private TableColumn<AdminRecord, String> branchColumn;
    @FXML private TableColumn<AdminRecord, String> lastLoginColumn;
    @FXML private TableColumn<AdminRecord, String> statusColumn;
    @FXML private TableColumn<AdminRecord, String> actionsColumn;

    @FXML private TableView<ActivityLog> activityLogsTable;
    @FXML private TableColumn<ActivityLog, String> activityUserColumn;
    @FXML private TableColumn<ActivityLog, String> actionColumn;
    @FXML private TableColumn<ActivityLog, String> moduleColumn;
    @FXML private TableColumn<ActivityLog, String> timestampColumn;
    @FXML private TableColumn<ActivityLog, String> detailsColumn;

    private final ObservableList<AdminRecord> allAdmins = FXCollections.observableArrayList();
    private final ObservableList<ActivityLog> allLogs = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadBranches();
        loadAdmins();
        loadActivityLogs();
    }

    private void setupTableColumns() {
        userIdColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getUserId()));
        usernameColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getUsername()));
        roleColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getRole()));
        branchColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getBranch()));
        lastLoginColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getLastLogin()));
        statusColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getStatus()));
        actionsColumn.setCellValueFactory(v -> new SimpleStringProperty("—"));

        activityUserColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().user));
        actionColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().action));
        moduleColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().module));
        timestampColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().timestamp));
        detailsColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().details));

        adminTable.setPlaceholder(new Label("No data available"));
        activityLogsTable.setPlaceholder(new Label("No activity logs yet"));
    }

    private void loadBranches() {
        ObservableList<String> branches = FXCollections.observableArrayList("All");
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT name FROM branches WHERE active=1 ORDER BY name"
            ); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    branches.add(safe(rs.getString("name")));
                }
            }
        } catch (Exception ignored) {
            // Keep default branch filter only.
        }

        branchFilterCombo.setItems(branches);
        branchFilterCombo.setValue("All");
        if (roleFilterCombo.getValue() == null) roleFilterCombo.setValue("All");
        if (statusFilterCombo.getValue() == null) statusFilterCombo.setValue("All");
    }

    private void loadAdmins() {
        allAdmins.clear();
        allAdmins.addAll(fetchAdmins(
            branchFilterCombo.getValue(),
            roleFilterCombo.getValue(),
            statusFilterCombo.getValue()
        ));

        adminTable.setItems(allAdmins);
        updateAdminStatistics();
    }

    private List<AdminRecord> fetchAdmins(String branchFilter, String roleFilter, String statusFilter) {
        List<AdminRecord> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT u.id, u.username, u.role,
                   COALESCE(b.name, '—') AS branch_name,
                   u.created_at,
                   'Active' AS status
            FROM users u
            LEFT JOIN branches b ON b.id = u.branch_id
            WHERE u.role IN ('ADMIN', 'LIBRARIAN')
        """);

        boolean filterBranch = branchFilter != null && !branchFilter.isBlank() && !"All".equals(branchFilter);
        boolean filterRole = roleFilter != null && !roleFilter.isBlank() && !"All".equals(roleFilter);
        boolean filterStatus = statusFilter != null && !statusFilter.isBlank() && !"All".equals(statusFilter);

        if (filterBranch) sql.append(" AND b.name = ?");
        if (filterRole) sql.append(" AND u.role = ?");
        if (filterStatus && "Inactive".equalsIgnoreCase(statusFilter)) {
            // No inactive field in schema; this forces an empty result for Inactive filter.
            sql.append(" AND 1 = 0");
        }
        sql.append(" ORDER BY u.created_at DESC");

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int index = 1;
                if (filterBranch) stmt.setString(index++, branchFilter);
                if (filterRole) stmt.setString(index, roleFilter);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new AdminRecord(
                            safe(rs.getString("id")),
                            safe(rs.getString("username")),
                            safe(rs.getString("role")),
                            safe(rs.getString("branch_name")),
                            safe(rs.getString("created_at")),
                            safe(rs.getString("status"))
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }

        return rows;
    }

    private void loadActivityLogs() {
        allLogs.clear();

        try {
            Connection conn = DatabaseConnection.getConnection();
            if (!tableExists(conn, "audit_logs")) {
                activityLogsTable.setItems(allLogs);
                activityLogsTable.setPlaceholder(new Label("No activity logs yet"));
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT COALESCE(user, 'system') AS user,
                       COALESCE(action, '') AS action,
                       COALESCE(module, '') AS module,
                       COALESCE(timestamp, '') AS timestamp,
                       COALESCE(details, '') AS details
                FROM audit_logs
                ORDER BY timestamp DESC
            """); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    allLogs.add(new ActivityLog(
                        safe(rs.getString("user")),
                        safe(rs.getString("action")),
                        safe(rs.getString("module")),
                        safe(rs.getString("timestamp")),
                        safe(rs.getString("details"))
                    ));
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }

        activityLogsTable.setItems(allLogs);
        if (allLogs.isEmpty()) {
            activityLogsTable.setPlaceholder(new Label("No activity logs yet"));
        }
    }

    private boolean tableExists(Connection conn, String name) {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
        )) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    @FXML
    private void refreshAdminList() {
        loadAdmins();
    }

    @FXML
    private void openAddAdminDialog() {
        showAlert("Info", "Add new admin dialog will open here");
    }

    private void updateAdminStatistics() {
        long total = allAdmins.size();
        long active = allAdmins.stream().filter(a -> "Active".equalsIgnoreCase(a.getStatus())).count();
        long inactive = total - active;

        totalAdminsLabel.setText(String.valueOf(total));
        activeAdminsLabel.setText(String.valueOf(active));
        inactiveAdminsLabel.setText(String.valueOf(inactive));
    }

    @FXML
    private void exportAdminList() {
        showAlert("Success", "Admin list export triggered");
    }

    @FXML
    private void exportActivityLogs() {
        showAlert("Success", "Activity logs export triggered");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class AdminRecord {
        private final String userId;
        private final String username;
        private final String role;
        private final String branch;
        private final String lastLogin;
        private final String status;

        public AdminRecord(String userId, String username, String role, String branch, String lastLogin, String status) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.branch = branch;
            this.lastLogin = lastLogin;
            this.status = status;
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public String getBranch() { return branch; }
        public String getLastLogin() { return lastLogin; }
        public String getStatus() { return status; }
    }

    public static class ActivityLog {
        final String user;
        final String action;
        final String module;
        final String timestamp;
        final String details;

        public ActivityLog(String user, String action, String module, String timestamp, String details) {
            this.user = user;
            this.action = action;
            this.module = module;
            this.timestamp = timestamp;
            this.details = details;
        }
    }
}

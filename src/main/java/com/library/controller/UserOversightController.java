package com.library.controller;

import com.library.database.DatabaseConnection;
import com.library.model.User;
import com.library.service.UserAdminService;
import com.library.util.SessionManager;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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
    private final UserAdminService userAdminService = new UserAdminService();
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");

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
        List<BranchChoice> branchChoices = loadActiveBranches();
        if (branchChoices.isEmpty()) {
            showWarning("No Branch Available", "Create a branch first, then add admin accounts.");
            return;
        }

        Dialog<AdminFormResult> dialog = new Dialog<>();
        dialog.setTitle("Create Admin Account");
        dialog.setHeaderText("Add a branch admin or librarian account");

        Window owner = getOwnerWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType createButtonType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        TextField usernameField = new TextField();
        usernameField.setPromptText("e.g. bmc_pokhara_admin");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Minimum 6 characters");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Retype password");

        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.setItems(FXCollections.observableArrayList("ADMIN", "LIBRARIAN"));
        roleCombo.setValue("ADMIN");

        ComboBox<BranchChoice> branchCombo = new ComboBox<>();
        branchCombo.setItems(FXCollections.observableArrayList(branchChoices));
        branchCombo.setPromptText("Select branch");

        Label infoLabel = new Label("Username: 3-30 chars (letters, numbers, ., _, -)");
        infoLabel.setStyle("-fx-text-fill: #576574; -fx-font-size: 11;");

        Label validationLabel = new Label();
        validationLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11;");
        validationLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12, 16, 8, 16));

        grid.add(new Label("Username"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Role"), 0, 1);
        grid.add(roleCombo, 1, 1);
        grid.add(new Label("Branch"), 0, 2);
        grid.add(branchCombo, 1, 2);
        grid.add(new Label("Password"), 0, 3);
        grid.add(passwordField, 1, 3);
        grid.add(new Label("Confirm Password"), 0, 4);
        grid.add(confirmPasswordField, 1, 4);
        grid.add(infoLabel, 1, 5);
        grid.add(validationLabel, 1, 6);

        dialog.getDialogPane().setContent(grid);

        Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        Runnable validateLive = () -> {
            String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
            String password = passwordField.getText() == null ? "" : passwordField.getText();
            String confirm = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

            boolean hasRequired = !username.isBlank()
                && roleCombo.getValue() != null
                && branchCombo.getValue() != null
                && !password.isBlank()
                && !confirm.isBlank();

            if (!hasRequired) {
                validationLabel.setText("Fill all required fields to continue.");
                createButton.setDisable(true);
                return;
            }

            if (!USERNAME_PATTERN.matcher(username).matches()) {
                validationLabel.setText("Username format is invalid.");
                createButton.setDisable(true);
                return;
            }

            if (password.length() < 6) {
                validationLabel.setText("Password must be at least 6 characters.");
                createButton.setDisable(true);
                return;
            }

            if (!password.equals(confirm)) {
                validationLabel.setText("Passwords do not match.");
                createButton.setDisable(true);
                return;
            }

            validationLabel.setText("");
            createButton.setDisable(false);
        };

        usernameField.textProperty().addListener((obs, oldV, newV) -> validateLive.run());
        passwordField.textProperty().addListener((obs, oldV, newV) -> validateLive.run());
        confirmPasswordField.textProperty().addListener((obs, oldV, newV) -> validateLive.run());
        roleCombo.valueProperty().addListener((obs, oldV, newV) -> validateLive.run());
        branchCombo.valueProperty().addListener((obs, oldV, newV) -> validateLive.run());

        dialog.setResultConverter(buttonType -> {
            if (buttonType != createButtonType) return null;
            return new AdminFormResult(
                usernameField.getText().trim(),
                passwordField.getText(),
                roleCombo.getValue(),
                branchCombo.getValue()
            );
        });

        Optional<AdminFormResult> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        AdminFormResult form = result.get();
        if (userAdminService.usernameExists(form.username)) {
            showWarning("Username Already Exists", "Choose a different username.");
            return;
        }

        boolean created = userAdminService.createAdminUser(
            form.username,
            form.password,
            form.role,
            form.branch.id
        );

        if (!created) {
            showError("Could Not Create Account", "Please verify details and try again.");
            return;
        }

        logAuditIfAvailable(
            "CREATE_ADMIN",
            "Created " + form.role + " user '" + form.username + "' for branch '" + form.branch.name + "'"
        );

        loadAdmins();
        loadActivityLogs();
        showInfo("Account Created", "New " + form.role + " account created for " + form.branch.name + ".");
    }

    @FXML
    private void openResetPasswordDialog() {
        AdminRecord selected = adminTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Select Account", "Select an admin/librarian row first, then click Reset Password.");
            return;
        }

        int userId;
        try {
            userId = Integer.parseInt(selected.getUserId());
        } catch (NumberFormatException ex) {
            showError("Invalid Account", "Could not resolve selected user ID.");
            return;
        }

        Dialog<PasswordResetFormResult> dialog = new Dialog<>();
        dialog.setTitle("Reset Branch Account Password");
        dialog.setHeaderText("Set a new password for " + selected.getUsername());

        Window owner = getOwnerWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType resetButtonType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resetButtonType, ButtonType.CANCEL);

        Label userLabel = new Label(selected.getUsername() + " (" + selected.getRole() + ")");
        userLabel.setStyle("-fx-font-weight: bold;");

        Label branchLabel = new Label(selected.getBranch().isBlank() ? "Unassigned" : selected.getBranch());

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Minimum 6 characters");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Retype password");

        Label hintLabel = new Label("Choose a strong password for this branch account.");
        hintLabel.setStyle("-fx-text-fill: #576574; -fx-font-size: 11;");

        Label validationLabel = new Label();
        validationLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11;");
        validationLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12, 16, 8, 16));

        grid.add(new Label("Username"), 0, 0);
        grid.add(userLabel, 1, 0);
        grid.add(new Label("Branch"), 0, 1);
        grid.add(branchLabel, 1, 1);
        grid.add(new Label("New Password"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(new Label("Confirm Password"), 0, 3);
        grid.add(confirmPasswordField, 1, 3);
        grid.add(hintLabel, 1, 4);
        grid.add(validationLabel, 1, 5);

        dialog.getDialogPane().setContent(grid);

        Button resetButton = (Button) dialog.getDialogPane().lookupButton(resetButtonType);
        resetButton.setDisable(true);

        Runnable validateLive = () -> {
            String password = passwordField.getText() == null ? "" : passwordField.getText();
            String confirm = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

            if (password.isBlank() || confirm.isBlank()) {
                validationLabel.setText("Enter and confirm the new password.");
                resetButton.setDisable(true);
                return;
            }

            if (password.length() < 6) {
                validationLabel.setText("Password must be at least 6 characters.");
                resetButton.setDisable(true);
                return;
            }

            if (!password.equals(confirm)) {
                validationLabel.setText("Passwords do not match.");
                resetButton.setDisable(true);
                return;
            }

            validationLabel.setText("");
            resetButton.setDisable(false);
        };

        passwordField.textProperty().addListener((obs, oldV, newV) -> validateLive.run());
        confirmPasswordField.textProperty().addListener((obs, oldV, newV) -> validateLive.run());

        dialog.setResultConverter(buttonType -> {
            if (buttonType != resetButtonType) return null;
            return new PasswordResetFormResult(passwordField.getText());
        });

        Optional<PasswordResetFormResult> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        boolean reset = userAdminService.resetAdminPassword(userId, result.get().password);
        if (!reset) {
            showError("Reset Failed", "Could not reset password. Try again.");
            return;
        }

        logAuditIfAvailable(
            "RESET_PASSWORD",
            "Reset password for " + selected.getRole() + " user '" + selected.getUsername() +
                "' (branch: '" + selected.getBranch() + "')"
        );
        loadActivityLogs();
        showInfo("Password Reset", "Password updated for " + selected.getUsername() + ".");
    }

    private List<BranchChoice> loadActiveBranches() {
        List<BranchChoice> branches = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name FROM branches WHERE active = 1 ORDER BY name"
            ); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    branches.add(new BranchChoice(rs.getInt("id"), safe(rs.getString("name"))));
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }
        return branches;
    }

    private void logAuditIfAvailable(String action, String details) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            if (!tableExists(conn, "audit_logs")) {
                return;
            }

            String actor = "system";
            User currentUser = SessionManager.getCurrentUser();
            if (currentUser != null && currentUser.getUsername() != null && !currentUser.getUsername().isBlank()) {
                actor = currentUser.getUsername();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO audit_logs (user, action, module, timestamp, details)
                VALUES (?, ?, 'User Management', datetime('now','localtime'), ?)
            """)) {
                stmt.setString(1, actor);
                stmt.setString(2, action);
                stmt.setString(3, details);
                stmt.executeUpdate();
            }
        } catch (Exception ignored) {
            // Logging should never block primary workflows.
        }
    }

    private Window getOwnerWindow() {
        return branchFilterCombo != null && branchFilterCombo.getScene() != null
            ? branchFilterCombo.getScene().getWindow()
            : null;
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
        showInfo("Export", "Admin list export triggered");
    }

    @FXML
    private void exportActivityLogs() {
        showInfo("Export", "Activity logs export triggered");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    private void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    private void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        Window owner = getOwnerWindow();
        if (owner != null) {
            alert.initOwner(owner);
        }

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

    private static class BranchChoice {
        final int id;
        final String name;

        BranchChoice(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class AdminFormResult {
        final String username;
        final String password;
        final String role;
        final BranchChoice branch;

        AdminFormResult(String username, String password, String role, BranchChoice branch) {
            this.username = username;
            this.password = password;
            this.role = role;
            this.branch = branch;
        }
    }

    private static class PasswordResetFormResult {
        final String password;

        PasswordResetFormResult(String password) {
            this.password = password;
        }
    }
}

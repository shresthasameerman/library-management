package com.library.controller;

import com.library.App;
import com.library.database.DatabaseConnection;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SystemManagementController {

    @FXML private TextField dbPathField;
    @FXML private Label dbSizeLabel;
    @FXML private Label appVersionLabel;
    @FXML private Label javaVersionLabel;
    @FXML private Label totalUsersLabel;
    @FXML private Label totalBooksLabel;
    @FXML private Label totalMembersLabel;
    @FXML private Label totalBranchesLabel;
    @FXML private Label lastBackupLabel;
    @FXML private Label uptimeLabel;
    @FXML private Label dbStatusLabel;
    @FXML private ProgressBar diskSpaceBar;
    @FXML private Label diskSpaceLabel;
    @FXML private ProgressBar memoryBar;
    @FXML private Label memoryLabel;
    @FXML private ComboBox<String> logLevelFilter;
    @FXML private TableView<AuditLog> auditLogsTable;
    @FXML private TableColumn<AuditLog, String> timestampColumn;
    @FXML private TableColumn<AuditLog, String> levelColumn;
    @FXML private TableColumn<AuditLog, String> userColumn;
    @FXML private TableColumn<AuditLog, String> moduleColumn;
    @FXML private TableColumn<AuditLog, String> actionColumn;
    @FXML private TableColumn<AuditLog, String> messageColumn;

    private final ObservableList<AuditLog> auditLogs = FXCollections.observableArrayList();

    private static final String DB_PATH = Path.of(
        System.getProperty("user.home"), "LibraryApp", "library.db"
    ).toString();

    private static final String BACKUP_DIR = Path.of(
        System.getProperty("user.home"), "LibraryApp", "backups"
    ).toString();

    private static final String BACKUP_META_FILE = Path.of(
        System.getProperty("user.home"), "LibraryApp", "backup.meta"
    ).toString();

    @FXML
    public void initialize() {
        setupAuditTableColumns();
        auditLogsTable.setItems(auditLogs);
        auditLogsTable.setPlaceholder(new Label("No audit logs recorded yet."));

        dbPathField.setText(DB_PATH);
        appVersionLabel.setText(resolveAppVersion());
        javaVersionLabel.setText(System.getProperty("java.version", "—"));

        refreshSystemInfo();
        refreshSystemHealth();
        loadAuditLogs();
    }

    private void setupAuditTableColumns() {
        timestampColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().timestamp));
        levelColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().level));
        userColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().user));
        moduleColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().module));
        actionColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().action));
        messageColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().message));
    }

    private String resolveAppVersion() {
        Package pkg = App.class.getPackage();
        String version = pkg != null ? pkg.getImplementationVersion() : null;
        return version == null || version.isBlank() ? "—" : version;
    }

    private void refreshSystemInfo() {
        try {
            File dbFile = new File(DB_PATH);
            dbSizeLabel.setText(dbFile.exists() ? humanBytes(dbFile.length()) : "—");

            Connection conn = DatabaseConnection.getConnection();
            totalUsersLabel.setText(String.valueOf(queryLong(conn, "SELECT COUNT(*) FROM users")));
            totalMembersLabel.setText(String.valueOf(queryLong(conn, "SELECT COUNT(*) FROM members WHERE active=1")));
            totalBooksLabel.setText(String.valueOf(queryLong(conn, "SELECT COALESCE(SUM(total_copies),0) FROM books")));
            totalBranchesLabel.setText(String.valueOf(queryLong(conn, "SELECT COUNT(*) FROM branches WHERE active=1")));

            lastBackupLabel.setText(readLastBackupTime());
            uptimeLabel.setText(formatUptime(System.currentTimeMillis() - App.getStartTimeMillis()));
        } catch (Exception e) {
            totalUsersLabel.setText("—");
            totalMembersLabel.setText("—");
            totalBooksLabel.setText("—");
            totalBranchesLabel.setText("—");
            lastBackupLabel.setText("Never");
            uptimeLabel.setText("—");
        }
    }

    private long queryLong(Connection conn, String sql) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private String readLastBackupTime() {
        try {
            Path meta = Path.of(BACKUP_META_FILE);
            if (!Files.exists(meta)) return "Never";
            String content = Files.readString(meta).trim();
            return content.isBlank() ? "Never" : content;
        } catch (Exception e) {
            return "Never";
        }
    }

    private void refreshSystemHealth() {
        try {
            File[] roots = File.listRoots();
            File root = (roots != null && roots.length > 0) ? roots[0] : new File("/");

            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usedSpace = Math.max(0, totalSpace - freeSpace);

            if (totalSpace > 0) {
                diskSpaceBar.setProgress((double) usedSpace / totalSpace);
                diskSpaceLabel.setText(humanBytes(freeSpace) + " free / " + humanBytes(totalSpace) + " total");
            } else {
                diskSpaceBar.setProgress(0);
                diskSpaceLabel.setText("—");
            }

            Runtime rt = Runtime.getRuntime();
            long usedMemory = rt.totalMemory() - rt.freeMemory();
            long totalMemory = rt.maxMemory();
            if (totalMemory > 0) {
                memoryBar.setProgress((double) usedMemory / totalMemory);
                memoryLabel.setText(humanBytes(usedMemory) + " used / " + humanBytes(totalMemory) + " total");
            } else {
                memoryBar.setProgress(0);
                memoryLabel.setText("—");
            }

            dbStatusLabel.setText(checkDbHealth() ? "✓ Healthy" : "Unhealthy");
        } catch (Exception e) {
            dbStatusLabel.setText("—");
            diskSpaceLabel.setText("—");
            memoryLabel.setText("—");
            diskSpaceBar.setProgress(0);
            memoryBar.setProgress(0);
        }
    }

    private boolean checkDbHealth() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("PRAGMA integrity_check");
                 ResultSet rs = stmt.executeQuery()) {
                return rs.next() && "ok".equalsIgnoreCase(rs.getString(1));
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void loadAuditLogs() {
        auditLogs.clear();

        try {
            Connection conn = DatabaseConnection.getConnection();
            if (!tableExists(conn, "audit_logs")) {
                auditLogsTable.setPlaceholder(new Label("No audit logs recorded yet."));
                return;
            }

            String levelFilter = logLevelFilter.getValue();
            boolean filterByLevel = levelFilter != null && !levelFilter.isBlank() && !"All".equals(levelFilter);

            String sql = "SELECT timestamp, level, user, module, action, message FROM audit_logs" +
                (filterByLevel ? " WHERE level = ?" : "") +
                " ORDER BY timestamp DESC";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (filterByLevel) stmt.setString(1, levelFilter);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        auditLogs.add(new AuditLog(
                            safe(rs.getString("timestamp")),
                            safe(rs.getString("level")),
                            safe(rs.getString("user")),
                            safe(rs.getString("module")),
                            safe(rs.getString("action")),
                            safe(rs.getString("message"))
                        ));
                    }
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }

        if (auditLogs.isEmpty()) {
            auditLogsTable.setPlaceholder(new Label("No audit logs recorded yet."));
        }
    }

    private boolean tableExists(Connection conn, String tableName) {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
        )) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    @FXML
    private void backupDatabase() {
        try {
            Path backupDir = Path.of(BACKUP_DIR);
            Files.createDirectories(backupDir);

            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String fileName = "library_backup_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".db";

            Path target = backupDir.resolve(fileName);
            Files.copy(Path.of(DB_PATH), target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(Path.of(BACKUP_META_FILE), stamp + " | " + target.toString());

            refreshSystemInfo();
            showAlert("Success", "Database backup completed: " + target);
        } catch (Exception e) {
            showAlert("Error", "Backup failed: " + e.getMessage());
        }
    }

    @FXML
    private void restoreBackup() {
        showAlert("Info", "Restore flow is not configured yet.");
    }

    @FXML
    private void checkDatabaseHealth() {
        refreshSystemHealth();
        showAlert("Info", "Database health checked.");
    }

    @FXML
    private void optimizeDatabase() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            conn.createStatement().execute("VACUUM");
            showAlert("Success", "Database optimized.");
        } catch (Exception e) {
            showAlert("Error", "Optimization failed.");
        }
    }

    @FXML
    private void clearAllFines() {
        if (confirmAction("Clear All Fines", "Are you sure? This action cannot be undone.")) {
            try {
                Connection conn = DatabaseConnection.getConnection();
                conn.createStatement().executeUpdate("UPDATE issue_records SET fine_amount = 0");
                showAlert("Success", "All fines have been cleared.");
            } catch (Exception e) {
                showAlert("Error", "Could not clear fines.");
            }
        }
    }

    @FXML
    private void resetOverdueStatus() {
        if (confirmAction("Reset Overdue Status", "Are you sure? This action cannot be undone.")) {
            try {
                Connection conn = DatabaseConnection.getConnection();
                conn.createStatement().executeUpdate("UPDATE issue_records SET status='ISSUED' WHERE status='OVERDUE'");
                showAlert("Success", "Overdue status has been reset.");
            } catch (Exception e) {
                showAlert("Error", "Could not reset overdue status.");
            }
        }
    }

    @FXML
    private void refreshLogs() {
        refreshSystemInfo();
        refreshSystemHealth();
        loadAuditLogs();
    }

    @FXML
    private void clearLogs() {
        if (confirmAction("Clear Logs", "Clear all audit logs? This action cannot be undone.")) {
            try {
                Connection conn = DatabaseConnection.getConnection();
                if (tableExists(conn, "audit_logs")) {
                    conn.createStatement().executeUpdate("DELETE FROM audit_logs");
                }
                loadAuditLogs();
                showAlert("Success", "Audit logs cleared.");
            } catch (Exception e) {
                showAlert("Error", "Could not clear logs.");
            }
        }
    }

    @FXML
    private void exportLogs() {
        if (auditLogs.isEmpty()) {
            showAlert("Info", "No audit logs recorded yet.");
            return;
        }

        Path out = Path.of(System.getProperty("user.home"), "LibraryApp", "audit_logs_export.csv");
        try {
            Files.createDirectories(out.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("timestamp,level,user,module,action,message");
            for (AuditLog log : auditLogs) {
                lines.add(csv(log.timestamp) + "," + csv(log.level) + "," + csv(log.user) + "," +
                    csv(log.module) + "," + csv(log.action) + "," + csv(log.message));
            }
            Files.write(out, lines);
            showAlert("Success", "Logs exported to: " + out);
        } catch (IOException e) {
            showAlert("Error", "Could not export logs.");
        }
    }

    private String csv(String text) {
        if (text == null) return "";
        if (text.contains(",") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private boolean confirmAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText("Confirm Action");
        alert.setContentText(message);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }

    private String formatUptime(long millis) {
        if (millis < 0) return "—";
        Duration d = Duration.ofMillis(millis);
        long days = d.toDays();
        long hours = d.minusDays(days).toHours();
        long minutes = d.minusDays(days).minusHours(hours).toMinutes();
        return String.format("%d days, %d hours, %d minutes", days, hours, minutes);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class AuditLog {
        final String timestamp;
        final String level;
        final String user;
        final String module;
        final String action;
        final String message;

        public AuditLog(String timestamp, String level, String user, String module, String action, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.user = user;
            this.module = module;
            this.action = action;
            this.message = message;
        }
    }
}

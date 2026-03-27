package com.library.controller;

import com.library.database.DatabaseConnection;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
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

public class BranchManagementController {

    @FXML private TableView<BranchRecord> branchesTable;
    @FXML private TableColumn<BranchRecord, String> branchIdColumn;
    @FXML private TableColumn<BranchRecord, String> branchNameColumn;
    @FXML private TableColumn<BranchRecord, String> locationColumn;
    @FXML private TableColumn<BranchRecord, Number> totalBooksColumn;
    @FXML private TableColumn<BranchRecord, Number> availableBooksColumn;
    @FXML private TableColumn<BranchRecord, Number> totalMembersColumn;
    @FXML private TableColumn<BranchRecord, String> adminColumn;
    @FXML private TableColumn<BranchRecord, Boolean> activeColumn;

    @FXML private ComboBox<String> adminAssignCombo;

    @FXML private Label detailBranchName;
    @FXML private Label detailLocation;
    @FXML private Label detailCreatedDate;
    @FXML private Label detailAdmin;
    @FXML private Label metricIssued;
    @FXML private Label metricReturned;
    @FXML private Label metricFines;

    private final ObservableList<BranchRecord> allBranches = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        branchesTable.setItems(allBranches);
        branchesTable.setPlaceholder(new Label("No data available"));
        setupTableListener();
        clearBranchDetails();
        clearAdminAssignOptions();
        loadBranches();
    }

    private void setupColumns() {
        branchIdColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().branchId));
        branchNameColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().branchName));
        locationColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().location));
        totalBooksColumn.setCellValueFactory(v -> new SimpleIntegerProperty(v.getValue().totalBooks));
        availableBooksColumn.setCellValueFactory(v -> new SimpleIntegerProperty(v.getValue().availableBooks));
        totalMembersColumn.setCellValueFactory(v -> new SimpleIntegerProperty(v.getValue().totalMembers));
        adminColumn.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().admin));
        activeColumn.setCellValueFactory(v -> new SimpleBooleanProperty(v.getValue().active));
    }

    private void loadBranches() {
        allBranches.clear();
        allBranches.addAll(fetchBranches());
        if (allBranches.isEmpty()) {
            clearBranchDetails();
            clearAdminAssignOptions();
        }
    }

    private List<BranchRecord> fetchBranches() {
        List<BranchRecord> rows = new ArrayList<>();

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT b.id,
                       b.code,
                       b.name,
                       b.department,
                       COUNT(DISTINCT bk.id) AS total_books,
                       COALESCE(SUM(bk.available_copies), 0) AS available_books,
                       COUNT(DISTINCT m.id) AS total_members,
                       COALESCE(MAX(u.username), '—') AS admin,
                       b.active
                FROM branches b
                LEFT JOIN books bk ON bk.branch_id = b.id
                LEFT JOIN members m ON m.branch_id = b.id
                LEFT JOIN users u ON u.branch_id = b.id AND u.role IN ('ADMIN','LIBRARIAN')
                GROUP BY b.id, b.code, b.name, b.department, b.active
                ORDER BY b.name
            """); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BranchRecord(
                        safe(rs.getString("id")),
                        safe(rs.getString("name")),
                        safe(rs.getString("department")),
                        rs.getInt("total_books"),
                        rs.getInt("available_books"),
                        rs.getInt("total_members"),
                        safe(rs.getString("admin")),
                        rs.getInt("active") == 1,
                        safe(rs.getString("code"))
                    ));
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }

        return rows;
    }

    private void setupTableListener() {
        branchesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            if (selected == null) {
                clearBranchDetails();
                clearAdminAssignOptions();
                return;
            }

            populateBranchDetails(selected);
            loadAdminsForSelectedBranch(selected);
        });
    }

    private void populateBranchDetails(BranchRecord branch) {
        detailBranchName.setText(blankToDash(branch.branchName));
        detailLocation.setText(blankToDash(branch.location));
        detailAdmin.setText(blankToDash(branch.admin));

        try {
            Connection conn = DatabaseConnection.getConnection();

            try (PreparedStatement createdStmt = conn.prepareStatement(
                "SELECT created_at FROM branches WHERE id = ?"
            )) {
                createdStmt.setInt(1, Integer.parseInt(branch.branchId));
                try (ResultSet rs = createdStmt.executeQuery()) {
                    detailCreatedDate.setText(rs.next() ? blankToDash(rs.getString("created_at")) : "—");
                }
            }

            try (PreparedStatement perfStmt = conn.prepareStatement("""
                SELECT
                    SUM(CASE WHEN status='ISSUED' THEN 1 ELSE 0 END) AS issued,
                    SUM(CASE WHEN status='RETURNED' THEN 1 ELSE 0 END) AS returned,
                    COALESCE(SUM(CASE WHEN status='OVERDUE' THEN fine_amount ELSE 0 END),0) AS fines
                FROM issue_records
                WHERE branch_id = ?
            """)) {
                perfStmt.setInt(1, Integer.parseInt(branch.branchId));
                try (ResultSet rs = perfStmt.executeQuery()) {
                    if (rs.next()) {
                        metricIssued.setText(String.valueOf(rs.getInt("issued")));
                        metricReturned.setText(String.valueOf(rs.getInt("returned")));
                        metricFines.setText("Rs. " + String.format("%.2f", rs.getDouble("fines")));
                    } else {
                        metricIssued.setText("—");
                        metricReturned.setText("—");
                        metricFines.setText("—");
                    }
                }
            }
        } catch (Exception ignored) {
            detailCreatedDate.setText("—");
            metricIssued.setText("—");
            metricReturned.setText("—");
            metricFines.setText("—");
        }
    }

    private void clearBranchDetails() {
        String placeholder = "Select a branch to view details";
        detailBranchName.setText(placeholder);
        detailLocation.setText(placeholder);
        detailCreatedDate.setText(placeholder);
        detailAdmin.setText(placeholder);
        metricIssued.setText("—");
        metricReturned.setText("—");
        metricFines.setText("—");
    }

    private void loadAdminsForSelectedBranch(BranchRecord branch) {
        ObservableList<String> admins = FXCollections.observableArrayList();
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT username
                FROM users
                WHERE role IN ('ADMIN','LIBRARIAN')
                  AND (branch_id IS NULL OR branch_id = ?)
                ORDER BY username
            """)) {
                stmt.setInt(1, Integer.parseInt(branch.branchId));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        admins.add(safe(rs.getString("username")));
                    }
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }

        adminAssignCombo.setItems(admins);
        if (admins.isEmpty()) {
            adminAssignCombo.setPromptText("No admins available");
        } else {
            adminAssignCombo.setPromptText("Select admin to assign...");
        }
    }

    private void clearAdminAssignOptions() {
        adminAssignCombo.setItems(FXCollections.observableArrayList());
        adminAssignCombo.setValue(null);
        adminAssignCombo.setPromptText("Select a branch first");
    }

    @FXML
    private void openAddBranchDialog() {
        showAlert("Info", "Add new branch dialog will open here");
    }

    @FXML
    private void editSelectedBranch() {
        if (branchesTable.getSelectionModel().getSelectedItem() != null) {
            showAlert("Info", "Edit dialog will open for selected branch");
        }
    }

    @FXML
    private void deleteSelectedBranch() {
        if (branchesTable.getSelectionModel().getSelectedItem() != null) {
            showAlert("Info", "Confirmation required before deleting branch");
        }
    }

    @FXML
    private void refreshBranchList() {
        loadBranches();
    }

    @FXML
    private void reassignAdmin() {
        showAlert("Info", "Admin reassignment dialog will open");
    }

    @FXML
    private void assignAdminToBranch() {
        BranchRecord selectedBranch = branchesTable.getSelectionModel().getSelectedItem();
        String selectedAdmin = adminAssignCombo.getValue();

        if (selectedBranch == null || selectedAdmin == null || selectedAdmin.isBlank()) {
            showAlert("Info", "Select a branch and an admin first");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET branch_id = ? WHERE username = ? AND role IN ('ADMIN','LIBRARIAN')"
            )) {
                stmt.setInt(1, Integer.parseInt(selectedBranch.branchId));
                stmt.setString(2, selectedAdmin);
                stmt.executeUpdate();
            }
            refreshBranchList();
            showAlert("Success", "Admin assigned to branch");
        } catch (Exception e) {
            showAlert("Error", "Could not assign admin");
        }
    }

    @FXML
    private void removeAdminFromBranch() {
        String selectedAdmin = adminAssignCombo.getValue();
        if (selectedAdmin == null || selectedAdmin.isBlank()) {
            showAlert("Info", "Select an admin first");
            return;
        }

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET branch_id = NULL WHERE username = ? AND role IN ('ADMIN','LIBRARIAN')"
            )) {
                stmt.setString(1, selectedAdmin);
                stmt.executeUpdate();
            }
            refreshBranchList();
            showAlert("Success", "Admin removed from branch");
        } catch (Exception e) {
            showAlert("Error", "Could not remove admin");
        }
    }

    @FXML
    private void exportBranchList() {
        showAlert("Info", "Export branch list action triggered");
    }

    @FXML
    private void exportPerformanceReport() {
        showAlert("Info", "Export performance report action triggered");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String blankToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class BranchRecord {
        final String branchId;
        final String branchName;
        final String location;
        final int totalBooks;
        final int availableBooks;
        final int totalMembers;
        final String admin;
        final boolean active;
        final String code;

        public BranchRecord(String branchId, String branchName, String location, int totalBooks,
                            int availableBooks, int totalMembers, String admin, boolean active, String code) {
            this.branchId = branchId;
            this.branchName = branchName;
            this.location = location;
            this.totalBooks = totalBooks;
            this.availableBooks = availableBooks;
            this.totalMembers = totalMembers;
            this.admin = admin;
            this.active = active;
            this.code = code;
        }
    }
}

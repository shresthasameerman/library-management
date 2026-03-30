package com.library.controller;

import com.library.database.DatabaseConnection;
import com.library.service.BranchService;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
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
    private final BranchService branchService = new BranchService();
    private static final Pattern BRANCH_CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]{2,12}$");

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
        Dialog<BranchFormResult> dialog = new Dialog<>();
        dialog.setTitle("Create Branch");
        dialog.setHeaderText("Add a new library branch");

        Window owner = getOwnerWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType createButtonType = new ButtonType("Create Branch", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. BMC Pokhara");

        TextField locationField = new TextField();
        locationField.setPromptText("e.g. A Levels");

        TextField codeField = new TextField();
        codeField.setPromptText("e.g. BMPKR (2-12 chars)");

        Label hintLabel = new Label("Code: uppercase letters/numbers, underscore or hyphen.");
        hintLabel.setStyle("-fx-text-fill: #576574; -fx-font-size: 11;");

        Label validationLabel = new Label();
        validationLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 11;");
        validationLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12, 16, 8, 16));

        grid.add(new Label("Branch Name"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Location"), 0, 1);
        grid.add(locationField, 1, 1);
        grid.add(new Label("Branch Code"), 0, 2);
        grid.add(codeField, 1, 2);
        grid.add(hintLabel, 1, 3);
        grid.add(validationLabel, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Button createButton = (Button) dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        Runnable validateLive = () -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            String location = locationField.getText() == null ? "" : locationField.getText().trim();
            String code = normalizeCode(codeField.getText());

            if (name.isBlank() || location.isBlank() || code.isBlank()) {
                validationLabel.setText("Fill all fields to continue.");
                createButton.setDisable(true);
                return;
            }

            if (name.length() < 3) {
                validationLabel.setText("Branch name should be at least 3 characters.");
                createButton.setDisable(true);
                return;
            }

            if (!BRANCH_CODE_PATTERN.matcher(code).matches()) {
                validationLabel.setText("Branch code format is invalid.");
                createButton.setDisable(true);
                return;
            }

            validationLabel.setText("");
            createButton.setDisable(false);
        };

        nameField.textProperty().addListener((obs, oldV, newV) -> {
            String currentCode = codeField.getText() == null ? "" : codeField.getText().trim();
            if (currentCode.isBlank()) {
                codeField.setText(suggestBranchCode(newV));
            }
            validateLive.run();
        });
        locationField.textProperty().addListener((obs, oldV, newV) -> validateLive.run());
        codeField.textProperty().addListener((obs, oldV, newV) -> {
            String normalized = normalizeCode(newV);
            if (!normalized.equals(newV == null ? "" : newV)) {
                codeField.setText(normalized);
            }
            validateLive.run();
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType != createButtonType) return null;
            return new BranchFormResult(
                nameField.getText().trim(),
                locationField.getText().trim(),
                normalizeCode(codeField.getText())
            );
        });

        Optional<BranchFormResult> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        BranchFormResult form = result.get();

        if (branchService.branchNameExists(form.name)) {
            showAlert(Alert.AlertType.WARNING, "Duplicate Branch", "An active branch with this name already exists.");
            return;
        }

        if (branchService.branchCodeExists(form.code)) {
            showAlert(Alert.AlertType.WARNING, "Duplicate Code", "This branch code is already in use.");
            return;
        }

        boolean created = branchService.addBranch(form.name, form.location, form.code);
        if (!created) {
            showAlert(Alert.AlertType.ERROR, "Could Not Create Branch", "Please verify details and try again.");
            return;
        }

        loadBranches();
        selectBranchByName(form.name);
        showAlert(Alert.AlertType.INFORMATION, "Branch Created", "Branch '" + form.name + "' was created successfully.");
    }

    private void selectBranchByName(String branchName) {
        if (branchName == null || branchName.isBlank()) return;
        for (BranchRecord row : allBranches) {
            if (row.branchName != null && row.branchName.equalsIgnoreCase(branchName)) {
                branchesTable.getSelectionModel().select(row);
                branchesTable.scrollTo(row);
                break;
            }
        }
    }

    private String normalizeCode(String code) {
        if (code == null) return "";
        return code.trim().toUpperCase();
    }

    private String suggestBranchCode(String branchName) {
        if (branchName == null) return "";
        String[] parts = branchName.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank() && Character.isLetterOrDigit(part.charAt(0))) {
                builder.append(Character.toUpperCase(part.charAt(0)));
            }
            if (builder.length() >= 6) {
                break;
            }
        }
        String candidate = builder.length() < 2
            ? branchName.replaceAll("[^A-Za-z0-9]", "").toUpperCase()
            : builder.toString();
        if (candidate.length() > 12) {
            candidate = candidate.substring(0, 12);
        }
        return candidate;
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

    private Window getOwnerWindow() {
        return branchesTable != null && branchesTable.getScene() != null
            ? branchesTable.getScene().getWindow()
            : null;
    }

    private void showAlert(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
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

    private static class BranchFormResult {
        final String name;
        final String location;
        final String code;

        BranchFormResult(String name, String location, String code) {
            this.name = name;
            this.location = location;
            this.code = code;
        }
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

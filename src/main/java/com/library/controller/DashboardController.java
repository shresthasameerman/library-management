package com.library.controller;

import com.library.database.DatabaseConnection;
import com.library.model.Branch;
import com.library.model.BranchSummary;
import com.library.model.DashboardStats;
import com.library.model.User;
import com.library.service.BranchService;
import com.library.service.DashboardService;
import com.library.service.ReportService;
import com.library.service.UserAdminService;
import com.library.util.SessionManager;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    // ── Sidebar ───────────────────────────────────────────────────────
    @FXML private BorderPane mainPane;
    @FXML private Button     btnDashboard;
    @FXML private Button     btnBooks;
    @FXML private Button     btnMembers;
    @FXML private Button     btnIssueReturn;
    @FXML private Button     btnManageBranches;
    @FXML private Button     btnManageLibrarians;
    @FXML private Button     btnSystemSummary;
    @FXML private HBox       superAdminActionsRow;

    // ── Header ────────────────────────────────────────────────────────
    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private Label avatarLabel;
    @FXML private Label greetingLabel;
    @FXML private Label dateLabel;

    // ── Stats ─────────────────────────────────────────────────────────
    @FXML private Label totalBooksLabel;
    @FXML private Label totalMembersLabel;
    @FXML private Label issuedBooksLabel;
    @FXML private Label overdueLabel;

    // ── Charts ────────────────────────────────────────────────────────
    @FXML private PieChart categoryPieChart;
    @FXML private BarChart<String, Number> monthlyBarChart;
    @FXML private PieChart memberPieChart;
    @FXML private Label    studentCountLabel;
    @FXML private Label    staffCountLabel;

    // ── Reports ───────────────────────────────────────────────────────
    @FXML private Label reportStatusLabel;
    @FXML private Label reportPathLabel;

    // ── Services ──────────────────────────────────────────────────────
    private final ReportService reportService = new ReportService();
    private final BranchService branchService = new BranchService();
    private final UserAdminService userAdminService = new UserAdminService();
    private final DashboardService dashboardService = new DashboardService();
    private Node dashboardCenter;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // ── User info ─────────────────────────────────────────────
        var user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText(user.getUsername());
            String branchText = user.getBranchId() != null
                ? " • Branch " + user.getBranchId()
                : " • All Branches";
            roleLabel.setText(user.getRole() + branchText);
            avatarLabel.setText(
                String.valueOf(user.getUsername().charAt(0)).toUpperCase()
            );
        }

        // ── Greeting ──────────────────────────────────────────────
        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good Morning! 👋"
                        : hour < 17 ? "Good Afternoon! 👋"
                        : "Good Evening! 👋";
        if (greetingLabel != null) greetingLabel.setText(greeting);

        // ── Date ──────────────────────────────────────────────────
        if (dateLabel != null)
            dateLabel.setText(LocalDate.now()
                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));

        // ── Report path ───────────────────────────────────────────
        if (reportPathLabel != null)
            reportPathLabel.setText(
                "📁 " + reportService.getReportsDirectory());

        // ── Load stats + charts ───────────────────────────────────
        loadStats();
        loadCharts();
        applySuperAdminVisibility();

        Platform.runLater(() -> dashboardCenter = mainPane.getCenter());
        setActiveButton(btnDashboard);
    }

    private void applySuperAdminVisibility() {
        boolean superAdmin = SessionManager.isSuperAdmin();
        if (superAdminActionsRow != null) {
            superAdminActionsRow.setManaged(superAdmin);
            superAdminActionsRow.setVisible(superAdmin);
        }
        if (btnSystemSummary != null) {
            btnSystemSummary.setManaged(superAdmin);
            btnSystemSummary.setVisible(superAdmin);
        }
    }

    // ── Stats with animated counters ──────────────────────────────────
    private void loadStats() {
        try {
            DashboardStats stats = dashboardService.getStats();
            animateCount(totalBooksLabel, stats.getTotalBooks());
            animateCount(totalMembersLabel, stats.getTotalMembers());
            animateCount(issuedBooksLabel, stats.getIssuedBooks());
            animateCount(overdueLabel, stats.getOverdueBooks());

        } catch (Exception e) {
            System.err.println("Stats error: " + e.getMessage());
        }
    }

    // ── Animated counter — counts up from 0 to target ────────────────
    private void animateCount(Label label, int target) {
        if (label == null) return;

        // Start from current value if already set
        int start = 0;
        try {
            start = Integer.parseInt(label.getText());
        } catch (NumberFormatException ignored) {}

        final int from = start;
        final int to   = target;

        Timeline timeline = new Timeline();
        int steps = Math.max(20, Math.abs(to - from));
        steps = Math.min(steps, 60); // cap at 60 frames

        for (int i = 0; i <= steps; i++) {
            final int step = i;
            double progress = (double) step / steps;
            // Ease out — fast start, slow end
            double eased = 1 - Math.pow(1 - progress, 3);
            int value = from + (int)((to - from) * eased);

            KeyFrame kf = new KeyFrame(
                Duration.millis(600.0 * step / steps),
                e -> label.setText(String.valueOf(value))
            );
            timeline.getKeyFrames().add(kf);
        }

        // Ensure exact final value
        timeline.getKeyFrames().add(new KeyFrame(
            Duration.millis(620),
            e -> label.setText(String.valueOf(to))
        ));

        timeline.play();
    }

    // ── Charts ────────────────────────────────────────────────────────
    private void loadCharts() {
        loadCategoryPieChart();
        loadMonthlyBarChart();
        loadMemberPieChart();
    }

    private void loadCategoryPieChart() {
        if (categoryPieChart == null) return;
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery("""
                SELECT COALESCE(category,'Other') AS category,
                       COUNT(*) AS total
                FROM books
                %s
                GROUP BY category
                ORDER BY total DESC
            """.formatted(SessionManager.isBranchScopedUser()
                ? "WHERE branch_id = " + SessionManager.getCurrentBranchId()
                : ""));

            var data = FXCollections.<PieChart.Data>observableArrayList();
            boolean hasData = false;
            while (rs.next()) {
                data.add(new PieChart.Data(
                    rs.getString("category") +
                    " (" + rs.getInt("total") + ")",
                    rs.getInt("total")
                ));
                hasData = true;
            }
            if (!hasData) data.add(new PieChart.Data("No books yet", 1));

            categoryPieChart.setData(data);
            categoryPieChart.setAnimated(true);

        } catch (Exception e) {
            System.err.println("Category chart error: " + e.getMessage());
        }
    }

    private void loadMonthlyBarChart() {
        if (monthlyBarChart == null) return;
        try {
            Connection conn = DatabaseConnection.getConnection();

            XYChart.Series<String, Number> issueSeries =
                new XYChart.Series<>();
            issueSeries.setName("Issued");

            XYChart.Series<String, Number> returnSeries =
                new XYChart.Series<>();
            returnSeries.setName("Returned");

            for (int i = 5; i >= 0; i--) {
                LocalDate month    = LocalDate.now().minusMonths(i);
                String    monthStr = month.format(
                    DateTimeFormatter.ofPattern("yyyy-MM"));
                String    label    = month.format(
                    DateTimeFormatter.ofPattern("MMM"));

                ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM issue_records " +
                    "WHERE strftime('%Y-%m', issue_date) = '" + monthStr + "'" +
                    (SessionManager.isBranchScopedUser()
                        ? " AND branch_id = " + SessionManager.getCurrentBranchId()
                        : "")
                );
                issueSeries.getData().add(
                    new XYChart.Data<>(label, rs.next() ? rs.getInt(1) : 0));

                rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM issue_records " +
                    "WHERE strftime('%Y-%m', return_date) = '" + monthStr + "'" +
                    (SessionManager.isBranchScopedUser()
                        ? " AND branch_id = " + SessionManager.getCurrentBranchId()
                        : "")
                );
                returnSeries.getData().add(
                    new XYChart.Data<>(label, rs.next() ? rs.getInt(1) : 0));
            }

            monthlyBarChart.getData().clear();
            monthlyBarChart.getData().addAll(issueSeries, returnSeries);
            monthlyBarChart.setAnimated(true);
            monthlyBarChart.setBarGap(3);
            monthlyBarChart.setCategoryGap(20);

            // Show Y-axis in whole numbers only: 0,1,2,3...
            NumberAxis yAxis = (NumberAxis) monthlyBarChart.getYAxis();
            int maxIssued = issueSeries.getData().stream()
                .mapToInt(d -> d.getYValue().intValue())
                .max().orElse(0);
            int maxReturned = returnSeries.getData().stream()
                .mapToInt(d -> d.getYValue().intValue())
                .max().orElse(0);
            int upperBound = Math.max(1, Math.max(maxIssued, maxReturned));

            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(0);
            yAxis.setUpperBound(upperBound);
            yAxis.setTickUnit(1);
            yAxis.setMinorTickVisible(false);
            yAxis.setMinorTickCount(0);
            yAxis.setForceZeroInRange(true);
            yAxis.setTickLabelFormatter(new StringConverter<>() {
                @Override
                public String toString(Number n) {
                    return String.valueOf(n.intValue());
                }

                @Override
                public Number fromString(String s) {
                    return Integer.parseInt(s);
                }
            });

        } catch (Exception e) {
            System.err.println("Monthly chart error: " + e.getMessage());
        }
    }

    private void loadMemberPieChart() {
        if (memberPieChart == null) return;
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery("""
                SELECT COALESCE(member_type,'Student') AS type,
                       COUNT(*) AS total
                FROM members WHERE active = 1
                %s
                GROUP BY member_type
            """.formatted(SessionManager.isBranchScopedUser()
                ? "AND branch_id = " + SessionManager.getCurrentBranchId()
                : ""));

            var data = FXCollections.<PieChart.Data>observableArrayList();
            int students = 0, staff = 0;
            while (rs.next()) {
                String type  = rs.getString("type");
                int    count = rs.getInt("total");
                data.add(new PieChart.Data(type, count));
                if ("Student".equals(type)) students = count;
                else staff = count;
            }

            if (data.isEmpty())
                data.add(new PieChart.Data("No members", 1));

            memberPieChart.setData(data);
            memberPieChart.setAnimated(true);

            if (studentCountLabel != null)
                animateCount(studentCountLabel, students);
            if (staffCountLabel != null)
                animateCount(staffCountLabel, staff);

        } catch (Exception e) {
            System.err.println("Member chart error: " + e.getMessage());
        }
    }

    // ── Navigation ────────────────────────────────────────────────────

    @FXML
    private void showDashboard() {
        setActiveButton(btnDashboard);
        if (dashboardCenter != null)
            mainPane.setCenter(dashboardCenter);
        loadStats();
        loadCharts();
    }

    @FXML private void showBooks() {
        setActiveButton(btnBooks);
        loadPage("/com/library/fxml/Books.fxml");
    }

    @FXML private void showMembers() {
        setActiveButton(btnMembers);
        loadPage("/com/library/fxml/Members.fxml");
    }

    @FXML private void showIssueReturn() {
        setActiveButton(btnIssueReturn);
        loadPage("/com/library/fxml/IssueReturn.fxml");
    }

    @FXML
    private void handleLogout() {
        try {
            SessionManager.logout();
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/library/fxml/Login.fxml"));
            Scene scene = new Scene(loader.load(), 900, 600);
            scene.getStylesheets().add(
                getClass().getResource("/com/library/css/style.css")
                          .toExternalForm());
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.setWidth(900);
            stage.setHeight(600);
            stage.setResizable(false);
            stage.setScene(scene);
            stage.setTitle("Library Management System — Login");
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleManageBranches() {
        if (!SessionManager.isSuperAdmin()) return;

        TableView<BranchSummary> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BranchSummary, String> nameCol = new TableColumn<>("Branch");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<BranchSummary, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(new PropertyValueFactory<>("department"));

        TableColumn<BranchSummary, Integer> booksCol = new TableColumn<>("Books");
        booksCol.setCellValueFactory(new PropertyValueFactory<>("totalBooks"));

        TableColumn<BranchSummary, Integer> membersCol = new TableColumn<>("Members");
        membersCol.setCellValueFactory(new PropertyValueFactory<>("totalMembers"));

        TableColumn<BranchSummary, Integer> issuedCol = new TableColumn<>("Issued");
        issuedCol.setCellValueFactory(new PropertyValueFactory<>("issuedBooks"));

        TableColumn<BranchSummary, Integer> librariansCol = new TableColumn<>("Librarians");
        librariansCol.setCellValueFactory(new PropertyValueFactory<>("librarians"));

        table.getColumns().addAll(nameCol, deptCol, booksCol, membersCol, issuedCol, librariansCol);
        table.getItems().setAll(branchService.getBranchSummaries());

        TextField nameField = new TextField();
        nameField.setPromptText("Branch name");
        TextField deptField = new TextField();
        deptField.setPromptText("Department");
        TextField codeField = new TextField();
        codeField.setPromptText("Code (e.g. CSE)");
        Label info = new Label();

        Button addBtn = new Button("Add Branch");
        Button removeBtn = new Button("Deactivate Branch");
        Button closeBtn = new Button("Close");

        addBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String dept = deptField.getText().trim();
            String code = codeField.getText().trim().toUpperCase();

            if (name.isEmpty() || code.isEmpty()) {
                info.setText("Branch name and code are required.");
                return;
            }
            if (branchService.branchCodeExists(code)) {
                info.setText("Branch code already exists.");
                return;
            }

            boolean created = branchService.addBranch(name, dept, code);
            if (created) {
                info.setText("Branch created.");
                nameField.clear();
                deptField.clear();
                codeField.clear();
                table.getItems().setAll(branchService.getBranchSummaries());
            } else {
                info.setText("Failed to create branch.");
            }
        });

        removeBtn.setOnAction(e -> {
            BranchSummary selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                info.setText("Select a branch first.");
                return;
            }

            if ("MAIN".equalsIgnoreCase(selected.getCode())) {
                info.setText("MAIN branch cannot be deactivated.");
                return;
            }

            boolean ok = branchService.deactivateBranch(selected.getId());
            if (ok) {
                info.setText("Branch deactivated.");
                table.getItems().setAll(branchService.getBranchSummaries());
            } else {
                info.setText("Cannot deactivate: branch has linked data/users.");
            }
        });

        VBox root = new VBox(10,
            new Label("SuperAdmin: Branch Management"),
            table,
            new HBox(8, nameField, deptField, codeField),
            new HBox(8, addBtn, removeBtn, closeBtn),
            info
        );
        root.setStyle("-fx-padding: 16; -fx-background-color: white;");

        Stage dialog = new Stage();
        dialog.initOwner(mainPane.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Manage Branches");
        dialog.setScene(new Scene(root, 980, 520));

        closeBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();

        loadStats();
        loadCharts();
    }

    @FXML
    private void handleManageLibrarians() {
        if (!SessionManager.isSuperAdmin()) return;

        TableView<User> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<User, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<User, String> userCol = new TableColumn<>("Username");
        userCol.setCellValueFactory(new PropertyValueFactory<>("username"));

        TableColumn<User, Integer> branchCol = new TableColumn<>("Branch ID");
        branchCol.setCellValueFactory(new PropertyValueFactory<>("branchId"));

        TableColumn<User, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));

        table.getColumns().addAll(idCol, userCol, branchCol, roleCol);
        table.getItems().setAll(userAdminService.getLibrarians());

        TextField usernameField = new TextField();
        usernameField.setPromptText("New librarian username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Temporary password");
        ComboBox<Branch> branchBox = new ComboBox<>();
        branchBox.getItems().setAll(branchService.getAllBranches());
        branchBox.setPromptText("Assign branch");
        Label info = new Label();

        Button createBtn = new Button("Create Librarian");
        Button reassignBtn = new Button("Reassign Branch");
        Button deleteBtn = new Button("Delete Librarian");
        Button closeBtn = new Button("Close");

        createBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            Branch branch = branchBox.getValue();

            if (username.isEmpty() || password.length() < 6 || branch == null) {
                info.setText("Username, branch and password(>=6 chars) are required.");
                return;
            }
            if (userAdminService.usernameExists(username)) {
                info.setText("Username already exists.");
                return;
            }

            boolean ok = userAdminService.createLibrarian(username, password, branch.getId());
            if (ok) {
                info.setText("Librarian account created.");
                usernameField.clear();
                passwordField.clear();
                table.getItems().setAll(userAdminService.getLibrarians());
            } else {
                info.setText("Failed to create librarian account.");
            }
        });

        reassignBtn.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            Branch branch = branchBox.getValue();
            if (selected == null || branch == null) {
                info.setText("Select librarian and target branch.");
                return;
            }
            boolean ok = userAdminService.reassignLibrarian(selected.getId(), branch.getId());
            info.setText(ok ? "Librarian reassigned." : "Reassign failed.");
            if (ok) table.getItems().setAll(userAdminService.getLibrarians());
        });

        deleteBtn.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                info.setText("Select a librarian first.");
                return;
            }
            boolean ok = userAdminService.deleteLibrarian(selected.getId());
            info.setText(ok ? "Librarian removed." : "Delete failed.");
            if (ok) table.getItems().setAll(userAdminService.getLibrarians());
        });

        VBox root = new VBox(10,
            new Label("SuperAdmin: Librarian Management"),
            table,
            new HBox(8, usernameField, passwordField, branchBox),
            new HBox(8, createBtn, reassignBtn, deleteBtn, closeBtn),
            info
        );
        root.setStyle("-fx-padding: 16; -fx-background-color: white;");

        Stage dialog = new Stage();
        dialog.initOwner(mainPane.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Manage Librarians");
        dialog.setScene(new Scene(root, 960, 520));

        closeBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    @FXML
    private void handleSystemSummary() {
        if (!SessionManager.isSuperAdmin()) return;

        TableView<BranchSummary> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BranchSummary, String> branchCol = new TableColumn<>("Branch");
        branchCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<BranchSummary, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(new PropertyValueFactory<>("department"));

        TableColumn<BranchSummary, Integer> booksCol = new TableColumn<>("Books");
        booksCol.setCellValueFactory(new PropertyValueFactory<>("totalBooks"));

        TableColumn<BranchSummary, Integer> membersCol = new TableColumn<>("Members");
        membersCol.setCellValueFactory(new PropertyValueFactory<>("totalMembers"));

        TableColumn<BranchSummary, Integer> issuedCol = new TableColumn<>("Issued");
        issuedCol.setCellValueFactory(new PropertyValueFactory<>("issuedBooks"));

        TableColumn<BranchSummary, Integer> librariansCol = new TableColumn<>("Librarians");
        librariansCol.setCellValueFactory(new PropertyValueFactory<>("librarians"));

        table.getColumns().addAll(branchCol, deptCol, booksCol, membersCol, issuedCol, librariansCol);
        table.getItems().setAll(branchService.getBranchSummaries());

        int totalBooks = table.getItems().stream().mapToInt(BranchSummary::getTotalBooks).sum();
        int totalMembers = table.getItems().stream().mapToInt(BranchSummary::getTotalMembers).sum();
        int totalIssued = table.getItems().stream().mapToInt(BranchSummary::getIssuedBooks).sum();

        Label totals = new Label(
            "Network Totals  |  Books: " + totalBooks +
            "  Members: " + totalMembers +
            "  Issued: " + totalIssued
        );

        Button closeBtn = new Button("Close");
        VBox root = new VBox(10,
            new Label("SuperAdmin: Consolidated Branch Monitoring"),
            table,
            totals,
            closeBtn
        );
        root.setStyle("-fx-padding: 16; -fx-background-color: white;");

        Stage dialog = new Stage();
        dialog.initOwner(mainPane.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("System Summary");
        dialog.setScene(new Scene(root, 900, 520));

        closeBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    // ── Reports ───────────────────────────────────────────────────────

    @FXML private void downloadIssuedReport() {
        generateReport("Issued Books",
            reportService.generateIssuedBooksReport());
    }
    @FXML private void downloadOverdueReport() {
        generateReport("Overdue Report",
            reportService.generateOverdueReport());
    }
    @FXML private void downloadInventoryReport() {
        generateReport("Book Inventory",
            reportService.generateInventoryReport());
    }
    @FXML private void downloadMemberReport() {
        generateReport("Member List",
            reportService.generateMemberReport());
    }
    @FXML private void downloadMonthlySummary() {
        generateReport("Monthly Summary",
            reportService.generateMonthlySummary());
    }

    private void generateReport(String name, String filePath) {
        if (filePath == null) {
            if (reportStatusLabel != null) {
                reportStatusLabel.setText("❌ Failed");
                reportStatusLabel.setStyle(
                    "-fx-text-fill: #e63946; -fx-font-size: 11px;");
            }
            return;
        }
        if (reportStatusLabel != null) {
            reportStatusLabel.setText("✅ Saved!");
            reportStatusLabel.setStyle(
                "-fx-text-fill: #2dc653; -fx-font-size: 11px;");
        }
        if (reportPathLabel != null)
            reportPathLabel.setText("📁 " + filePath);
        System.out.println("Report generated: " + name + " -> " + filePath);

        new Thread(() -> {
            try {
                Thread.sleep(3000);
                Platform.runLater(() -> {
                    if (reportStatusLabel != null)
                        reportStatusLabel.setText("");
                    if (reportPathLabel != null)
                        reportPathLabel.setText(
                            "📁 " + reportService.getReportsDirectory());
                });
            } catch (InterruptedException ignored) {}
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private void loadPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(fxmlPath));
            Node page = loader.load();
            mainPane.setCenter(page);
        } catch (Exception e) {
            System.err.println("Failed to load: " + fxmlPath);
            e.printStackTrace();
        }
    }

    private void setActiveButton(Button active) {
        btnDashboard.getStyleClass().remove("nav-button-active");
        btnBooks.getStyleClass().remove("nav-button-active");
        btnMembers.getStyleClass().remove("nav-button-active");
        btnIssueReturn.getStyleClass().remove("nav-button-active");
        active.getStyleClass().add("nav-button-active");
    }
}
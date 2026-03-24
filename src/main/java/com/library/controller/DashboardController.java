package com.library.controller;

import com.library.database.DatabaseConnection;
import com.library.service.ReportService;
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
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
    private Node dashboardCenter;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // ── User info ─────────────────────────────────────────────
        var user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText(user.getUsername());
            roleLabel.setText(user.getRole());
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

        Platform.runLater(() -> dashboardCenter = mainPane.getCenter());
        setActiveButton(btnDashboard);
    }

    // ── Stats with animated counters ──────────────────────────────────
    private void loadStats() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            Statement  stmt = conn.createStatement();

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM books");
            if (rs.next()) animateCount(totalBooksLabel, rs.getInt(1));

            rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM members WHERE active = 1");
            if (rs.next()) animateCount(totalMembersLabel, rs.getInt(1));

            rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM issue_records WHERE status = 'ISSUED'");
            if (rs.next()) animateCount(issuedBooksLabel, rs.getInt(1));

            rs = stmt.executeQuery("""
                SELECT COUNT(*) FROM issue_records
                WHERE status = 'ISSUED' AND due_date < DATE('now')
            """);
            if (rs.next()) animateCount(overdueLabel, rs.getInt(1));

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
                GROUP BY category
                ORDER BY total DESC
            """);

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
                    "WHERE strftime('%Y-%m', issue_date) = '" + monthStr + "'"
                );
                issueSeries.getData().add(
                    new XYChart.Data<>(label, rs.next() ? rs.getInt(1) : 0));

                rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM issue_records " +
                    "WHERE strftime('%Y-%m', return_date) = '" + monthStr + "'"
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
                GROUP BY member_type
            """);

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
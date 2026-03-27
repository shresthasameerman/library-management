package com.library.controller;

import com.library.database.DatabaseConnection;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BranchAnalyticsController {

    @FXML private ComboBox<String> branchCombo;

    @FXML private Label totalBooksLabel;
    @FXML private Label availableBooksLabel;
    @FXML private Label issuedBooksLabel;
    @FXML private Label totalMembersLabel;

    @FXML private LineChart<String, Number> issueTrendChart;
    @FXML private CategoryAxis issueTrendXAxis;
    @FXML private NumberAxis issueTrendYAxis;

    @FXML private LineChart<String, Number> returnTrendChart;
    @FXML private CategoryAxis returnTrendXAxis;
    @FXML private NumberAxis returnTrendYAxis;

    @FXML private BarChart<String, Number> overdueTrendChart;
    @FXML private CategoryAxis overdueTrendXAxis;
    @FXML private NumberAxis overdueTrendYAxis;

    @FXML private PieChart categoryChart;
    @FXML private PieChart memberTypeChart;

    @FXML private BarChart<String, Number> branchComparisonChart;
    @FXML private CategoryAxis branchComparisonXAxis;
    @FXML private NumberAxis branchComparisonYAxis;

    private final Map<String, Integer> branchIdByName = new HashMap<>();

    @FXML
    public void initialize() {
        loadBranches();
        updateAnalytics();
    }

    private void loadBranches() {
        ObservableList<String> branchNames = FXCollections.observableArrayList("All Branches");
        branchIdByName.clear();

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, name FROM branches WHERE active = 1 ORDER BY name"
            ); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = safe(rs.getString("name"));
                    if (!name.isBlank()) {
                        branchNames.add(name);
                        branchIdByName.put(name, id);
                    }
                }
            }
        } catch (Exception ignored) {
            // Empty list fallback.
        }

        branchCombo.setItems(branchNames);
        branchCombo.setValue("All Branches");
    }

    @FXML
    private void updateAnalytics() {
        Integer branchId = getSelectedBranchId();
        loadStatistics(branchId);
        loadIssueTrendChart(branchId);
        loadReturnTrendChart(branchId);
        loadOverdueTrendChart(branchId);
        loadCategoryChart(branchId);
        loadMemberTypeChart(branchId);
        loadBranchComparisonChart();
    }

    private Integer getSelectedBranchId() {
        String branchName = branchCombo.getValue();
        if (branchName == null || "All Branches".equals(branchName)) {
            return null;
        }
        return branchIdByName.get(branchName);
    }

    private void loadStatistics(Integer branchId) {
        try {
            Connection conn = DatabaseConnection.getConnection();

            long totalBooks = queryLong(conn,
                "SELECT COALESCE(SUM(total_copies),0) FROM books WHERE (branch_id = ? OR ? IS NULL)",
                branchId
            );

            long availableBooks = queryLong(conn,
                "SELECT COALESCE(SUM(available_copies),0) FROM books WHERE (branch_id = ? OR ? IS NULL)",
                branchId
            );

            long issuedBooks = queryLong(conn,
                "SELECT COUNT(*) FROM issue_records WHERE status='ISSUED' AND (branch_id = ? OR ? IS NULL)",
                branchId
            );

            long totalMembers = queryLong(conn,
                "SELECT COUNT(*) FROM members WHERE active=1 AND (branch_id = ? OR ? IS NULL)",
                branchId
            );

            totalBooksLabel.setText(String.valueOf(totalBooks));
            availableBooksLabel.setText(String.valueOf(availableBooks));
            issuedBooksLabel.setText(String.valueOf(issuedBooks));
            totalMembersLabel.setText(String.valueOf(totalMembers));
        } catch (Exception e) {
            totalBooksLabel.setText("—");
            availableBooksLabel.setText("—");
            issuedBooksLabel.setText("—");
            totalMembersLabel.setText("—");
        }
    }

    private long queryLong(Connection conn, String sql, Integer branchId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindBranchFilter(stmt, branchId, 1);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private List<XYChart.Data<String, Number>> queryMonthlyCounts(Connection conn, String sql, Integer branchId)
            throws Exception {
        List<XYChart.Data<String, Number>> data = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindBranchFilter(stmt, branchId, 1);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    data.add(new XYChart.Data<>(safe(rs.getString("month")), rs.getInt("count")));
                }
            }
        }
        return data;
    }

    private void bindBranchFilter(PreparedStatement stmt, Integer branchId, int startIndex) throws Exception {
        if (branchId == null) {
            stmt.setNull(startIndex, Types.INTEGER);
            stmt.setNull(startIndex + 1, Types.INTEGER);
        } else {
            stmt.setInt(startIndex, branchId);
            stmt.setInt(startIndex + 1, branchId);
        }
    }

    private void loadIssueTrendChart(Integer branchId) {
        issueTrendChart.getData().clear();

        try {
            Connection conn = DatabaseConnection.getConnection();
            List<XYChart.Data<String, Number>> data = queryMonthlyCounts(conn, """
                SELECT strftime('%Y-%m', issue_date) AS month, COUNT(*) AS count
                FROM issue_records
                WHERE (branch_id = ? OR ? IS NULL)
                GROUP BY month ORDER BY month DESC LIMIT 12
            """, branchId);

            if (data.isEmpty()) {
                issueTrendChart.setTitle("No data available");
                return;
            }

            issueTrendChart.setTitle("Books Issued Per Month");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Issued");
            for (int i = data.size() - 1; i >= 0; i--) {
                series.getData().add(data.get(i));
            }
            issueTrendChart.getData().add(series);
        } catch (Exception e) {
            issueTrendChart.setTitle("No data available");
        }
    }

    private void loadReturnTrendChart(Integer branchId) {
        returnTrendChart.getData().clear();

        try {
            Connection conn = DatabaseConnection.getConnection();
            List<XYChart.Data<String, Number>> data = queryMonthlyCounts(conn, """
                SELECT strftime('%Y-%m', return_date) AS month, COUNT(*) AS count
                FROM issue_records
                WHERE status='RETURNED' AND (branch_id = ? OR ? IS NULL)
                GROUP BY month ORDER BY month DESC LIMIT 12
            """, branchId);

            if (data.isEmpty()) {
                returnTrendChart.setTitle("No data available");
                return;
            }

            returnTrendChart.setTitle("Books Returned Per Month");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Returned");
            for (int i = data.size() - 1; i >= 0; i--) {
                series.getData().add(data.get(i));
            }
            returnTrendChart.getData().add(series);
        } catch (Exception e) {
            returnTrendChart.setTitle("No data available");
        }
    }

    private void loadOverdueTrendChart(Integer branchId) {
        overdueTrendChart.getData().clear();

        try {
            Connection conn = DatabaseConnection.getConnection();
            List<XYChart.Data<String, Number>> data = queryMonthlyCounts(conn, """
                SELECT strftime('%Y-%m', due_date) AS month, COUNT(*) AS count
                FROM issue_records
                WHERE status='OVERDUE' AND (branch_id = ? OR ? IS NULL)
                GROUP BY month ORDER BY month DESC LIMIT 12
            """, branchId);

            if (data.isEmpty()) {
                overdueTrendChart.setTitle("No data available");
                return;
            }

            overdueTrendChart.setTitle("Overdue Books Per Month");
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Overdue");
            for (int i = data.size() - 1; i >= 0; i--) {
                series.getData().add(data.get(i));
            }
            overdueTrendChart.getData().add(series);
        } catch (Exception e) {
            overdueTrendChart.setTitle("No data available");
        }
    }

    private void loadCategoryChart(Integer branchId) {
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT COALESCE(category, 'Uncategorized') AS category, COUNT(*) AS count
                FROM books
                WHERE (branch_id = ? OR ? IS NULL)
                GROUP BY category
                ORDER BY count DESC
            """)) {
                bindBranchFilter(stmt, branchId, 1);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        pieData.add(new PieChart.Data(safe(rs.getString("category")), rs.getInt("count")));
                    }
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }

        categoryChart.setData(pieData);
        categoryChart.setTitle(pieData.isEmpty() ? "No data available" : "Books by Category");
    }

    private void loadMemberTypeChart(Integer branchId) {
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT COALESCE(member_type, 'Unknown') AS member_type, COUNT(*) AS count
                FROM members
                WHERE active = 1 AND (branch_id = ? OR ? IS NULL)
                GROUP BY member_type
                ORDER BY count DESC
            """)) {
                bindBranchFilter(stmt, branchId, 1);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        pieData.add(new PieChart.Data(safe(rs.getString("member_type")), rs.getInt("count")));
                    }
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }

        memberTypeChart.setData(pieData);
        memberTypeChart.setTitle(pieData.isEmpty() ? "No data available" : "Member Distribution");
    }

    private void loadBranchComparisonChart() {
        branchComparisonChart.getData().clear();

        XYChart.Series<String, Number> issuesSeries = new XYChart.Series<>();
        issuesSeries.setName("Issues");

        XYChart.Series<String, Number> returnsSeries = new XYChart.Series<>();
        returnsSeries.setName("Returns");

        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("""
                SELECT b.name AS branch,
                       SUM(CASE WHEN ir.status='ISSUED' THEN 1 ELSE 0 END) AS issued_count,
                       SUM(CASE WHEN ir.status='RETURNED' THEN 1 ELSE 0 END) AS returned_count
                FROM branches b
                LEFT JOIN issue_records ir ON ir.branch_id = b.id
                WHERE b.active = 1
                GROUP BY b.id, b.name
                ORDER BY b.name
            """)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String branch = safe(rs.getString("branch"));
                        issuesSeries.getData().add(new XYChart.Data<>(branch, rs.getInt("issued_count")));
                        returnsSeries.getData().add(new XYChart.Data<>(branch, rs.getInt("returned_count")));
                    }
                }
            }
        } catch (Exception ignored) {
            // Empty fallback.
        }

        if (issuesSeries.getData().isEmpty() && returnsSeries.getData().isEmpty()) {
            branchComparisonChart.setTitle("No data available");
            return;
        }

        branchComparisonChart.setTitle("Issues vs Returns by Branch");
        branchComparisonChart.getData().addAll(issuesSeries, returnsSeries);
    }

    @FXML
    private void showAllBranches() {
        branchCombo.setValue("All Branches");
        updateAnalytics();
    }

    @FXML
    private void exportAnalytics() {
        showAlert("Info", "Export analytics action triggered.");
    }

    @FXML
    private void generateReport() {
        showAlert("Info", "Generate report action triggered.");
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
}

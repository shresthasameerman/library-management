package com.library.controller;

import com.library.database.DatabaseConnection;
import com.library.util.SessionManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    @FXML private Label      welcomeLabel;
    @FXML private Label      roleLabel;
    @FXML private Label      avatarLabel;
    @FXML private Label      greetingLabel;
    @FXML private Label      dateLabel;
    @FXML private Label      totalBooksLabel;
    @FXML private Label      totalMembersLabel;
    @FXML private Label      issuedBooksLabel;
    @FXML private Label      overdueLabel;
    @FXML private BorderPane mainPane;

    @FXML private Button btnDashboard;
    @FXML private Button btnBooks;
    @FXML private Button btnMembers;
    @FXML private Button btnIssueReturn;

    // Saves the original dashboard center so we can restore it
    private Node dashboardCenter;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // ── Set logged-in user info ───────────────────────────────
        var user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText(user.getUsername());
            roleLabel.setText(user.getRole());

            // Avatar = first letter of username (uppercase)
            avatarLabel.setText(
                String.valueOf(user.getUsername().charAt(0)).toUpperCase()
            );
        }

        // ── Greeting based on time of day ─────────────────────────
        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good Morning! 👋"
                        : hour < 17 ? "Good Afternoon! 👋"
                        : "Good Evening! 👋";

        if (greetingLabel != null) greetingLabel.setText(greeting);

        // ── Date label ────────────────────────────────────────────
        if (dateLabel != null) {
            dateLabel.setText(
                LocalDate.now().format(
                    DateTimeFormatter.ofPattern("MMM dd, yyyy")
                )
            );
        }

        // ── Load stats ────────────────────────────────────────────
        loadStats();

        // ── Save dashboard center after scene is fully built ──────
        Platform.runLater(() -> {
            dashboardCenter = mainPane.getCenter();
        });

        // ── Set dashboard as active nav button ────────────────────
        setActiveButton(btnDashboard);
    }

    // ── Load Stats from DB ────────────────────────────────────────────
    private void loadStats() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            Statement  stmt = conn.createStatement();

            // Total books
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM books"
            );
            if (rs.next() && totalBooksLabel != null)
                totalBooksLabel.setText(String.valueOf(rs.getInt(1)));

            // Total active members
            rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM members WHERE active = 1"
            );
            if (rs.next() && totalMembersLabel != null)
                totalMembersLabel.setText(String.valueOf(rs.getInt(1)));

            // Currently issued books
            rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM issue_records WHERE status = 'ISSUED'"
            );
            if (rs.next() && issuedBooksLabel != null)
                issuedBooksLabel.setText(String.valueOf(rs.getInt(1)));

            // Overdue books
            rs = stmt.executeQuery("""
                SELECT COUNT(*) FROM issue_records
                WHERE status = 'ISSUED'
                AND due_date < DATE('now')
            """);
            if (rs.next() && overdueLabel != null)
                overdueLabel.setText(String.valueOf(rs.getInt(1)));

        } catch (Exception e) {
            System.err.println("Failed to load stats: " + e.getMessage());
        }
    }

    // ── Sidebar Navigation ────────────────────────────────────────────

    @FXML
    private void showDashboard() {
        setActiveButton(btnDashboard);
        // Restore original dashboard center
        if (dashboardCenter != null) {
            mainPane.setCenter(dashboardCenter);
        }
        // Refresh stats every time dashboard is shown
        loadStats();
    }

    @FXML
    private void showBooks() {
        setActiveButton(btnBooks);
        loadPage("/com/library/fxml/Books.fxml");
    }

    @FXML
    private void showMembers() {
        setActiveButton(btnMembers);
        loadPage("/com/library/fxml/Members.fxml");
    }

    @FXML
    private void showIssueReturn() {
        setActiveButton(btnIssueReturn);
        loadPage("/com/library/fxml/IssueReturn.fxml");
    }

    @FXML
    private void handleLogout() {
        try {
            SessionManager.logout();

            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/library/fxml/Login.fxml")
            );
            Scene scene = new Scene(loader.load(), 900, 600);
            scene.getStylesheets().add(
                getClass().getResource("/com/library/css/style.css")
                          .toExternalForm()
            );

            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.setWidth(900);
            stage.setHeight(600);
            stage.setResizable(false);
            stage.setScene(scene);
            stage.setTitle("Library Management System — Login");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Load Page into Center ─────────────────────────────────────────
    private void loadPage(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(fxmlPath)
            );
            Node page = loader.load();
            mainPane.setCenter(page);
        } catch (Exception e) {
            System.err.println("Failed to load page: " + fxmlPath);
            e.printStackTrace();
        }
    }

    // ── Set Active Nav Button ─────────────────────────────────────────
    private void setActiveButton(Button active) {
        btnDashboard.getStyleClass().remove("nav-button-active");
        btnBooks.getStyleClass().remove("nav-button-active");
        btnMembers.getStyleClass().remove("nav-button-active");
        btnIssueReturn.getStyleClass().remove("nav-button-active");
        active.getStyleClass().add("nav-button-active");
    }
}
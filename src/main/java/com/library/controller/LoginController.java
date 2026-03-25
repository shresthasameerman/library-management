package com.library.controller;

import com.library.database.DatabaseConnection;
import com.library.model.User;
import com.library.util.SessionManager;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        // ── Basic validation ──────────────────────────────────────
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        // ── Check credentials against DB ─────────────────────────
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, username, password_hash, role, branch_id FROM users WHERE username = ?"
            );
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");

                // BCrypt check — compares plain password to stored hash
                boolean passwordMatch = org.mindrot.jbcrypt.BCrypt.checkpw(
                    password, storedHash
                );

                if (passwordMatch) {
                    String role = rs.getString("role");
                    Integer branchId = rs.getObject("branch_id") != null
                        ? rs.getInt("branch_id")
                        : null;

                    if (("ADMIN".equals(role) || "LIBRARIAN".equals(role))
                            && branchId == null) {
                        showError("Access denied: librarian account is not assigned to a branch.");
                        return;
                    }

                    // ── Login success ─────────────────────────────
                    User user = new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        role,
                        branchId
                    );
                    SessionManager.setCurrentUser(user);
                    System.out.println("✓ Logged in as: " + user);

                    loadDashboard();
                } else {
                    showError("Incorrect password. Please try again.");
                }
            } else {
                showError("User not found. Check your username.");
            }

        } catch (Exception e) {
            showError("Login failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/library/fxml/Dashboard.fxml")
            );
            Scene scene = new Scene(loader.load(), 1100, 680);
            scene.getStylesheets().add(
                getClass().getResource("/com/library/css/style.css").toExternalForm()
            );

            // Get current stage and switch scene
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setWidth(1100);
            stage.setHeight(680);
            stage.setResizable(true);
            stage.setScene(scene);
            stage.setTitle("Library Management System — Dashboard");

        } catch (Exception e) {
            showError("Failed to load dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #ef233c; -fx-font-size: 12px;");
    }
}

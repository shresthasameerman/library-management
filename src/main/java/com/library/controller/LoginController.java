package com.library.controller;

import com.library.database.DatabaseConnection;
import com.library.model.Member;
import com.library.model.User;
import com.library.service.MemberService;
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

    private final MemberService memberService = new MemberService();

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
                    String normalizedRole = "SUPER_ADMIN".equals(role)
                        ? "SUPERADMIN"
                        : role;
                    Integer branchId = rs.getObject("branch_id") != null
                        ? rs.getInt("branch_id")
                        : null;

                        if (("ADMIN".equals(normalizedRole) || "LIBRARIAN".equals(normalizedRole))
                            && branchId == null) {
                        showError("Access denied: librarian account is not assigned to a branch.");
                        return;
                    }

                    // ── Login success ─────────────────────────────
                    User user;
                    if ("STUDENT".equals(normalizedRole)) {
                        Member member = memberService.getMemberByMemberId(username);
                        if (member == null) {
                            showError("Student profile not found for this Member ID.");
                            return;
                        }
                        if (!member.isActive()) {
                            showError("This student account is inactive.");
                            return;
                        }

                        user = new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            normalizedRole,
                            branchId,
                            member.getId()
                        );
                    } else {
                        user = new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            normalizedRole,
                            branchId
                        );
                    }
                    SessionManager.setCurrentUser(user);
                    System.out.println("✓ Logged in as: " + user);

                    loadDashboard(normalizedRole);
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

    private void loadDashboard(String role) {
        try {
            String fxmlFile;
            String title;
            
            // Navigate based on user role
            if ("SUPERADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
                fxmlFile = "/com/library/fxml/SuperAdminDashboard.fxml";
                title = "Library Management System — SuperAdmin Dashboard";
            } else if ("STUDENT".equals(role)) {
                fxmlFile = "/com/library/fxml/StudentDashboard.fxml";
                title = "Library Management System — Student Portal";
            } else {
                // ADMIN or LIBRARIAN
                fxmlFile = "/com/library/fxml/Dashboard.fxml";
                title = "Library Management System — Dashboard";
            }
            
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource(fxmlFile)
            );
            Scene scene = new Scene(loader.load(), 1100, 680);
            scene.getStylesheets().add(
                getClass().getResource("/com/library/css/style.css").toExternalForm()
            );
            if ("SUPERADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
                scene.getStylesheets().add(
                    getClass().getResource("/com/library/css/superadmin.css").toExternalForm()
                );
            } else if ("STUDENT".equals(role)) {
                scene.getStylesheets().add(
                    getClass().getResource("/com/library/css/style.css").toExternalForm()
                );
            }

            // Get current stage and switch scene
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setWidth(1100);
            stage.setHeight(680);
            stage.setResizable(true);
            stage.setScene(scene);
            stage.setTitle(title);

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

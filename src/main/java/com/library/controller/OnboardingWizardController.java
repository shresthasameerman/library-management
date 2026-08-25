package com.library.controller;

import com.library.database.DatabaseConnection;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

public class OnboardingWizardController {

    @FXML private VBox step1;
    @FXML private VBox step2;
    @FXML private VBox step3;

    @FXML private TextField libraryNameField;
    @FXML private TextField libraryCodeField;

    @FXML private TextField bookTitleField;
    @FXML private TextField bookAuthorField;
    @FXML private TextField bookIsbnField;

    @FXML private TextField adminUserField;
    @FXML private PasswordField adminPassField;

    @FXML private Button btnBack;
    @FXML private Button btnNext;
    @FXML private Label statusLabel;

    private int currentStep = 1;

    @FXML
    public void initialize() {
        showStep(currentStep);
    }

    @FXML
    private void handleNext() {
        statusLabel.setText("");

        if (currentStep == 1) {
            if (libraryNameField.getText().isEmpty() || libraryCodeField.getText().isEmpty()) {
                statusLabel.setText("Please fill all fields in Step 1.");
                return;
            }
            currentStep++;
        } else if (currentStep == 2) {
            if (bookTitleField.getText().isEmpty() || bookAuthorField.getText().isEmpty() || bookIsbnField.getText().isEmpty()) {
                statusLabel.setText("Please fill all fields in Step 2.");
                return;
            }
            currentStep++;
        } else if (currentStep == 3) {
            if (adminUserField.getText().isEmpty() || adminPassField.getText().isEmpty()) {
                statusLabel.setText("Please fill all fields in Step 3.");
                return;
            }
            finishOnboarding();
            return;
        }

        showStep(currentStep);
    }

    @FXML
    private void handleBack() {
        if (currentStep > 1) {
            currentStep--;
            showStep(currentStep);
            statusLabel.setText("");
        }
    }

    private void showStep(int step) {
        step1.setVisible(step == 1);
        step2.setVisible(step == 2);
        step3.setVisible(step == 3);

        step1.setManaged(step == 1);
        step2.setManaged(step == 2);
        step3.setManaged(step == 3);

        btnBack.setDisable(step == 1);
        
        if (step == 3) {
            btnNext.setText("Finish Setup");
        } else {
            btnNext.setText("Next");
        }
    }

    private void finishOnboarding() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Create Branch
                int branchId = -1;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO branches (name, department, code) VALUES (?, 'General', ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, libraryNameField.getText().trim());
                    stmt.setString(2, libraryCodeField.getText().trim());
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            branchId = rs.getInt(1);
                        }
                    }
                }

                // 2. Add Book
                int bookId = -1;
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO books (title, author, isbn, branch_id) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, bookTitleField.getText().trim());
                    stmt.setString(2, bookAuthorField.getText().trim());
                    stmt.setString(3, bookIsbnField.getText().trim());
                    stmt.setInt(4, branchId);
                    stmt.executeUpdate();
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            bookId = rs.getInt(1);
                        }
                    }
                }

                // Add Book Copy
                String accessionNo = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO book_copies (book_id, accession_number, branch_id) VALUES (?, ?, ?)")) {
                    stmt.setInt(1, bookId);
                    stmt.setString(2, accessionNo);
                    stmt.setInt(3, branchId);
                    stmt.executeUpdate();
                }

                // 3. Create Admin User
                String hashed = BCrypt.hashpw(adminPassField.getText(), BCrypt.gensalt());
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO users (username, password_hash, role, branch_id) VALUES (?, ?, 'ADMIN', ?)")) {
                    stmt.setString(1, adminUserField.getText().trim());
                    stmt.setString(2, hashed);
                    stmt.setInt(3, branchId);
                    stmt.executeUpdate();
                }

                conn.commit();
                
                // Close Wizard
                Stage stage = (Stage) btnNext.getScene().getWindow();
                stage.close();

            } catch (Exception ex) {
                conn.rollback();
                statusLabel.setText("Error during setup: " + ex.getMessage());
            }
        } catch (Exception ex) {
            statusLabel.setText("Database error: " + ex.getMessage());
        }
    }
}

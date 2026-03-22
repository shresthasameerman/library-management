package com.library.controller;

import com.library.model.Member;
import com.library.service.MemberService;
import com.library.util.AlertHelper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class MemberController implements Initializable {

    @FXML private TableView<Member>           membersTable;
    @FXML private TableColumn<Member,Integer> colId;
    @FXML private TableColumn<Member,String>  colMemberId;
    @FXML private TableColumn<Member,String>  colName;
    @FXML private TableColumn<Member,String>  colDepartment;
    @FXML private TableColumn<Member,String>  colPhone;
    @FXML private TableColumn<Member,String>  colEmail;
    @FXML private TableColumn<Member,String>  colStatus;
    @FXML private TableColumn<Member,Void>    colActions;
    @FXML private TextField                   searchField;
    @FXML private ComboBox<String>            departmentFilter;
    @FXML private CheckBox                    showInactiveCheck;
    @FXML private Label                       statusLabel;

    private final MemberService          memberService = new MemberService();
    private final ObservableList<Member> memberList    = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
        setupDepartmentFilter();
        loadMembers();
    }

    // ── Table Setup ───────────────────────────────────────────────────
    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colMemberId.setCellValueFactory(new PropertyValueFactory<>("memberId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("department"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));

        // Status column — green Active, red Inactive
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(value);
                    setStyle("Active".equals(value)
                        ? "-fx-text-fill: #2dc653; -fx-font-weight: bold;"
                        : "-fx-text-fill: #e63946; -fx-font-weight: bold;");
                }
            }
        });

        // Actions column
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn       = new Button("✏️ Edit");
            private final Button deactivateBtn = new Button("🚫");
            private final HBox   box           = new HBox(6, editBtn, deactivateBtn);

            {
                editBtn.setStyle(
                    "-fx-background-color: #4361ee; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 4 10 4 10;");
                deactivateBtn.setStyle(
                    "-fx-background-color: #e63946; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 4 8 4 8;");

                editBtn.setOnAction(e -> {
                    Member m = getTableView().getItems().get(getIndex());
                    openMemberForm(m);
                });
                deactivateBtn.setOnAction(e -> {
                    Member m = getTableView().getItems().get(getIndex());
                    handleDeactivate(m);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Member m = getTableView().getItems().get(getIndex());
                    // Change button based on active status
                    if (m.isActive()) {
                        deactivateBtn.setText("🚫");
                        deactivateBtn.setStyle(
                            "-fx-background-color: #e63946; -fx-text-fill: white;" +
                            "-fx-font-size: 11px; -fx-background-radius: 4;" +
                            "-fx-cursor: hand; -fx-padding: 4 8 4 8;");
                    } else {
                        deactivateBtn.setText("✅");
                        deactivateBtn.setStyle(
                            "-fx-background-color: #2dc653; -fx-text-fill: white;" +
                            "-fx-font-size: 11px; -fx-background-radius: 4;" +
                            "-fx-cursor: hand; -fx-padding: 4 8 4 8;");
                    }
                    setGraphic(box);
                }
            }
        });

        membersTable.setItems(memberList);
    }

    private void setupDepartmentFilter() {
        departmentFilter.setItems(FXCollections.observableArrayList(
            "All Departments", "Computer Science", "Business",
            "Engineering", "Medicine", "Law", "Arts",
            "Science", "Management", "Other"
        ));
        departmentFilter.setValue("All Departments");
    }

    // ── Load Members ──────────────────────────────────────────────────
    private void loadMembers() {
        boolean showInactive = showInactiveCheck != null
                               && showInactiveCheck.isSelected();
        List<Member> members = memberService.searchMembers("", !showInactive);
        memberList.setAll(members);
        updateStatus(members.size());
    }

    private void updateStatus(int count) {
        statusLabel.setText("Total: " + count + " member(s)");
    }

    // ── Search ────────────────────────────────────────────────────────
    @FXML
    private void handleSearch() {
        String keyword    = searchField.getText().trim();
        boolean activeOnly = !showInactiveCheck.isSelected();
        List<Member> results = memberService.searchMembers(keyword, activeOnly);
        memberList.setAll(results);
        updateStatus(results.size());
    }

    @FXML
    private void handleDepartmentFilter() {
        String selected = departmentFilter.getValue();
        if (selected == null || selected.equals("All Departments")) {
            loadMembers();
        } else {
            boolean activeOnly = !showInactiveCheck.isSelected();
            List<Member> results = memberService.searchMembers(selected, activeOnly);
            memberList.setAll(results);
            updateStatus(results.size());
        }
    }

    @FXML
    private void handleShowInactive() {
        loadMembers();
    }

    @FXML
    private void handleClearSearch() {
        searchField.clear();
        departmentFilter.setValue("All Departments");
        showInactiveCheck.setSelected(false);
        loadMembers();
    }

    // ── Add Member ────────────────────────────────────────────────────
    @FXML
    private void handleAddMember() {
        openMemberForm(null);
    }

    // ── Deactivate / Reactivate ───────────────────────────────────────
    private void handleDeactivate(Member member) {
        if (member.isActive()) {
            // Check for active issues first
            if (memberService.hasActiveIssues(member.getId())) {
                AlertHelper.showError("Cannot Deactivate",
                    member.getName() + " has books that haven't been returned yet.\n" +
                    "Please return all books before deactivating.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Deactivate Member");
            confirm.setHeaderText("Deactivate " + member.getName() + "?");
            confirm.setContentText(
                "This member will lose library access.\n" +
                "You can reactivate them later."
            );
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                memberService.deactivateMember(member.getId());
                loadMembers();
            }
        } else {
            // Reactivate
            memberService.reactivateMember(member.getId());
            AlertHelper.showSuccess("Reactivated",
                member.getName() + " is now active again.");
            loadMembers();
        }
    }

    // ── Member Form (Add / Edit) ──────────────────────────────────────
    private void openMemberForm(Member existing) {
        boolean isEdit = existing != null;

        // ── Fields ───────────────────────────────────────────────────
        TextField        nameField   = new TextField();
        TextField        idField     = new TextField();
        TextField        phoneField  = new TextField();
        TextField        emailField  = new TextField();
        ComboBox<String> deptBox     = new ComboBox<>();
        Label            errorLabel  = new Label();

        nameField.setPromptText("e.g. Sameer Thapa");
        phoneField.setPromptText("e.g. 9800000000");
        emailField.setPromptText("e.g. sameer@college.edu");

        deptBox.setItems(FXCollections.observableArrayList(
            "Computer Science", "Business", "Engineering",
            "Medicine", "Law", "Arts", "Science", "Management", "Other"
        ));
        deptBox.setPromptText("Select department");
        deptBox.setPrefWidth(Double.MAX_VALUE);

        errorLabel.setStyle("-fx-text-fill: #e63946; -fx-font-size: 12px;");

        // Auto-generate Member ID for new members
        if (!isEdit) {
            idField.setText(memberService.generateMemberId());
        }

        // Pre-fill if editing
        if (isEdit) {
            nameField.setText(existing.getName());
            idField.setText(existing.getMemberId());
            phoneField.setText(existing.getPhone());
            emailField.setText(existing.getEmail());
            deptBox.setValue(existing.getDepartment());
        }

        // ── Layout ────────────────────────────────────────────────────
        String labelStyle =
            "-fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-text-fill: #333333;";

        VBox form = new VBox(14);
        form.setPadding(new Insets(24, 28, 10, 28));
        form.setPrefWidth(460);

        // Set field widths
        nameField.setPrefWidth(280);  nameField.setPrefHeight(36);
        idField.setPrefWidth(280);    idField.setPrefHeight(36);
        phoneField.setPrefWidth(280); phoneField.setPrefHeight(36);
        emailField.setPrefWidth(280); emailField.setPrefHeight(36);
        deptBox.setPrefHeight(36);

        form.getChildren().addAll(
            fieldBox("Full Name *",   labelStyle, nameField),
            fieldBox("Member ID *",   labelStyle, idField),
            fieldBox("Phone",         labelStyle, phoneField),
            fieldBox("Email",         labelStyle, emailField),
            fieldBox("Department",    labelStyle, deptBox),
            errorLabel
        );

        // ── Buttons ───────────────────────────────────────────────────
        Button saveBtn   = new Button(isEdit ? "💾  Update Member"
                                             : "➕  Register Member");
        Button cancelBtn = new Button("Cancel");

        saveBtn.setPrefWidth(180); saveBtn.setPrefHeight(40);
        saveBtn.setStyle(
            "-fx-background-color: #4361ee; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 14px;" +
            "-fx-background-radius: 8; -fx-cursor: hand;");

        cancelBtn.setPrefWidth(100); cancelBtn.setPrefHeight(40);
        cancelBtn.setStyle(
            "-fx-background-color: #eef1fb; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand;");

        HBox btnBox = new HBox(12, saveBtn, cancelBtn);
        btnBox.setPadding(new Insets(10, 28, 24, 28));
        btnBox.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(0, form, btnBox);
        root.setStyle("-fx-background-color: white;");

        // ── Dialog ────────────────────────────────────────────────────
        Stage dialog = new Stage();
        dialog.setTitle(isEdit ? "Edit Member" : "Register New Member");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setScene(new Scene(root));
        dialog.setMinWidth(460);
        dialog.setResizable(false);

        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            String name   = nameField.getText().trim();
            String mId    = idField.getText().trim();
            String phone  = phoneField.getText().trim();
            String email  = emailField.getText().trim();
            String dept   = deptBox.getValue();

            // Validation
            if (name.isEmpty()) {
                errorLabel.setText("⚠ Full name is required.");
                return;
            }
            if (mId.isEmpty()) {
                errorLabel.setText("⚠ Member ID is required.");
                return;
            }
            if (memberService.memberIdExists(mId,
                    isEdit ? existing.getId() : 0)) {
                errorLabel.setText("⚠ This Member ID already exists.");
                return;
            }

            Member member = new Member(
                isEdit ? existing.getId() : 0,
                name, email, phone, mId,
                dept != null ? dept : "Other",
                true
            );

            boolean success = isEdit
                ? memberService.updateMember(member)
                : memberService.addMember(member);

            if (success) {
                dialog.close();
                loadMembers();
                AlertHelper.showSuccess("Success",
                    isEdit ? "Member updated successfully!"
                           : member.getName() + " registered successfully!");
            } else {
                errorLabel.setText("⚠ Failed to save. Please try again.");
            }
        });

        dialog.showAndWait();
    }

    // ── Helper: label + field pair ────────────────────────────────────
    private VBox fieldBox(String labelText, String labelStyle,
                          javafx.scene.Node field) {
        Label label = new Label(labelText);
        label.setStyle(labelStyle);
        return new VBox(6, label, field);
    }
}
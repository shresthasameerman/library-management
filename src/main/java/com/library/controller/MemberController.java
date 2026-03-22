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
    @FXML private TableColumn<Member,String>  colType;
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
    private final ObservableList<Member> memberList    =
                                         FXCollections.observableArrayList();

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

        // ── Type column — badge style ─────────────────────────────────
        colType.setCellValueFactory(new PropertyValueFactory<>("memberType"));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(
                        "Staff".equals(value) ? "👨‍💼 Staff" : "🎓 Student"
                    );
                    badge.setStyle(
                        "Staff".equals(value)
                        ? "-fx-background-color: #fff4e6;" +
                          "-fx-text-fill: #f77f00;" +
                          "-fx-font-weight: bold; -fx-font-size: 11px;" +
                          "-fx-background-radius: 4; -fx-padding: 3 8 3 8;"
                        : "-fx-background-color: #eef1fb;" +
                          "-fx-text-fill: #4361ee;" +
                          "-fx-font-weight: bold; -fx-font-size: 11px;" +
                          "-fx-background-radius: 4; -fx-padding: 3 8 3 8;"
                    );
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // ── Status column ─────────────────────────────────────────────
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(value);
                    setStyle("Active".equals(value)
                        ? "-fx-text-fill: #2dc653; -fx-font-weight: bold;"
                        : "-fx-text-fill: #e63946; -fx-font-weight: bold;");
                }
            }
        });

        // ── Actions column ────────────────────────────────────────────
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn       = new Button("✏️");
            private final Button deactivateBtn = new Button("🚫");
            private final Button deleteBtn     = new Button("🗑️");
            private final HBox   box           = new HBox(5,
                                                   editBtn,
                                                   deactivateBtn,
                                                   deleteBtn);
            {
                editBtn.setStyle(
                    "-fx-background-color: #4361ee; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 5 8 5 8;");
                editBtn.setTooltip(new Tooltip("Edit member"));

                deactivateBtn.setStyle(
                    "-fx-background-color: #f77f00; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 5 8 5 8;");

                deleteBtn.setStyle(
                    "-fx-background-color: #e63946; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 5 8 5 8;");
                deleteBtn.setTooltip(new Tooltip("Permanently remove"));

                editBtn.setOnAction(e -> {
                    Member m = getTableView().getItems().get(getIndex());
                    openMemberForm(m);
                });
                deactivateBtn.setOnAction(e -> {
                    Member m = getTableView().getItems().get(getIndex());
                    handleDeactivate(m);
                });
                deleteBtn.setOnAction(e -> {
                    Member m = getTableView().getItems().get(getIndex());
                    handleDelete(m);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Member m = getTableView().getItems().get(getIndex());
                    if (m.isActive()) {
                        deactivateBtn.setText("🚫");
                        deactivateBtn.setTooltip(new Tooltip("Deactivate"));
                        deactivateBtn.setStyle(
                            "-fx-background-color: #f77f00; -fx-text-fill: white;" +
                            "-fx-font-size: 11px; -fx-background-radius: 4;" +
                            "-fx-cursor: hand; -fx-padding: 5 8 5 8;");
                    } else {
                        deactivateBtn.setText("✅");
                        deactivateBtn.setTooltip(new Tooltip("Reactivate"));
                        deactivateBtn.setStyle(
                            "-fx-background-color: #2dc653; -fx-text-fill: white;" +
                            "-fx-font-size: 11px; -fx-background-radius: 4;" +
                            "-fx-cursor: hand; -fx-padding: 5 8 5 8;");
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

    // ── Search & Filters ──────────────────────────────────────────────
    @FXML
    private void handleSearch() {
        String keyword = searchField.getText().trim();
        boolean activeOnly = !showInactiveCheck.isSelected();
        List<Member> results = memberService.searchMembers(keyword, activeOnly);
        memberList.setAll(results);
        updateStatus(results.size());
    }

    @FXML
    private void handleDepartmentFilter() {
        String selected = departmentFilter.getValue();
        boolean activeOnly = !showInactiveCheck.isSelected();
        if (selected == null || selected.equals("All Departments")) {
            loadMembers();
        } else {
            List<Member> results = memberService.searchMembers(selected, activeOnly);
            memberList.setAll(results);
            updateStatus(results.size());
        }
    }

    @FXML private void handleShowInactive() { loadMembers(); }

    @FXML
    private void handleClearSearch() {
        searchField.clear();
        departmentFilter.setValue("All Departments");
        showInactiveCheck.setSelected(false);
        loadMembers();
    }

    @FXML
    private void handleAddMember() { openMemberForm(null); }

    // ── Deactivate / Reactivate ───────────────────────────────────────
    private void handleDeactivate(Member member) {
        if (member.isActive()) {
            if (memberService.hasActiveIssues(member.getId())) {
                AlertHelper.showError("Cannot Deactivate",
                    member.getName() + " still has books issued.\n" +
                    "Please return all books first.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Deactivate Member");
            confirm.setHeaderText("Deactivate " + member.getName() + "?");
            confirm.setContentText(
                "They will lose library access.\n" +
                "You can reactivate them anytime."
            );
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                memberService.deactivateMember(member.getId());
                loadMembers();
            }
        } else {
            memberService.reactivateMember(member.getId());
            AlertHelper.showSuccess("Reactivated",
                member.getName() + " is now active again.");
            loadMembers();
        }
    }

    // ── Permanently Delete ────────────────────────────────────────────
    private void handleDelete(Member member) {
        if (memberService.hasActiveIssues(member.getId())) {
            AlertHelper.showError("Cannot Delete",
                member.getName() + " still has books issued.\n" +
                "Please return all books before deleting.");
            return;
        }

        ButtonType removeBtn = new ButtonType("Remove",
                                   ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel",
                                   ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Member");
        confirm.setHeaderText("Permanently remove " + member.getName() + "?");
        confirm.setContentText(
            "Type: " + member.getMemberType() +
            " | ID: " + member.getMemberId() + "\n\n" +
            "⚠ This CANNOT be undone.\n" +
            "Members with borrowing history cannot be deleted.\n" +
            "Use 🚫 Deactivate for graduated students with history."
        );
        confirm.getButtonTypes().setAll(removeBtn, cancelBtn);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == removeBtn) {
            boolean deleted = memberService.deleteMember(member.getId());
            if (deleted) {
                loadMembers();
                AlertHelper.showSuccess("Removed",
                    member.getName() + " removed from the system.");
            } else {
                AlertHelper.showError("Cannot Delete",
                    "This member has borrowing history.\n" +
                    "Use 🚫 Deactivate instead.");
            }
        }
    }

    // ── Member Form (Add / Edit) ──────────────────────────────────────
    private void openMemberForm(Member existing) {
        boolean isEdit = existing != null;

        TextField        nameField  = new TextField();
        TextField        idField    = new TextField();
        TextField        phoneField = new TextField();
        TextField        emailField = new TextField();
        ComboBox<String> deptBox    = new ComboBox<>();
        ComboBox<String> typeBox    = new ComboBox<>();
        Label            errorLabel = new Label();

        nameField.setPromptText("e.g. Sameer Thapa");
        phoneField.setPromptText("e.g. 9800000000");
        emailField.setPromptText("e.g. sameer@college.edu");

        typeBox.setItems(FXCollections.observableArrayList(
            "Student", "Staff"
        ));
        typeBox.setValue("Student");
        typeBox.setPrefWidth(Double.MAX_VALUE);
        typeBox.setPrefHeight(36);

        deptBox.setItems(FXCollections.observableArrayList(
            "Computer Science", "Business", "Engineering",
            "Medicine", "Law", "Arts", "Science", "Management", "Other"
        ));
        deptBox.setPromptText("Select department");
        deptBox.setPrefWidth(Double.MAX_VALUE);
        deptBox.setPrefHeight(36);

        errorLabel.setStyle(
            "-fx-text-fill: #e63946; -fx-font-size: 12px;"
        );

        // Set field sizes
        for (TextField tf : new TextField[]{
                nameField, idField, phoneField, emailField}) {
            tf.setPrefWidth(280);
            tf.setPrefHeight(36);
        }

        // Auto-generate ID for new members
        if (!isEdit) idField.setText(memberService.generateMemberId());

        // Pre-fill if editing
        if (isEdit) {
            nameField.setText(existing.getName());
            idField.setText(existing.getMemberId());
            phoneField.setText(existing.getPhone());
            emailField.setText(existing.getEmail());
            deptBox.setValue(existing.getDepartment());
            typeBox.setValue(existing.getMemberType());
        }

        String labelStyle =
            "-fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-text-fill: #333333;";

        VBox form = new VBox(14);
        form.setPadding(new Insets(24, 28, 10, 28));
        form.setPrefWidth(460);
        form.getChildren().addAll(
            fieldBox("Member Type *", labelStyle, typeBox),
            fieldBox("Full Name *",   labelStyle, nameField),
            fieldBox("Member ID *",   labelStyle, idField),
            fieldBox("Phone",         labelStyle, phoneField),
            fieldBox("Email",         labelStyle, emailField),
            fieldBox("Department",    labelStyle, deptBox),
            errorLabel
        );

        Button saveBtn   = new Button(isEdit ? "💾  Update"
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

        Stage dialog = new Stage();
        dialog.setTitle(isEdit ? "Edit Member" : "Register New Member");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setScene(new Scene(root));
        dialog.setMinWidth(460);
        dialog.setResizable(false);

        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            String name  = nameField.getText().trim();
            String mId   = idField.getText().trim();
            String phone = phoneField.getText().trim();
            String email = emailField.getText().trim();
            String dept  = deptBox.getValue();
            String type  = typeBox.getValue();

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
                type != null ? type : "Student",
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
                           : member.getName() + " registered!");
            } else {
                errorLabel.setText("⚠ Failed to save. Try again.");
            }
        });

        dialog.showAndWait();
    }

    private VBox fieldBox(String labelText, String labelStyle,
                          javafx.scene.Node field) {
        Label label = new Label(labelText);
        label.setStyle(labelStyle);
        return new VBox(6, label, field);
    }
}
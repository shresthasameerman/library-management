package com.library.controller;

import com.library.model.Member;
import com.library.model.IssueRecord;
import com.library.service.IssueService;
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
import javafx.stage.Window;

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
    @FXML private TableColumn<Member,String>  colIntake;
    @FXML private TableColumn<Member,String>  colPhone;
    @FXML private TableColumn<Member,String>  colStatus;
    @FXML private TableColumn<Member,Void>    colActions;

    @FXML private TextField      searchField;
    @FXML private ComboBox<String> courseFilter;
    @FXML private ComboBox<String> intakeFilter;
    @FXML private ComboBox<String> typeFilter;
    @FXML private CheckBox        showInactiveCheck;
    @FXML private Label           statusLabel;
    @FXML private Label           filterInfoLabel;

    private final MemberService          memberService = new MemberService();
    private final IssueService           issueService  = new IssueService();
    private final ObservableList<Member> memberList    =
                                         FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
        setupFilters();
        loadMembers();
    }

    // ── Table Columns ─────────────────────────────────────────────────
    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colMemberId.setCellValueFactory(new PropertyValueFactory<>("memberId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setCellFactory(col -> new TableCell<>() {
            private final Hyperlink nameLink = new Hyperlink();
            {
                nameLink.setOnAction(e -> {
                    Member member = getTableView().getItems().get(getIndex());
                    if (member != null) {
                        openMemberProfile(member);
                    }
                });
                nameLink.setStyle(
                    "-fx-text-fill: #4361ee; -fx-font-size: 13px;" +
                    "-fx-font-weight: bold;"
                );
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) {
                    setGraphic(null);
                } else {
                    nameLink.setText(value);
                    setGraphic(nameLink);
                }
                setText(null);
            }
        });
        colDepartment.setCellValueFactory(new PropertyValueFactory<>("department"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));

        // Intake column
        colIntake.setCellValueFactory(new PropertyValueFactory<>("intake"));
        colIntake.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) {
                    setText("—");
                    setStyle("-fx-text-fill: #c0c8e0;");
                } else {
                    setText(value);
                    setStyle("-fx-text-fill: #4a5568;");
                }
            }
        });

        // Type badge
        colType.setCellValueFactory(new PropertyValueFactory<>("memberType"));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null); setGraphic(null); return;
                }
                Label badge = new Label(
                    "Staff".equals(value) ? "👨‍💼 Staff" : "🎓 Student"
                );
                badge.setStyle("Staff".equals(value)
                    ? "-fx-background-color: #fff4e6; -fx-text-fill: #f77f00;" +
                      "-fx-font-weight: bold; -fx-font-size: 11px;" +
                      "-fx-background-radius: 4; -fx-padding: 3 8 3 8;"
                    : "-fx-background-color: #eef1fb; -fx-text-fill: #4361ee;" +
                      "-fx-font-weight: bold; -fx-font-size: 11px;" +
                      "-fx-background-radius: 4; -fx-padding: 3 8 3 8;"
                );
                setGraphic(badge); setText(null);
            }
        });

        // Status
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setText(null); return; }
                setText(value);
                setStyle("Active".equals(value)
                    ? "-fx-text-fill: #2dc653; -fx-font-weight: bold;"
                    : "-fx-text-fill: #e63946; -fx-font-weight: bold;");
            }
        });

        // Actions
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn       = new Button("✏️");
            private final Button deactivateBtn = new Button("🚫");
            private final Button deleteBtn     = new Button("🗑️");
            private final HBox   box = new HBox(5, editBtn, deactivateBtn, deleteBtn);

            {
                editBtn.setStyle(
                    "-fx-background-color: #4361ee; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 5 8 5 8;");
                deactivateBtn.setStyle(
                    "-fx-background-color: #f77f00; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 5 8 5 8;");
                deleteBtn.setStyle(
                    "-fx-background-color: #e63946; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 5 8 5 8;");

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
                if (empty) { setGraphic(null); return; }
                Member m = getTableView().getItems().get(getIndex());
                if (m.isActive()) {
                    deactivateBtn.setText("🚫");
                    deactivateBtn.setStyle(
                        "-fx-background-color: #f77f00; -fx-text-fill: white;" +
                        "-fx-font-size: 11px; -fx-background-radius: 4;" +
                        "-fx-cursor: hand; -fx-padding: 5 8 5 8;");
                    deactivateBtn.setTooltip(new Tooltip("Deactivate"));
                } else {
                    deactivateBtn.setText("✅");
                    deactivateBtn.setStyle(
                        "-fx-background-color: #2dc653; -fx-text-fill: white;" +
                        "-fx-font-size: 11px; -fx-background-radius: 4;" +
                        "-fx-cursor: hand; -fx-padding: 5 8 5 8;");
                    deactivateBtn.setTooltip(new Tooltip("Reactivate"));
                }
                setGraphic(box);
            }
        });

        membersTable.setItems(memberList);
    }

    // ── Setup Filters ─────────────────────────────────────────────────
    private void setupFilters() {
        // Course filter
        var courses = FXCollections.observableArrayList("All Courses");
        courses.addAll(MemberService.COURSES);
        courseFilter.setItems(courses);
        courseFilter.setValue("All Courses");

        // Intake filter
        var intakes = FXCollections.observableArrayList("All Intakes");
        intakes.addAll(MemberService.INTAKES);
        intakeFilter.setItems(intakes);
        intakeFilter.setValue("All Intakes");

        // Type filter
        typeFilter.setItems(FXCollections.observableArrayList(
            "All Types", "Student", "Staff"
        ));
        typeFilter.setValue("All Types");
    }

    // ── Load Members ──────────────────────────────────────────────────
    private void loadMembers() {
        applyFilters();
    }

    private void applyFilters() {
        String keyword    = searchField.getText().trim();
        String course     = courseFilter.getValue();
        String intake     = intakeFilter.getValue();
        String type       = typeFilter.getValue();
        boolean activeOnly = !showInactiveCheck.isSelected();

        // Normalize "All" values
        String courseFilter2 = (course == null || course.equals("All Courses"))
                               ? "All" : course;
        String intakeFilter2 = (intake == null || intake.equals("All Intakes"))
                               ? "All" : intake;

        List<Member> results = memberService.searchMembersFiltered(
            keyword, courseFilter2, intakeFilter2, activeOnly
        );

        // Apply type filter in memory
        if (type != null && !type.equals("All Types")) {
            results = results.stream()
                .filter(m -> type.equals(m.getMemberType()))
                .toList();
        }

        memberList.setAll(results);
        updateStatus(results.size(), courseFilter2, intakeFilter2, type);
    }

    private void updateStatus(int count, String course,
                               String intake, String type) {
        statusLabel.setText("Showing: " + count + " member(s)");

        StringBuilder info = new StringBuilder();
        if (!"All".equals(course))       info.append("📚 ").append(course).append("  ");
        if (!"All".equals(intake))       info.append("📅 ").append(intake).append("  ");
        if (!"All Types".equals(type))   info.append("👤 ").append(type);
        filterInfoLabel.setText(info.toString().trim());
    }

    @FXML private void handleSearch()       { applyFilters(); }
    @FXML private void handleFilterChange() { applyFilters(); }

    @FXML
    private void handleClearSearch() {
        searchField.clear();
        courseFilter.setValue("All Courses");
        intakeFilter.setValue("All Intakes");
        typeFilter.setValue("All Types");
        showInactiveCheck.setSelected(false);
        applyFilters();
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
            confirm.setContentText("They will lose library access.\n" +
                                   "You can reactivate them anytime.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                memberService.deactivateMember(member.getId());
                applyFilters();
            }
        } else {
            memberService.reactivateMember(member.getId());
            AlertHelper.showSuccess("Reactivated",
                member.getName() + " is now active again.");
            applyFilters();
        }
    }

    // ── Delete ────────────────────────────────────────────────────────
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
            "Course: " + member.getDepartment() +
            " | Intake: " + member.getIntake() + "\n\n" +
            "⚠ This CANNOT be undone.\n" +
            "Members with borrowing history cannot be deleted.\n" +
            "Use 🚫 Deactivate for graduated students with history."
        );
        confirm.getButtonTypes().setAll(removeBtn, cancelBtn);
        confirm.showAndWait().ifPresent(result -> {
            if (result == removeBtn) {
                boolean deleted = memberService.deleteMember(member.getId());
                if (deleted) {
                    applyFilters();
                    AlertHelper.showSuccess("Removed",
                        member.getName() + " removed from the system.");
                } else {
                    AlertHelper.showError("Cannot Delete",
                        "This member has borrowing history.\n" +
                        "Use 🚫 Deactivate instead.");
                }
            }
        });
    }

    // ── Member Form ───────────────────────────────────────────────────
    private void openMemberForm(Member existing) {
        boolean isEdit = existing != null;

        TextField        nameField  = new TextField();
        TextField        idField    = new TextField();
        TextField        phoneField = new TextField();
        TextField        emailField = new TextField();
        ComboBox<String> typeBox    = new ComboBox<>();
        ComboBox<String> courseBox  = new ComboBox<>();
        ComboBox<String> intakeBox  = new ComboBox<>();
        Label            errorLabel = new Label();

        nameField.setPromptText("e.g. Sameer Thapa");
        phoneField.setPromptText("e.g. 9800000000");
        emailField.setPromptText("e.g. sameer@college.edu");

        // Type
        typeBox.setItems(FXCollections.observableArrayList("Student", "Staff"));
        typeBox.setValue("Student");
        typeBox.setPrefWidth(Double.MAX_VALUE);
        typeBox.setPrefHeight(38);

        // Course
        courseBox.setItems(FXCollections.observableArrayList(
            MemberService.COURSES
        ));
        courseBox.setPromptText("Select course");
        courseBox.setPrefWidth(Double.MAX_VALUE);
        courseBox.setPrefHeight(38);

        // Intake — auto update based on type
        intakeBox.setItems(FXCollections.observableArrayList(
            MemberService.INTAKES
        ));
        intakeBox.setPromptText("Select intake");
        intakeBox.setPrefWidth(Double.MAX_VALUE);
        intakeBox.setPrefHeight(38);

        // When type = Staff, set intake to N/A and disable
        typeBox.valueProperty().addListener((obs, old, newVal) -> {
            if ("Staff".equals(newVal)) {
                courseBox.setValue("Staff");
                intakeBox.setValue("N/A (Staff)");
                intakeBox.setDisable(true);
                courseBox.setDisable(true);
            } else {
                intakeBox.setDisable(false);
                courseBox.setDisable(false);
                if ("Staff".equals(courseBox.getValue()))
                    courseBox.setValue(null);
                if ("N/A (Staff)".equals(intakeBox.getValue()))
                    intakeBox.setValue(null);
            }
        });

        errorLabel.setStyle("-fx-text-fill: #e63946; -fx-font-size: 12px;");

        for (TextField tf : new TextField[]{
                nameField, idField, phoneField, emailField}) {
            tf.setPrefWidth(300);
            tf.setPrefHeight(38);
        }

        if (!isEdit) idField.setText(memberService.generateMemberId());

        if (isEdit) {
            nameField.setText(existing.getName());
            idField.setText(existing.getMemberId());
            phoneField.setText(existing.getPhone());
            emailField.setText(existing.getEmail());
            typeBox.setValue(existing.getMemberType());
            courseBox.setValue(existing.getDepartment());
            intakeBox.setValue(existing.getIntake());
        }

        String labelStyle =
            "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #333;";

        VBox form = new VBox(12);
        form.setPadding(new Insets(24, 28, 10, 28));
        form.setPrefWidth(480);
        form.getChildren().addAll(
            fieldBox("Member Type *", labelStyle, typeBox),
            fieldBox("Full Name *",   labelStyle, nameField),
            fieldBox("Member ID *",   labelStyle, idField),
            fieldBox("Course *",      labelStyle, courseBox),
            fieldBox("Intake Session *", labelStyle, intakeBox),
            fieldBox("Phone",         labelStyle, phoneField),
            fieldBox("Email",         labelStyle, emailField),
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
        dialog.initOwner(Window.getWindows().stream()
            .filter(Window::isShowing).findFirst().orElse(null));
        dialog.setScene(new Scene(root));
        dialog.setMinWidth(480);
        dialog.setResizable(false);

        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            String name   = nameField.getText().trim();
            String mId    = idField.getText().trim();
            String phone  = phoneField.getText().trim();
            String email  = emailField.getText().trim();
            String type   = typeBox.getValue();
            String course = courseBox.getValue();
            String intake = intakeBox.getValue();

            if (name.isEmpty()) {
                errorLabel.setText("⚠ Full name is required."); return;
            }
            if (mId.isEmpty()) {
                errorLabel.setText("⚠ Member ID is required."); return;
            }
            if (course == null) {
                errorLabel.setText("⚠ Please select a course."); return;
            }
            if (intake == null) {
                errorLabel.setText("⚠ Please select an intake."); return;
            }
            if (memberService.memberIdExists(mId,
                    isEdit ? existing.getId() : 0)) {
                errorLabel.setText("⚠ This Member ID already exists."); return;
            }

            Member member = new Member(
                isEdit ? existing.getId() : 0,
                name, email, phone, mId,
                course, type != null ? type : "Student",
                intake, true
            );

            boolean success = isEdit
                ? memberService.updateMember(member)
                : memberService.addMember(member);

            if (success) {
                dialog.close();
                applyFilters();
                AlertHelper.showSuccess("Success",
                    isEdit ? "Member updated!"
                           : member.getName() + " registered!");
            } else {
                errorLabel.setText("⚠ Failed to save. Try again.");
            }
        });

        dialog.showAndWait();
    }

    // ── Helper ────────────────────────────────────────────────────────
    private void openMemberProfile(Member member) {
        List<IssueRecord> activeIssues = issueService.getIssuedBooksByMember(member.getId());
        List<IssueRecord> previousIssues = issueService.getReturnedBooksByMember(member.getId());

        Label title = new Label("Member Profile");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(24);
        infoGrid.setVgap(10);
        infoGrid.setPadding(new Insets(8, 0, 4, 0));

        addInfoRow(infoGrid, 0, "Name", member.getName(), "Member ID", member.getMemberId());
        addInfoRow(infoGrid, 1, "Phone", blankAsDash(member.getPhone()), "Email", blankAsDash(member.getEmail()));
        addInfoRow(infoGrid, 2, "Type", member.getMemberType(), "Course", blankAsDash(member.getDepartment()));
        addInfoRow(infoGrid, 3, "Intake", blankAsDash(member.getIntake()), "Status", member.getStatus());

        Label currentLabel = new Label("Currently Issued Books");
        currentLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        TableView<IssueRecord> currentTable = createCurrentIssueTable(activeIssues);

        Label historyLabel = new Label("Previous Books Issued");
        historyLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
        TableView<IssueRecord> historyTable = createHistoryTable(previousIssues);

        Label summaryLabel = new Label(
            "Active: " + activeIssues.size() +
            "  |  Previously Returned: " + previousIssues.size()
        );
        summaryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #4a5568;");

        Button closeBtn = new Button("Close");
        closeBtn.setPrefWidth(110);
        closeBtn.setPrefHeight(36);
        closeBtn.setStyle(
            "-fx-background-color: #4361ee; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;"
        );

        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12,
            title,
            new Separator(),
            infoGrid,
            currentLabel,
            currentTable,
            historyLabel,
            historyTable,
            summaryLabel,
            footer
        );
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: white;");

        Stage dialog = new Stage();
        dialog.setTitle("Member Profile - " + member.getName());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(Window.getWindows().stream()
            .filter(Window::isShowing).findFirst().orElse(null));
        dialog.setScene(new Scene(root, 940, 680));
        dialog.setMinWidth(900);
        dialog.setMinHeight(640);

        closeBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    private TableView<IssueRecord> createCurrentIssueTable(List<IssueRecord> records) {
        TableView<IssueRecord> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(records));
        table.setPrefHeight(190);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<IssueRecord, String> titleCol = new TableColumn<>("Book Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));

        TableColumn<IssueRecord, String> accessionCol = new TableColumn<>("Accession");
        accessionCol.setCellValueFactory(new PropertyValueFactory<>("accessionNumber"));

        TableColumn<IssueRecord, String> issueDateCol = new TableColumn<>("Issued On");
        issueDateCol.setCellValueFactory(new PropertyValueFactory<>("issueDate"));

        TableColumn<IssueRecord, String> dueDateCol = new TableColumn<>("Due Date");
        dueDateCol.setCellValueFactory(new PropertyValueFactory<>("dueDate"));

        table.getColumns().addAll(titleCol, accessionCol, issueDateCol, dueDateCol);
        table.setPlaceholder(new Label("No currently issued books."));
        return table;
    }

    private TableView<IssueRecord> createHistoryTable(List<IssueRecord> records) {
        TableView<IssueRecord> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(records));
        table.setPrefHeight(190);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<IssueRecord, String> titleCol = new TableColumn<>("Book Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));

        TableColumn<IssueRecord, String> accessionCol = new TableColumn<>("Accession");
        accessionCol.setCellValueFactory(new PropertyValueFactory<>("accessionNumber"));

        TableColumn<IssueRecord, String> issueDateCol = new TableColumn<>("Issued On");
        issueDateCol.setCellValueFactory(new PropertyValueFactory<>("issueDate"));

        TableColumn<IssueRecord, String> returnDateCol = new TableColumn<>("Returned On");
        returnDateCol.setCellValueFactory(new PropertyValueFactory<>("returnDate"));

        table.getColumns().addAll(titleCol, accessionCol, issueDateCol, returnDateCol);
        table.setPlaceholder(new Label("No previous issue history."));
        return table;
    }

    private void addInfoRow(GridPane grid, int row,
                            String leftLabel, String leftValue,
                            String rightLabel, String rightValue) {
        Label l1 = new Label(leftLabel + ":");
        l1.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        Label v1 = new Label(blankAsDash(leftValue));
        v1.setStyle("-fx-text-fill: #0f172a;");

        Label l2 = new Label(rightLabel + ":");
        l2.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        Label v2 = new Label(blankAsDash(rightValue));
        v2.setStyle("-fx-text-fill: #0f172a;");

        grid.add(l1, 0, row);
        grid.add(v1, 1, row);
        grid.add(l2, 2, row);
        grid.add(v2, 3, row);
    }

    private String blankAsDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private VBox fieldBox(String labelText, String labelStyle,
                          javafx.scene.Node field) {
        Label label = new Label(labelText);
        label.setStyle(labelStyle);
        return new VBox(6, label, field);
    }
}

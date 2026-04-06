package com.library.controller;

import com.library.model.IssueRecord;
import com.library.model.Member;
import com.library.model.User;
import com.library.service.IssueService;
import com.library.service.MemberService;
import com.library.util.SessionManager;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class StudentDashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label memberIdLabel;
    @FXML private Label courseLabel;
    @FXML private Label intakeLabel;
    @FXML private Label statusLabel;
    @FXML private Label issuedCountLabel;
    @FXML private Label historyCountLabel;
    @FXML private Label nextDueLabel;
    @FXML private Label overdueCountLabel;
    @FXML private Label fineEstimateLabel;
    @FXML private FlowPane currentBooksPane;
    @FXML private TableView<IssueRecord> historyTable;
    @FXML private TableColumn<IssueRecord, String> historyBookColumn;
    @FXML private TableColumn<IssueRecord, String> historyAccessionColumn;
    @FXML private TableColumn<IssueRecord, String> historyIssueDateColumn;
    @FXML private TableColumn<IssueRecord, String> historyDueDateColumn;
    @FXML private TableColumn<IssueRecord, String> historyReturnDateColumn;
    @FXML private TableColumn<IssueRecord, String> historyFineColumn;

    private final MemberService memberService = new MemberService();
    private final IssueService issueService = new IssueService();

    @FXML
    private void initialize() {
        setupHistoryTable();
        loadStudentData();
    }

    private void setupHistoryTable() {
        historyBookColumn.setCellValueFactory(new PropertyValueFactory<>("bookTitle"));
        historyAccessionColumn.setCellValueFactory(new PropertyValueFactory<>("accessionNumber"));
        historyIssueDateColumn.setCellValueFactory(new PropertyValueFactory<>("issueDate"));
        historyDueDateColumn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        historyReturnDateColumn.setCellValueFactory(new PropertyValueFactory<>("returnDate"));
        historyFineColumn.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getFineDisplay()));
    }

    private void loadStudentData() {
        User currentUser = SessionManager.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        Member member = resolveMember(currentUser);
        if (member == null) {
            welcomeLabel.setText(currentUser.getUsername());
            statusLabel.setText("Profile not found");
            return;
        }

        welcomeLabel.setText(member.getName());
        memberIdLabel.setText(member.getMemberId());
        courseLabel.setText(blank(member.getDepartment()));
        intakeLabel.setText(blank(member.getIntake()));
        statusLabel.setText(member.getStatus());

        List<IssueRecord> issued = issueService.getIssuedBooksByMember(member.getId());
        List<IssueRecord> returned = issueService.getReturnedBooksByMember(member.getId());

        issuedCountLabel.setText(String.valueOf(issued.size()));
        historyCountLabel.setText(String.valueOf(returned.size()));

        currentBooksPane.getChildren().clear();

        int overdueCount = 0;
        double fineEstimate = 0;
        IssueRecord soonest = null;
        LocalDate today = LocalDate.now();

        for (IssueRecord record : issued) {
            LocalDate dueDate = LocalDate.parse(record.getDueDate());
            long daysLeft = ChronoUnit.DAYS.between(today, dueDate);
            if (daysLeft < 0) {
                overdueCount++;
            }
            fineEstimate += issueService.calculateCurrentFine(record.getDueDate());
            if (soonest == null || dueDate.isBefore(LocalDate.parse(soonest.getDueDate()))) {
                soonest = record;
            }

            currentBooksPane.getChildren().add(createBookCard(record, daysLeft));
        }

        overdueCountLabel.setText(String.valueOf(overdueCount));
        fineEstimateLabel.setText("Rs. " + String.format("%.0f", fineEstimate));
        nextDueLabel.setText(soonest == null
            ? "No books issued"
            : soonest.getBookTitle() + " • " + soonest.getDueDate());

        historyTable.setItems(FXCollections.observableArrayList(returned));
    }

    private Member resolveMember(User currentUser) {
        if (currentUser.getMemberRecordId() != null) {
            return memberService.getMemberById(currentUser.getMemberRecordId());
        }
        return memberService.getMemberByMemberId(currentUser.getUsername());
    }

    private Node createBookCard(IssueRecord record, long daysLeft) {
        String tone = daysLeft < 0 ? "#e63946" : daysLeft <= 3 ? "#f77f00" : "#2dc653";
        String statusText = daysLeft < 0
            ? "Overdue by " + Math.abs(daysLeft) + " day(s)"
            : daysLeft == 0 ? "Due today" : daysLeft + " day(s) left";

        LocalDate issueDate = LocalDate.parse(record.getIssueDate());
        LocalDate dueDate = LocalDate.parse(record.getDueDate());
        long assignedDays = ChronoUnit.DAYS.between(issueDate, dueDate);
        long extensionDays = Math.max(0, assignedDays - issueService.getLoanDays());

        VBox card = new VBox(8);
        card.setPrefWidth(250);
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 18; -fx-padding: 18;" +
            "-fx-effect: dropshadow(gaussian, rgba(67,97,238,0.10), 14, 0, 0, 4);"
        );

        Label title = new Label(record.getBookTitle());
        title.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label accession = new Label("Accession: " + blank(record.getAccessionNumber()));
        accession.setStyle("-fx-text-fill: #4a5568; -fx-font-size: 12px;");

        Label issued = new Label("Issued: " + record.getIssueDate());
        issued.setStyle("-fx-text-fill: #4a5568; -fx-font-size: 12px;");

        Label due = new Label("Due: " + record.getDueDate());
        due.setStyle("-fx-text-fill: " + tone + "; -fx-font-size: 13px; -fx-font-weight: bold;");

        Label loanMeta = new Label(
            extensionDays > 0
                ? "Loan window: " + assignedDays + " days (includes +" + extensionDays + " renewal)"
                : "Loan window: " + issueService.getLoanDays() + " days"
        );
        loanMeta.setStyle("-fx-text-fill: #7c8db5; -fx-font-size: 11px;");

        Label status = new Label(statusText);
        status.setStyle(
            "-fx-background-color: " + tone + "20; -fx-text-fill: " + tone + ";" +
            "-fx-background-radius: 999; -fx-padding: 5 10 5 10; -fx-font-size: 11px; -fx-font-weight: bold;"
        );

        Label fine = new Label("Fine today: " + record.getFineDisplay());
        fine.setStyle("-fx-text-fill: #1a1a2e; -fx-font-size: 12px;");

        card.getChildren().addAll(title, accession, issued, due, loanMeta, status, fine);
        return card;
    }

    @FXML
    private void handleRefresh() {
        loadStudentData();
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
                getClass().getResource("/com/library/css/style.css").toExternalForm()
            );
            Stage stage = (Stage) currentBooksPane.getScene().getWindow();
            stage.setWidth(900);
            stage.setHeight(600);
            stage.setResizable(false);
            stage.setScene(scene);
            stage.setTitle("Library Management System — Login");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
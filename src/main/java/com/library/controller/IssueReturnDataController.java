package com.library.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.io.PrintWriter;

public class IssueReturnDataController {
    
    @FXML
    private ComboBox<String> branchFilterCombo;
    @FXML
    private ComboBox<String> statusFilterCombo;
    @FXML
    private DatePicker fromDatePicker;
    @FXML
    private DatePicker toDatePicker;
    @FXML
    private TextField memberSearchField;
    
    @FXML
    private Label totalIssuesLabel;
    @FXML
    private Label totalReturnsLabel;
    @FXML
    private Label overdueLabel;
    @FXML
    private Label totalFinesLabel;
    
    @FXML
    private TableView<IssueReturnRecord> issueReturnTable;
    @FXML
    private TableColumn<IssueReturnRecord, String> accessionColumn;
    @FXML
    private TableColumn<IssueReturnRecord, String> titleColumn;
    @FXML
    private TableColumn<IssueReturnRecord, String> memberColumn;
    @FXML
    private TableColumn<IssueReturnRecord, String> branchColumn;
    @FXML
    private TableColumn<IssueReturnRecord, String> issueDateColumn;
    @FXML
    private TableColumn<IssueReturnRecord, String> dueDateColumn;
    @FXML
    private TableColumn<IssueReturnRecord, String> returnDateColumn;
    @FXML
    private TableColumn<IssueReturnRecord, String> fineColumn;
    @FXML
    private TableColumn<IssueReturnRecord, String> statusColumn;

    private ObservableList<IssueReturnRecord> allRecords = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        loadBranches();
        loadData();
        setupTableColumns();
    }

    private void loadBranches() {
        // Load branches from database
        ObservableList<String> branches = FXCollections.observableArrayList("All Branches", "Main Library", "Branch 2", "Branch 3");
        branchFilterCombo.setItems(branches);
        branchFilterCombo.setValue("All Branches");
    }

    private void loadData() {
        // Load issue/return data from database
        // This is placeholder data - replace with actual database queries
        allRecords.add(new IssueReturnRecord("ACC001", "The Great Gatsby", "John Doe", "Main Library", 
            "2024-01-15", "2024-01-29", "2024-01-25", "0", "RETURNED"));
        allRecords.add(new IssueReturnRecord("ACC002", "To Kill a Mockingbird", "Jane Smith", "Main Library", 
            "2024-01-16", "2024-01-30", null, "150", "OVERDUE"));
        
        issueReturnTable.setItems(allRecords);
        updateStatistics();
    }

    private void setupTableColumns() {
        accessionColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getAccessionNo()));
        titleColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getBookTitle()));
        memberColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getMember()));
        branchColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getBranch()));
        issueDateColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getIssueDate()));
        dueDateColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getDueDate()));
        returnDateColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getReturnDate()));
        fineColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty("Rs. " + param.getValue().getFine()));
        statusColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getStatus()));
    }

    @FXML
    private void applyFilters() {
        ObservableList<IssueReturnRecord> filtered = FXCollections.observableArrayList();
        
        String branch = branchFilterCombo.getValue();
        String status = statusFilterCombo.getValue();
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();
        String member = memberSearchField.getText().toLowerCase();
        
        for (IssueReturnRecord record : allRecords) {
            boolean matches = true;
            
            if (!branch.equals("All Branches") && !record.getBranch().equals(branch)) matches = false;
            if (!status.equals("All") && !record.getStatus().equals(status)) matches = false;
            if (!member.isEmpty() && !record.getMember().toLowerCase().contains(member)) matches = false;
            
            if (matches) filtered.add(record);
        }
        
        issueReturnTable.setItems(filtered);
        updateStatistics();
    }

    @FXML
    private void resetFilters() {
        branchFilterCombo.setValue("All Branches");
        statusFilterCombo.setValue("All");
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        memberSearchField.clear();
        issueReturnTable.setItems(allRecords);
        updateStatistics();
    }

    private void updateStatistics() {
        long totalIssues = issueReturnTable.getItems().size();
        long returned = issueReturnTable.getItems().stream()
            .filter(r -> r.getStatus().equals("RETURNED")).count();
        long overdue = issueReturnTable.getItems().stream()
            .filter(r -> r.getStatus().equals("OVERDUE")).count();
        double totalFines = issueReturnTable.getItems().stream()
            .mapToDouble(r -> Double.parseDouble(r.getFine())).sum();
        
        totalIssuesLabel.setText(String.valueOf(totalIssues));
        totalReturnsLabel.setText(String.valueOf(returned));
        overdueLabel.setText(String.valueOf(overdue));
        totalFinesLabel.setText("Rs. " + String.format("%.2f", totalFines));
    }

    @FXML
    private void exportToCSV() {
        try {
            PrintWriter writer = new PrintWriter("issue_return_data.csv");
            writer.println("Accession No.,Book Title,Member,Branch,Issue Date,Due Date,Return Date,Fine,Status");
            for (IssueReturnRecord record : issueReturnTable.getItems()) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    record.getAccessionNo(), record.getBookTitle(), record.getMember(),
                    record.getBranch(), record.getIssueDate(), record.getDueDate(),
                    record.getReturnDate(), record.getFine(), record.getStatus()));
            }
            writer.close();
            showAlert("Success", "Data exported to issue_return_data.csv");
        } catch (IOException e) {
            showAlert("Error", "Failed to export CSV: " + e.getMessage());
        }
    }

    @FXML
    private void exportToPDF() {
        showAlert("Info", "PDF export feature coming soon");
    }

    @FXML
    private void printData() {
        showAlert("Info", "Print feature coming soon");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for table data
    public static class IssueReturnRecord {
        private String accessionNo, bookTitle, member, branch, issueDate, dueDate, returnDate, fine, status;

        public IssueReturnRecord(String accessionNo, String bookTitle, String member, String branch,
                               String issueDate, String dueDate, String returnDate, String fine, String status) {
            this.accessionNo = accessionNo;
            this.bookTitle = bookTitle;
            this.member = member;
            this.branch = branch;
            this.issueDate = issueDate;
            this.dueDate = dueDate;
            this.returnDate = returnDate;
            this.fine = fine;
            this.status = status;
        }

        public String getAccessionNo() { return accessionNo; }
        public String getBookTitle() { return bookTitle; }
        public String getMember() { return member; }
        public String getBranch() { return branch; }
        public String getIssueDate() { return issueDate; }
        public String getDueDate() { return dueDate; }
        public String getReturnDate() { return returnDate; }
        public String getFine() { return fine; }
        public String getStatus() { return status; }
    }
}

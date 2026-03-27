package com.library.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class NotificationsController {
    
    @FXML
    private Label criticalAlertsLabel;
    @FXML
    private Label warningsLabel;
    @FXML
    private Label infoMessagesLabel;
    @FXML
    private Label totalUnreadLabel;
    
    @FXML
    private ComboBox<String> alertTypeFilter;
    @FXML
    private ComboBox<String> severityFilter;
    @FXML
    private ComboBox<String> branchFilter;
    
    @FXML
    private TableView<Notification> overdueTable;
    @FXML
    private TableView<Notification> highFinesTable;
    @FXML
    private TableView<Notification> lowInventoryTable;
    @FXML
    private TableView<Notification> systemAlertsTable;
    @FXML
    private TableView<Notification> userActivityTable;
    
    @FXML
    private CheckBox overdueCheckbox;
    @FXML
    private CheckBox fineCheckbox;
    @FXML
    private CheckBox inventoryCheckbox;
    @FXML
    private CheckBox systemCheckbox;

    private ObservableList<Notification> allNotifications = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        loadBranches();
        loadNotifications();
        updateNotificationSummary();
    }

    private void loadBranches() {
        ObservableList<String> branches = FXCollections.observableArrayList("All", "Main Library", "Branch 2", "Branch 3");
        branchFilter.setItems(branches);
        branchFilter.setValue("All");
    }

    private void loadNotifications() {
        // Overdue Books
        ObservableList<Notification> overdueBooks = FXCollections.observableArrayList();
        overdueBooks.add(new Notification("ACC001", "The Great Gatsby", "John Doe", "Main Library", "2024-01-15", 6, "900"));
        overdueBooks.add(new Notification("ACC002", "1984", "Jane Smith", "Branch 2", "2024-01-14", 7, "1050"));
        overdueTable.setItems(overdueBooks);
        
        // High Fines
        ObservableList<Notification> finePending = FXCollections.observableArrayList();
        finePending.add(new Notification("MEM001", "John Doe", "Main Library", "", "2024-01-20", 3, "1200"));
        finePending.add(new Notification("MEM002", "Jane Smith", "Branch 2", "", "2024-01-19", 5, "2100"));
        highFinesTable.setItems(finePending);
        
        // Low Inventory
        ObservableList<Notification> lowInv = FXCollections.observableArrayList();
        lowInv.add(new Notification("", "Python Programming", "", "", "", 0, "2"));
        lowInv.add(new Notification("", "Data Science 101", "", "", "", 0, "1"));
        lowInventoryTable.setItems(lowInv);
        
        // System Alerts
        ObservableList<Notification> sysAlerts = FXCollections.observableArrayList();
        sysAlerts.add(new Notification("2024-01-20 15:30", "WARNING", "Database", "Slow query detected on issue_records table", "", 0, ""));
        sysAlerts.add(new Notification("2024-01-20 14:00", "ERROR", "Backup", "Daily backup failed - check disk space", "", 0, ""));
        systemAlertsTable.setItems(sysAlerts);
    }

    private void updateNotificationSummary() {
        criticalAlertsLabel.setText("2");
        warningsLabel.setText("5");
        infoMessagesLabel.setText("12");
        totalUnreadLabel.setText("19");
    }

    @FXML
    private void applyFilters() {
        showAlert("Info", "Notifications filtered");
    }

    @FXML
    private void clearAllNotifications() {
        if (confirmAction("Clear All", "Clear all notifications?")) {
            showAlert("Success", "All notifications cleared");
            criticalAlertsLabel.setText("0");
            warningsLabel.setText("0");
            infoMessagesLabel.setText("0");
            totalUnreadLabel.setText("0");
        }
    }

    @FXML
    private void refreshNotifications() {
        loadNotifications();
        updateNotificationSummary();
        showAlert("Success", "Notifications refreshed");
    }

    @FXML
    private void markOverduePending() {
        showAlert("Info", "Selected overdue books marked as pending");
    }

    @FXML
    private void sendReminderEmail() {
        showAlert("Success", "Reminder emails sent to " + overdueTable.getItems().size() + " members");
    }

    @FXML
    private void sendPaymentReminder() {
        showAlert("Success", "Payment reminders sent to " + highFinesTable.getItems().size() + " members");
    }

    @FXML
    private void generateFineNotice() {
        showAlert("Success", "Fine notices generated for pending fines");
    }

    @FXML
    private void requestPurchase() {
        showAlert("Info", "Purchase request created for low inventory books");
    }

    @FXML
    private void resolveAlert() {
        showAlert("Success", "Alert marked as resolved");
    }

    @FXML
    private void viewFullAuditLog() {
        showAlert("Info", "Full audit log will open in new window");
    }

    @FXML
    private void savePreferences() {
        showAlert("Success", "Notification preferences saved");
    }

    @FXML
    private void exportNotifications() {
        showAlert("Success", "Notifications exported to CSV");
    }

    @FXML
    private void generateNotificationReport() {
        showAlert("Success", "Notification report generated");
    }

    private boolean confirmAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText("Confirm");
        alert.setContentText(message);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class Notification {
        private String field1, field2, field3, field4, field5;
        private int field6;
        private String field7;

        public Notification(String field1, String field2, String field3, String field4, String field5, int field6, String field7) {
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
            this.field4 = field4;
            this.field5 = field5;
            this.field6 = field6;
            this.field7 = field7;
        }
    }
}

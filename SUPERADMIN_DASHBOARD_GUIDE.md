# SuperAdmin Dashboard - Integration & Usage Guide

## Overview

The SuperAdmin Dashboard is a comprehensive management system for library administrators with access to all branches. It provides read-only data views and system management capabilities without the ability to issue or return books directly.

## Features

### 1. **Issue/Return Data View**
- **Functionality**: View all issued and returned books across all branches
- **Filters**:
  - Branch (dropdown)
  - Status (ISSUED, RETURNED, OVERDUE)
  - Date Range (from/to date)
  - Member Search
- **Displays**:
  - Statistics: Total Issues, Total Returns, Overdue Books, Total Fines
  - Detailed table with: Accession No., Book Title, Member, Branch, Issue Date, Due Date, Return Date, Fine, Status
- **Export Options**: CSV, PDF, Print
- **Key Use Case**: Monitor all circulating books across the library system

### 2. **Branch Analytics**
- **Functionality**: View performance metrics and trends for single or all branches
- **Statistics Cards**:
  - Total Books
  - Available Books
  - Issued Books
  - Total Members
- **Visualizations**:
  - Line Charts: Issue trends over time, Return trends over time
  - Bar Chart: Overdue books trend
  - Pie Charts: Books by category, Member distribution
  - Bar Chart: Branch performance comparison
- **Export**: Analytics report, Custom report generation
- **Key Use Case**: Track library performance, identify trends, compare branch effectiveness

### 3. **User & Admin Oversight**
- **Functionality**: Manage all admin and librarian accounts system-wide
- **Features**:
  - Filter admins by branch, role (ADMIN/LIBRARIAN), status (Active/Inactive)
  - View last login and activity for each admin
  - Add new admin accounts
  - View activity logs for all users
- **Statistics**: Total Admins, Active, Inactive
- **Export**: Admin list, Activity logs
- **Key Use Case**: Monitor admin activities, manage permissions, track system access

### 4. **Branch Management**
- **Functionality**: Administer all library branches
- **Operations**:
  - Add/Edit/Delete branches
  - Assign admins to branches
  - View branch performance metrics (issues this month, returns, fines collected)
  - View selected branch details
- **Metrics Per Branch**:
  - Total Books, Available Books
  - Total Members, Admin assigned
  - Performance data (issues, returns, fines)
- **Key Use Case**: Manage multi-branch operations, assign responsibilities

### 5. **Reports**
- **Custom Reports**:
  - Select report type (Issue/Return Summary, Branch Performance, Member Activity, Fine Collection, Overdue Books, Category Distribution, Admin Activity, Complete System Report)
  - Choose branch, date range, format (CSV, Excel, PDF, JSON)
- **Quick Report Templates**:
  - Today's Issues
  - Weekly/Monthly Summary
  - Overdue Alert
  - Fine Collection Report
  - Admin Activity Report
  - Member Statistics
  - Inventory Report
- **Report History**: View, download previously generated reports
- **Statistics**: Track total reports generated, monthly reports, total downloads
- **Key Use Case**: Generate insights, create audit trails, professional reporting

### 6. **System Management**
- **Database Management**:
  - Backup database (with timestamp)
  - Restore from backup
  - Health check
  - Optimize database
- **System Info**:
  - Application version, Java version
  - Total users, books, members, branches
  - Last backup time, system uptime
- **Health Monitoring**:
  - Disk space usage
  - Memory usage
  - Database status
- **Dangerous Actions** (with confirmation):
  - Clear all fines
  - Reset overdue status
- **Audit Logs**: View, filter, export system logs
- **Key Use Case**: Monitor system health, maintain data integrity, troubleshoot

### 7. **Notifications & Alerts**
- **Alert Types**:
  - **Overdue Books**: Books overdue >5 days with expected fines
  - **High Fines**: Members with fines >Rs. 500
  - **Low Inventory**: Books with <3 available copies
  - **System Alerts**: Database/backup/error alerts
  - **User Activity**: Suspicious or notable activities
- **Actions**:
  - Mark as pending
  - Send reminder/payment emails
  - Generate notices
  - Request purchases
  - Resolve alerts
- **Notification Settings**:
  - Enable/disable notifications by type
  - Custom preferences
- **Export**: Notifications as CSV, Generate reports
- **Key Use Case**: Proactive monitoring, timely interventions, data-driven decisions

## Integration Steps

### Step 1: Update `LoginController.java`
When SuperAdmin logs in, check if role is "SUPERADMIN" and navigate to SuperAdminDashboard:

```java
if (user.getRole().equals("SUPERADMIN")) {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("SuperAdminDashboard.fxml"));
    Scene scene = new Scene(loader.load());
    Stage stage = (Stage) loginButton.getScene().getWindow();
    stage.setScene(scene);
    stage.show();
} else {
    // Existing dashboard logic
}
```

### Step 2: Update `App.java`
Ensure SuperAdminDashboard.fxml is copied to resources folder and app initializes correctly.

### Step 3: Create Service Methods
Implement database queries in service classes:

```java
// In IssueService
public List<IssueRecord> getAllIssueRecords(LocalDate from, LocalDate to) {
    // Query all branches with date filter
}

// In BranchService
public List<BranchStats> getAllBranchStats() {
    // Return statistics for all branches
}

// In UserService
public List<User> getAllAdmins() {
    // Return all admin/librarian users
}
```

### Step 4: Replace Placeholder Data
Update controllers to load actual data from database:

```java
// Replace in each controller
private void loadData() {
    // Use service methods to query database
    allRecords = issueService.getAllIssueRecords(...);
    issueReturnTable.setItems(allRecords);
}
```

### Step 5: Add ExportService
Create utility class for exporting reports:

```java
public class ExportService {
    public static void exportToCSV(TableView table, String filename) { ... }
    public static void exportToPDF(ObservableList data, String filename) { ... }
    public static void exportToExcel(ObservableList data, String filename) { ... }
}
```

## Database Schema Updates

### New Tables (Optional)
```sql
-- Activity Logs
CREATE TABLE activity_logs (
    log_id INTEGER PRIMARY KEY,
    user_id INTEGER,
    action TEXT,
    module TEXT,
    timestamp DATETIME,
    details TEXT,
    FOREIGN KEY(user_id) REFERENCES users(id)
);

-- System Audit
CREATE TABLE system_audit (
    audit_id INTEGER PRIMARY KEY,
    timestamp DATETIME,
    level TEXT,
    module TEXT,
    message TEXT
);

-- Notifications
CREATE TABLE notifications (
    notification_id INTEGER PRIMARY KEY,
    type TEXT,
    severity TEXT,
    message TEXT,
    read_status INTEGER,
    timestamp DATETIME,
    branch_id INTEGER
);
```

## Styling & Theming

The dashboard uses a professional color scheme:
- **Header**: Dark blue (#2c3e50)
- **Primary**: Light gray (#ecf0f1)
- **Success**: Green (#2ecc71)
- **Warning**: Orange (#f39c12)
- **Error**: Red (#e74c3c)
- **Info**: Blue (#3498db)

Customize in FXML or CSS file for consistent branding.

## Security Considerations

1. **Read-Only Access**: SuperAdmin can view but not modify core library transactions
2. **Permission Checks**: Always verify SUPERADMIN role before granting access
3. **Audit Trail**: Log all actions in activity_logs table
4. **Backup Protection**: Require confirmation for dangerous operations
5. **Data Export**: Implement usage limits on report generation

## Performance Tips

1. **Lazy Load**: Load charts and tables only when tab is selected
2. **Pagination**: Implement pagination for large tables (>1000 rows)
3. **Caching**: Cache branch statistics for 5 minutes
4. **Indexes**: Add database indexes on frequently filtered columns

## Future Enhancements

- [ ] Real-time notifications (WebSocket)
- [ ] Custom dashboard layouts
- [ ] Export to Excel with formatting
- [ ] Scheduled report generation
- [ ] Email delivery of reports
- [ ] Advanced charting (third-party library)
- [ ] Data visualization updates (D3.js/Chart.js)
- [ ] Mobile responsive design
- [ ] Dark mode toggle
- [ ] Multi-language support

## Testing Checklist

- [ ] All filters work correctly
- [ ] Charts render proper data
- [ ] Export functions generate valid files
- [ ] Statistics calculations are accurate
- [ ] All buttons and links function
- [ ] Error messages display clearly
- [ ] Large datasets load smoothly
- [ ] Responsive layout works on different screen sizes

## Contact & Support

For issues or feature requests related to SuperAdmin Dashboard, ensure:
1. Database connectivity is working
2. SUPERADMIN account exists in system
3. All required FXML and controller files are in place
4. Java version 21+ installed
5. JavaFX 21+ properly configured in Maven

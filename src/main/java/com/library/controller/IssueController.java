package com.library.controller;
import com.library.model.Book;
import com.library.model.IssueRecord;
import com.library.model.Member;
import com.library.service.BookService;
import com.library.service.IssueService;
import com.library.service.MemberService;
import com.library.util.AlertHelper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class IssueController implements Initializable {

    // ── Issue Tab ─────────────────────────────────────────────────────
    @FXML private TextField  memberSearchField;
    @FXML private TextField  bookSearchField;
    @FXML private Label      memberInfoLabel;
    @FXML private Label      bookInfoLabel;
    @FXML private Label      dueDateLabel;
    @FXML private Label      issueErrorLabel;
    @FXML private Label      totalIssuedLabel;
    @FXML private Label      totalOverdueLabel;
    @FXML private TextField  issuedSearchField;

    @FXML private TableView<IssueRecord>           issuedTable;
    @FXML private TableColumn<IssueRecord,Integer> colIssueId;
    @FXML private TableColumn<IssueRecord,String>  colIssueMember;
    @FXML private TableColumn<IssueRecord,String>  colIssueMemberId;
    @FXML private TableColumn<IssueRecord,String>  colIssueBook;
    @FXML private TableColumn<IssueRecord,String>  colIssueDate;
    @FXML private TableColumn<IssueRecord,String>  colDueDate;
    @FXML private TableColumn<IssueRecord,String>  colIssueFine;
    @FXML private TableColumn<IssueRecord,String>  colIssueStatus;

    // ── Return Tab ────────────────────────────────────────────────────
    @FXML private TextField  returnSearchField;

    @FXML private TableView<IssueRecord>           returnTable;
    @FXML private TableColumn<IssueRecord,Integer> colReturnId;
    @FXML private TableColumn<IssueRecord,String>  colReturnMember;
    @FXML private TableColumn<IssueRecord,String>  colReturnMemberId;
    @FXML private TableColumn<IssueRecord,String>  colReturnBook;
    @FXML private TableColumn<IssueRecord,String>  colReturnIssueDate;
    @FXML private TableColumn<IssueRecord,String>  colReturnDueDate;
    @FXML private TableColumn<IssueRecord,String>  colReturnFine;
    @FXML private TableColumn<IssueRecord,Void>    colReturnAction;

    // ── Overdue Tab ───────────────────────────────────────────────────
    @FXML private TableView<IssueRecord>           overdueTable;
    @FXML private TableColumn<IssueRecord,String>  colOvMember;
    @FXML private TableColumn<IssueRecord,String>  colOvMemberId;
    @FXML private TableColumn<IssueRecord,String>  colOvBook;
    @FXML private TableColumn<IssueRecord,String>  colOvIssueDate;
    @FXML private TableColumn<IssueRecord,String>  colOvDueDate;
    @FXML private TableColumn<IssueRecord,String>  colOvFine;
    @FXML private TableColumn<IssueRecord,Void>    colOvAction;

    // ── Services ──────────────────────────────────────────────────────
    private final IssueService  issueService  = new IssueService();
    private final BookService   bookService   = new BookService();
    private final MemberService memberService = new MemberService();

    // ── State ─────────────────────────────────────────────────────────
    private Member selectedMember = null;
    private Book   selectedBook   = null;

    private final ObservableList<IssueRecord> issuedList  =
        FXCollections.observableArrayList();
    private final ObservableList<IssueRecord> returnList  =
        FXCollections.observableArrayList();
    private final ObservableList<IssueRecord> overdueList =
        FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupIssuedTable();
        setupReturnTable();
        setupOverdueTable();
        loadIssuedBooks();
        loadOverdueBooks();
        updateStats();

        // Set due date preview
        dueDateLabel.setText(
            "Due date: " + LocalDate.now()
                .plusDays(issueService.getLoanDays())
                .toString()
        );
    }

    // ── Setup Tables ──────────────────────────────────────────────────
    private void setupIssuedTable() {
        colIssueId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colIssueMember.setCellValueFactory(
            new PropertyValueFactory<>("memberName"));
        colIssueMemberId.setCellValueFactory(
            new PropertyValueFactory<>("memberId2"));
        colIssueBook.setCellValueFactory(
            new PropertyValueFactory<>("bookTitle"));
        colIssueDate.setCellValueFactory(
            new PropertyValueFactory<>("issueDate"));
        colDueDate.setCellValueFactory(
            new PropertyValueFactory<>("dueDate"));

        // Fine so far (live calculation)
        colIssueFine.setCellValueFactory(
            new PropertyValueFactory<>("dueDate"));
        colIssueFine.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String dueDate, boolean empty) {
                super.updateItem(dueDate, empty);
                if (empty || dueDate == null) {
                    setText(null);
                    setStyle("");
                } else {
                    double fine = issueService.calculateCurrentFine(dueDate);
                    if (fine > 0) {
                        setText("Rs. " + (int) fine);
                        setStyle("-fx-text-fill: #e63946; -fx-font-weight: bold;");
                    } else {
                        setText("-");
                        setStyle("-fx-text-fill: #2dc653;");
                    }
                }
            }
        });

        // Status with color
        colIssueStatus.setCellValueFactory(
            new PropertyValueFactory<>("status"));
        colIssueStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setText(null); return; }
                IssueRecord rec = getTableRow().getItem();
                if (rec != null) {
                    double fine = issueService.calculateCurrentFine(
                        rec.getDueDate());
                    if (fine > 0) {
                        setText("⚠ OVERDUE");
                        setStyle("-fx-text-fill: #e63946; -fx-font-weight: bold;");
                    } else {
                        setText("✅ On Time");
                        setStyle("-fx-text-fill: #2dc653; -fx-font-weight: bold;");
                    }
                }
            }
        });

        issuedTable.setItems(issuedList);
    }

    private void setupReturnTable() {
        colReturnId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colReturnMember.setCellValueFactory(
            new PropertyValueFactory<>("memberName"));
        colReturnMemberId.setCellValueFactory(
            new PropertyValueFactory<>("memberId2"));
        colReturnBook.setCellValueFactory(
            new PropertyValueFactory<>("bookTitle"));
        colReturnIssueDate.setCellValueFactory(
            new PropertyValueFactory<>("issueDate"));
        colReturnDueDate.setCellValueFactory(
            new PropertyValueFactory<>("dueDate"));

        // Fine column — live calc
        colReturnFine.setCellValueFactory(
            new PropertyValueFactory<>("dueDate"));
        colReturnFine.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String dueDate, boolean empty) {
                super.updateItem(dueDate, empty);
                if (empty || dueDate == null) {
                    setText(null); return;
                }
                double fine = issueService.calculateCurrentFine(dueDate);
                if (fine > 0) {
                    setText("Rs. " + (int) fine);
                    setStyle("-fx-text-fill: #e63946; -fx-font-weight: bold;");
                } else {
                    setText("No fine");
                    setStyle("-fx-text-fill: #2dc653;");
                }
            }
        });

        // Return button column
        colReturnAction.setCellFactory(col -> new TableCell<>() {
            private final Button returnBtn = new Button("📥 Return");
            {
                returnBtn.setStyle(
                    "-fx-background-color: #4361ee; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 5 10 5 10;");
                returnBtn.setOnAction(e -> {
                    IssueRecord rec = getTableView().getItems().get(getIndex());
                    handleReturnBook(rec);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : returnBtn);
            }
        });

        returnTable.setItems(returnList);
    }

    private void setupOverdueTable() {
        colOvMember.setCellValueFactory(
            new PropertyValueFactory<>("memberName"));
        colOvMemberId.setCellValueFactory(
            new PropertyValueFactory<>("memberId2"));
        colOvBook.setCellValueFactory(
            new PropertyValueFactory<>("bookTitle"));
        colOvIssueDate.setCellValueFactory(
            new PropertyValueFactory<>("issueDate"));
        colOvDueDate.setCellValueFactory(
            new PropertyValueFactory<>("dueDate"));

        // Fine column
        colOvFine.setCellValueFactory(
            new PropertyValueFactory<>("dueDate"));
        colOvFine.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String dueDate, boolean empty) {
                super.updateItem(dueDate, empty);
                if (empty || dueDate == null) { setText(null); return; }
                double fine = issueService.calculateCurrentFine(dueDate);
                setText("Rs. " + (int) fine);
                setStyle("-fx-text-fill: #e63946; -fx-font-weight: bold;");
            }
        });

        // Return button
        colOvAction.setCellFactory(col -> new TableCell<>() {
            private final Button returnBtn = new Button("📥 Return");
            {
                returnBtn.setStyle(
                    "-fx-background-color: #e63946; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 5 10 5 10;");
                returnBtn.setOnAction(e -> {
                    IssueRecord rec = getTableView().getItems().get(getIndex());
                    handleReturnBook(rec);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : returnBtn);
            }
        });

        overdueTable.setItems(overdueList);
    }

    // ── Find Member ───────────────────────────────────────────────────
        @FXML
        private void handleFindMember() {
            String keyword = memberSearchField.getText().trim();
            if (keyword.isEmpty()) {
                memberInfoLabel.setText("⚠ Enter Member ID, name, or course.");
                memberInfoLabel.setStyle("-fx-text-fill: #e63946;");
                return;
            }

            List<Member> results = memberService.searchMembers(keyword, true);
            if (results.isEmpty()) {
                memberInfoLabel.setText("❌ No active member found.");
                memberInfoLabel.setStyle("-fx-text-fill: #e63946;");
                selectedMember = null;
            } else if (results.size() == 1) {
                selectedMember = results.get(0);
                memberInfoLabel.setText(
                    "✅ " + selectedMember.getName() +
                    "  |  " + selectedMember.getMemberId() +
                    "  |  " + selectedMember.getDepartment() +
                    "  |  " + selectedMember.getIntake()
                );
                memberInfoLabel.setStyle("-fx-text-fill: #2dc653;");
            } else {
                selectedMember = results.get(0);
                memberInfoLabel.setText(
                    "✅ " + selectedMember.getName() +
                    " (" + selectedMember.getDepartment() + ")" +
                    " — " + results.size() + " results, showing first"
                );
                memberInfoLabel.setStyle("-fx-text-fill: #f77f00;");
            }
        }

    // ── Find Book ─────────────────────────────────────────────────────
    @FXML
    private void handleFindBook() {
        String keyword = bookSearchField.getText().trim();
        if (keyword.isEmpty()) {
            bookInfoLabel.setText("⚠ Enter book title or ISBN.");
            bookInfoLabel.setStyle("-fx-text-fill: #e63946;");
            return;
        }

        List<Book> results = bookService.searchBooks(keyword);
        // Filter to available books only
        List<Book> available = results.stream()
            .filter(b -> b.getAvailableCopies() > 0)
            .toList();

        if (available.isEmpty()) {
            bookInfoLabel.setText("❌ No available copies found.");
            bookInfoLabel.setStyle("-fx-text-fill: #e63946;");
            selectedBook = null;
        } else if (available.size() == 1) {
            selectedBook = available.get(0);
            bookInfoLabel.setText(
                "✅ " + selectedBook.getTitle() +
                " | " + selectedBook.getAvailableCopies() + " copies available"
            );
            bookInfoLabel.setStyle("-fx-text-fill: #2dc653;");
        } else {
            selectedBook = available.get(0);
            bookInfoLabel.setText(
                "✅ " + selectedBook.getTitle() +
                " (and " + (available.size()-1) + " more — be more specific)"
            );
            bookInfoLabel.setStyle("-fx-text-fill: #f77f00;");
        }
    }

    // ── Issue Book ────────────────────────────────────────────────────
    @FXML
    private void handleIssueBook() {
        issueErrorLabel.setText("");

        if (selectedMember == null) {
            issueErrorLabel.setText("⚠ Please find and select a member first.");
            return;
        }
        if (selectedBook == null) {
            issueErrorLabel.setText("⚠ Please find and select a book first.");
            return;
        }

        boolean success = issueService.issueBook(
            selectedBook.getId(), selectedMember.getId()
        );

        if (success) {
            // Reset form
            memberSearchField.clear();
            bookSearchField.clear();
            memberInfoLabel.setText("");
            bookInfoLabel.setText("");
            selectedMember = null;
            selectedBook   = null;

            loadIssuedBooks();
            loadOverdueBooks();
            updateStats();

            AlertHelper.showSuccess("Book Issued!",
                "Book has been issued successfully.\n" +
                "Due in " + issueService.getLoanDays() + " days.");
        } else {
            issueErrorLabel.setText(
                "⚠ Could not issue. Book may be unavailable\n" +
                "or member already has this book."
            );
        }
    }

    // ── Search Issued ─────────────────────────────────────────────────
    @FXML
    private void handleSearchIssued() {
        String keyword = issuedSearchField.getText().trim();
        List<IssueRecord> results = issueService.getIssuedBooks(keyword);
        issuedList.setAll(results);
    }

    // ── Return Tab: Search ────────────────────────────────────────────
    @FXML
    private void handleSearchReturn() {
        String keyword = returnSearchField.getText().trim();
        List<IssueRecord> results = issueService.getIssuedBooks(keyword);
        returnList.setAll(results);
    }

    @FXML
    private void handleShowAllIssued() {
        returnSearchField.clear();
        List<IssueRecord> results = issueService.getIssuedBooks("");
        returnList.setAll(results);
    }

    // ── Process Return ────────────────────────────────────────────────
    private void handleReturnBook(IssueRecord record) {
        double currentFine = issueService.calculateCurrentFine(
            record.getDueDate()
        );

        // Confirm with fine info
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Return Book");
        confirm.setHeaderText("Return: " + record.getBookTitle());

        String content =
            "Member : " + record.getMemberName() +
            " (" + record.getMemberId2() + ")\n" +
            "Issued : " + record.getIssueDate() + "\n" +
            "Due    : " + record.getDueDate() + "\n\n";

        if (currentFine > 0) {
            content += "⚠ OVERDUE — Fine: Rs. " + (int) currentFine +
                       "\n(Rs. " + (int) issueService.getFinePerDay() +
                       " × " + getDaysLate(record.getDueDate()) + " days)";
        } else {
            content += "✅ Returned on time — No fine.";
        }

        confirm.setContentText(content);

        ButtonType returnBtn = new ButtonType("✅ Confirm Return",
                                   ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel",
                                   ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(returnBtn, cancelBtn);

        confirm.showAndWait().ifPresent(result -> {
            if (result == returnBtn) {
                double fine = issueService.returnBook(record.getId());
                if (fine >= 0) {
                    loadIssuedBooks();
                    loadOverdueBooks();
                    updateStats();

                    // Refresh return table
                    String kw = returnSearchField.getText().trim();
                    returnList.setAll(issueService.getIssuedBooks(kw));

                    String msg = fine > 0
                        ? "Book returned.\nFine collected: Rs. " + (int) fine
                        : "Book returned successfully!\nNo fine.";
                    AlertHelper.showSuccess("Return Processed", msg);
                } else {
                    AlertHelper.showError("Error", "Return failed. Try again.");
                }
            }
        });
    }

    // ── Refresh Overdue ───────────────────────────────────────────────
    @FXML
    private void handleRefreshOverdue() {
        loadOverdueBooks();
        updateStats();
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private void loadIssuedBooks() {
        issuedList.setAll(issueService.getIssuedBooks(""));
    }

    private void loadOverdueBooks() {
        overdueList.setAll(issueService.getOverdueBooks());
    }

    private void updateStats() {
        totalIssuedLabel.setText(
            String.valueOf(issueService.getIssuedBooks("").size())
        );
        totalOverdueLabel.setText(
            String.valueOf(issueService.getOverdueBooks().size())
        );
    }

    private long getDaysLate(String dueDateStr) {
        try {
            LocalDate due = LocalDate.parse(dueDateStr);
            LocalDate now = LocalDate.now();
            return java.time.temporal.ChronoUnit.DAYS.between(due, now);
        } catch (Exception e) {
            return 0;
        }
    }
}
package com.library.controller;

import com.library.model.Book;
import com.library.service.BookService;
import com.library.util.AlertHelper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import com.library.util.AlertHelper;

public class BookController implements Initializable {

    @FXML private TableView<Book>           booksTable;
    @FXML private TableColumn<Book,Integer> colId;
    @FXML private TableColumn<Book,String>  colTitle;
    @FXML private TableColumn<Book,String>  colAuthor;
    @FXML private TableColumn<Book,String>  colIsbn;
    @FXML private TableColumn<Book,String>  colCategory;
    @FXML private TableColumn<Book,Integer> colTotal;
    @FXML private TableColumn<Book,Integer> colAvailable;
    @FXML private TableColumn<Book,Void>    colActions;
    @FXML private TextField                 searchField;
    @FXML private ComboBox<String>          categoryFilter;
    @FXML private Label                     statusLabel;

    private final BookService          bookService = new BookService();
    private final ObservableList<Book> bookList    = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
        setupCategoryFilter();
        loadBooks();
    }

    // ── Table Setup ───────────────────────────────────────────────────
    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colIsbn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalCopies"));
        colAvailable.setCellValueFactory(new PropertyValueFactory<>("availableCopies"));

        // Color available copies: red if 0, green if available
        colAvailable.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(value.toString());
                    setStyle(value == 0
                        ? "-fx-text-fill: #ef233c; -fx-font-weight: bold;"
                        : "-fx-text-fill: #2dc653; -fx-font-weight: bold;");
                }
            }
        });

        // Action buttons column
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("✏️ Edit");
            private final Button deleteBtn = new Button("🗑️");
            private final HBox   box       = new HBox(6, editBtn, deleteBtn);

            {
                editBtn.setStyle(
                    "-fx-background-color: #4361ee; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 4 10 4 10;");
                deleteBtn.setStyle(
                    "-fx-background-color: #ef233c; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 4 8 4 8;");

                editBtn.setOnAction(e -> {
                    Book book = getTableView().getItems().get(getIndex());
                    openBookForm(book);
                });
                deleteBtn.setOnAction(e -> {
                    Book book = getTableView().getItems().get(getIndex());
                    handleDeleteBook(book);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        booksTable.setItems(bookList);
    }

    private void setupCategoryFilter() {
        categoryFilter.setItems(FXCollections.observableArrayList(
            "All Categories", "Fiction", "Non-Fiction", "Science",
            "Technology", "History", "Mathematics", "Literature",
            "Reference", "Other"
        ));
        categoryFilter.setValue("All Categories");
    }

    // ── Load / Refresh Books ──────────────────────────────────────────
    private void loadBooks() {
        List<Book> books = bookService.getAllBooks();
        bookList.setAll(books);
        updateStatus(books.size());
    }

    private void updateStatus(int count) {
        statusLabel.setText("Total: " + count + " book(s)");
    }

    // ── Search ────────────────────────────────────────────────────────
    @FXML
    private void handleSearch() {
        String keyword = searchField.getText().trim();
        List<Book> results = bookService.searchBooks(keyword);
        bookList.setAll(results);
        updateStatus(results.size());
    }

    @FXML
    private void handleCategoryFilter() {
        String selected = categoryFilter.getValue();
        if (selected == null || selected.equals("All Categories")) {
            loadBooks();
        } else {
            List<Book> results = bookService.searchBooks(selected);
            bookList.setAll(results);
            updateStatus(results.size());
        }
    }

    @FXML
    private void handleClearSearch() {
        searchField.clear();
        categoryFilter.setValue("All Categories");
        loadBooks();
    }

    // ── Add Book ──────────────────────────────────────────────────────
    @FXML
    private void handleAddBook() {
        openBookForm(null);
    }

    // ── Delete Book ───────────────────────────────────────────────────
    private void handleDeleteBook(Book book) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Book");
        confirm.setHeaderText("Delete \"" + book.getTitle() + "\"?");
        confirm.setContentText(
            "This action cannot be undone.\n" +
            "Note: Books currently issued cannot be deleted."
        );

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean deleted = bookService.deleteBook(book.getId());
            if (deleted) {
                showAlert(Alert.AlertType.INFORMATION,
                    "Deleted", "Book deleted successfully.");
                loadBooks();
            } else {
                showAlert(Alert.AlertType.ERROR,
                    "Cannot Delete",
                    "This book is currently issued to a member.\n" +
                    "Please return the book first.");
            }
        }
    }

    // ── Book Form (Add / Edit) ────────────────────────────────────────
   private void openBookForm(Book existingBook) {
    boolean isEdit = existingBook != null;

    // ── Form Fields ───────────────────────────────────────────────────
    TextField        titleField     = new TextField();
    TextField        authorField    = new TextField();
    TextField        isbnField      = new TextField();
    ComboBox<String> catBox         = new ComboBox<>();
    Spinner<Integer> copiesSpinner  = new Spinner<>(1, 999, 1);
    Label            errorLabel     = new Label();

    // Field sizing
    titleField.setPrefWidth(280);
    titleField.setPrefHeight(36);
    titleField.setPromptText("e.g. Introduction to Algorithms");

    authorField.setPrefWidth(280);
    authorField.setPrefHeight(36);
    authorField.setPromptText("e.g. Thomas H. Cormen");

    isbnField.setPrefWidth(280);
    isbnField.setPrefHeight(36);
    isbnField.setPromptText("e.g. 978-0262033848");

    catBox.setPrefWidth(280);
    catBox.setPrefHeight(36);
    catBox.setPromptText("Select category");
    catBox.setItems(FXCollections.observableArrayList(
        "Fiction", "Non-Fiction", "Science", "Technology",
        "History", "Mathematics", "Literature", "Reference", "Other"
    ));

    copiesSpinner.setEditable(true);
    copiesSpinner.setPrefWidth(280);
    copiesSpinner.setPrefHeight(36);

    errorLabel.setStyle("-fx-text-fill: #ef233c; -fx-font-size: 12px;");

    // Pre-fill if editing
    if (isEdit) {
        titleField.setText(existingBook.getTitle());
        authorField.setText(existingBook.getAuthor());
        isbnField.setText(existingBook.getIsbn());
        catBox.setValue(existingBook.getCategory());
        copiesSpinner.getValueFactory().setValue(existingBook.getTotalCopies());
    }

    // ── Layout ────────────────────────────────────────────────────────
    String labelStyle =
        "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #333333;";
    String fieldContainerStyle =
        "-fx-spacing: 6;";

    VBox form = new VBox(14);
    form.setPadding(new Insets(24, 28, 10, 28));
    form.setPrefWidth(460);

    form.getChildren().addAll(
        fieldBox("Title *",    labelStyle, fieldContainerStyle, titleField),
        fieldBox("Author *",   labelStyle, fieldContainerStyle, authorField),
        fieldBox("ISBN",       labelStyle, fieldContainerStyle, isbnField),
        fieldBox("Category",   labelStyle, fieldContainerStyle, catBox),
        fieldBox("No. of Copies *", labelStyle, fieldContainerStyle, copiesSpinner),
        errorLabel
    );

    // ── Buttons ───────────────────────────────────────────────────────
    Button saveBtn   = new Button(isEdit ? "💾  Update Book" : "➕  Add Book");
    Button cancelBtn = new Button("Cancel");

    saveBtn.setPrefWidth(160);
    saveBtn.setPrefHeight(40);
    saveBtn.setStyle(
        "-fx-background-color: #4361ee; -fx-text-fill: white;" +
        "-fx-font-weight: bold; -fx-font-size: 14px;" +
        "-fx-background-radius: 6; -fx-cursor: hand;");

    cancelBtn.setPrefWidth(100);
    cancelBtn.setPrefHeight(40);
    cancelBtn.setStyle(
        "-fx-background-color: #e9ecef; -fx-font-size: 13px;" +
        "-fx-background-radius: 6; -fx-cursor: hand;");

    HBox btnBox = new HBox(12, saveBtn, cancelBtn);
    btnBox.setPadding(new Insets(10, 28, 24, 28));
    btnBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    VBox root = new VBox(0, form, btnBox);
    root.setStyle("-fx-background-color: white;");

    // ── Dialog Stage ──────────────────────────────────────────────────
    Stage dialog = new Stage();
    dialog.setTitle(isEdit ? "Edit Book" : "Add New Book");
    dialog.initModality(Modality.APPLICATION_MODAL);
    dialog.setScene(new Scene(root));
    dialog.setMinWidth(460);
    dialog.setResizable(false);

    cancelBtn.setOnAction(e -> dialog.close());

    saveBtn.setOnAction(e -> {
        String title  = titleField.getText().trim();
        String author = authorField.getText().trim();
        String isbn   = isbnField.getText().trim();
        String cat    = catBox.getValue();
        int    copies = copiesSpinner.getValue();

        if (title.isEmpty()) {
            errorLabel.setText("⚠ Title is required.");
            return;
        }
        if (author.isEmpty()) {
            errorLabel.setText("⚠ Author is required.");
            return;
        }
        if (!isbn.isEmpty() && bookService.isbnExists(isbn,
                isEdit ? existingBook.getId() : 0)) {
            errorLabel.setText("⚠ This ISBN already exists.");
            return;
        }

        Book book = new Book(
            isEdit ? existingBook.getId() : 0,
            title, author, isbn,
            cat != null ? cat : "Other",
            copies, copies
        );

        boolean success = isEdit
            ? bookService.updateBook(book)
            : bookService.addBook(book);

        if (success) {
            dialog.close();
            loadBooks();
            showAlert(Alert.AlertType.INFORMATION, "Success",
                isEdit ? "Book updated successfully!"
                       : "Book added successfully!");
        } else {
            errorLabel.setText("⚠ Failed to save. Please try again.");
        }
    });

    dialog.showAndWait();
}

// ── Helper: builds a label + field pair ──────────────────────────────
private VBox fieldBox(String labelText, String labelStyle,
                      String boxStyle, javafx.scene.Node field) {
    Label label = new Label(labelText);
    label.setStyle(labelStyle);
    VBox box = new VBox(6, label, field);
    return box;
}

private void showAlert(Alert.AlertType type, String title, String msg) {
    if (type == Alert.AlertType.INFORMATION) {
        AlertHelper.showSuccess(title, msg);
    } else {
        AlertHelper.showError(title, msg);
    }
}
}
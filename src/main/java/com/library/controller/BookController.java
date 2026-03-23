package com.library.controller;

import com.library.model.Book;
import com.library.service.BookService;
import com.library.util.AlertHelper;
import com.library.util.QRCodeUtil;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class BookController implements Initializable {

    @FXML private TableView<Book>           booksTable;
    @FXML private TableColumn<Book,Integer> colId;
    @FXML private TableColumn<Book,String>  colAccession;
    @FXML private TableColumn<Book,String>  colTitle;
    @FXML private TableColumn<Book,String>  colAuthor;
    @FXML private TableColumn<Book,String>  colClassification;
    @FXML private TableColumn<Book,String>  colCategory;
    @FXML private TableColumn<Book,Integer> colTotal;
    @FXML private TableColumn<Book,Integer> colAvailable;
    @FXML private TableColumn<Book,Void>    colActions;
    @FXML private TextField                 searchField;
    @FXML private ComboBox<String>          categoryFilter;
    @FXML private Label                     statusLabel;

    private final BookService          bookService = new BookService();
    private final ObservableList<Book> bookList    =
        FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
        setupCategoryFilter();
        loadBooks();
    }

    // ── Table Setup ───────────────────────────────────────────────────
    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colAccession.setCellValueFactory(
            new PropertyValueFactory<>("accessionNumber"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colClassification.setCellValueFactory(
            new PropertyValueFactory<>("classificationNumber"));
        colCategory.setCellValueFactory(
            new PropertyValueFactory<>("category"));
        colTotal.setCellValueFactory(
            new PropertyValueFactory<>("totalCopies"));

        // Available — color coded
        colAvailable.setCellValueFactory(
            new PropertyValueFactory<>("availableCopies"));
        colAvailable.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setText(null); return; }
                setText(value.toString());
                setStyle(value == 0
                    ? "-fx-text-fill: #e63946; -fx-font-weight: bold;"
                    : "-fx-text-fill: #2dc653; -fx-font-weight: bold;");
            }
        });

        // Actions — Edit + QR + Delete
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("✏️");
            private final Button qrBtn     = new Button("📱 QR");
            private final Button deleteBtn = new Button("🗑️");
            private final HBox   box = new HBox(5, editBtn, qrBtn, deleteBtn);

            {
                editBtn.setStyle(
                    "-fx-background-color: #4361ee; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 4 8 4 8;");
                qrBtn.setStyle(
                    "-fx-background-color: #7b2ff7; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 4 8 4 8;");
                deleteBtn.setStyle(
                    "-fx-background-color: #e63946; -fx-text-fill: white;" +
                    "-fx-font-size: 11px; -fx-background-radius: 4;" +
                    "-fx-cursor: hand; -fx-padding: 4 8 4 8;");

                editBtn.setOnAction(e -> {
                    Book b = getTableView().getItems().get(getIndex());
                    openBookForm(b);
                });
                qrBtn.setOnAction(e -> {
                    Book b = getTableView().getItems().get(getIndex());
                    showQRCode(b);
                });
                deleteBtn.setOnAction(e -> {
                    Book b = getTableView().getItems().get(getIndex());
                    handleDelete(b);
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

    // ── Load / Refresh ────────────────────────────────────────────────
    private void loadBooks() {
        List<Book> books = bookService.getAllBooks();
        bookList.setAll(books);
        statusLabel.setText("Total: " + books.size() + " book(s)");
    }

    @FXML
    private void handleSearch() {
        String keyword = searchField.getText().trim();
        List<Book> results = bookService.searchBooks(keyword);
        bookList.setAll(results);
        statusLabel.setText("Showing: " + results.size() + " book(s)");
    }

    @FXML
    private void handleCategoryFilter() {
        String selected = categoryFilter.getValue();
        if (selected == null || selected.equals("All Categories")) {
            loadBooks();
        } else {
            List<Book> results = bookService.searchBooks(selected);
            bookList.setAll(results);
            statusLabel.setText("Showing: " + results.size() + " book(s)");
        }
    }

    @FXML
    private void handleClearSearch() {
        searchField.clear();
        categoryFilter.setValue("All Categories");
        loadBooks();
    }

    @FXML
    private void handleAddBook() { openBookForm(null); }

    // ── Delete ────────────────────────────────────────────────────────
    private void handleDelete(Book book) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Book");
        confirm.setHeaderText("Delete \"" + book.getTitle() + "\"?");
        confirm.setContentText(
            "Accession: " + book.getAccessionNumber() + "\n" +
            "This cannot be undone.\n" +
            "Books currently issued cannot be deleted."
        );
        confirm.initOwner(Window.getWindows().stream()
            .filter(Window::isShowing).findFirst().orElse(null));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (bookService.deleteBook(book.getId())) {
                AlertHelper.showSuccess("Deleted",
                    "\"" + book.getTitle() + "\" removed.");
                loadBooks();
            } else {
                AlertHelper.showError("Cannot Delete",
                    "This book is currently issued.\n" +
                    "Return the book first.");
            }
        }
    }

    // ── QR Code Viewer ────────────────────────────────────────────────
    private void showQRCode(Book book) {
        String content = QRCodeUtil.buildQRContent(
            book.getAccessionNumber(),
            book.getTitle(),
            book.getAuthor(),
            book.getClassificationNumber(),
            book.getId()
        );

        Image qrImage = QRCodeUtil.generateQRImage(content);
        if (qrImage == null) {
            AlertHelper.showError("QR Error",
                "Failed to generate QR code.");
            return;
        }

        ImageView imageView = new ImageView(qrImage);
        imageView.setFitWidth(250);
        imageView.setFitHeight(250);
        imageView.setSmooth(true);

        // Book info
        Label titleLbl = new Label(book.getTitle());
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;" +
                          "-fx-text-fill: #1a1a2e;");
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(280);

        Label infoLbl = new Label(
            "Accession: " + book.getAccessionNumber() + "\n" +
            "Class No:  " + book.getClassificationNumber() + "\n" +
            "Cutter:    " + book.getCutterNumber()
        );
        infoLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #555555;");

        // Buttons
        Button saveBtn = new Button("💾  Save QR as PNG");
        saveBtn.setStyle(
            "-fx-background-color: #4361ee; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-padding: 8 20;" +
            "-fx-background-radius: 8; -fx-cursor: hand;");

        Button closeBtn = new Button("Close");
        closeBtn.setStyle(
            "-fx-background-color: #eef1fb; -fx-padding: 8 20;" +
            "-fx-background-radius: 8; -fx-cursor: hand;");

        HBox btnBox = new HBox(10, saveBtn, closeBtn);
        btnBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(14, imageView, titleLbl, infoLbl, btnBox);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: white;");
        root.setPrefWidth(320);

        Stage dialog = new Stage();
        dialog.setTitle("QR Code — " + book.getTitle());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(Window.getWindows().stream()
            .filter(Window::isShowing).findFirst().orElse(null));
        dialog.setScene(new Scene(root));
        dialog.setResizable(false);

        saveBtn.setOnAction(e -> {
            String path = QRCodeUtil.saveQRCode(
                content, book.getAccessionNumber()
            );
            if (path != null) {
                AlertHelper.showSuccess("QR Saved!",
                    "Saved to:\n" + path);
            } else {
                AlertHelper.showError("Save Failed",
                    "Could not save QR code.");
            }
        });

        closeBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    // ── Book Form (Add / Edit) ────────────────────────────────────────
    private void openBookForm(Book existing) {
        boolean isEdit = existing != null;

        // ── Fields ───────────────────────────────────────────────────
        TextField accessionField      = field("e.g. TBC-2024-001");
        TextField titleField          = field("e.g. Introduction to Algorithms");
        TextField authorField         = field("e.g. Thomas H. Cormen");
        TextField isbnField           = field("e.g. 978-0262033848");
        TextField classificationField = field("e.g. 005.1 COR");
        TextField cutterField         = field("e.g. C813i");
        TextField editionField        = field("e.g. 3rd Edition");
        TextField publisherField      = field("e.g. MIT Press");
        TextField placeField          = field("e.g. Cambridge, MA");
        TextField yearField           = field("e.g. " + LocalDate.now().getYear());
        TextField pagesField          = field("e.g. 1292");
        ComboBox<String> catBox       = new ComboBox<>();
        Spinner<Integer> copiesSpinner = new Spinner<>(1, 999, 1);
        Label errorLabel              = new Label();

        catBox.setItems(FXCollections.observableArrayList(
            "Fiction", "Non-Fiction", "Science", "Technology",
            "History", "Mathematics", "Literature", "Reference", "Other"
        ));
        catBox.setPromptText("Select category");
        catBox.setPrefWidth(Double.MAX_VALUE);
        catBox.setPrefHeight(36);

        copiesSpinner.setEditable(true);
        copiesSpinner.setPrefWidth(Double.MAX_VALUE);
        copiesSpinner.setPrefHeight(36);

        errorLabel.setStyle(
            "-fx-text-fill: #e63946; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);

        // Pre-fill if editing
        if (isEdit) {
            accessionField.setText(existing.getAccessionNumber());
            titleField.setText(existing.getTitle());
            authorField.setText(existing.getAuthor());
            isbnField.setText(existing.getIsbn());
            classificationField.setText(existing.getClassificationNumber());
            cutterField.setText(existing.getCutterNumber());
            editionField.setText(existing.getEdition());
            publisherField.setText(existing.getPublisher());
            placeField.setText(existing.getPlaceOfPublication());
            if (existing.getYearOfPublication() > 0)
                yearField.setText(String.valueOf(existing.getYearOfPublication()));
            if (existing.getNumberOfPages() > 0)
                pagesField.setText(String.valueOf(existing.getNumberOfPages()));
            catBox.setValue(existing.getCategory());
            copiesSpinner.getValueFactory().setValue(existing.getTotalCopies());
        }

        String lblStyle =
            "-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #333;";

        // ── Two-column layout ─────────────────────────────────────────
        // Left column
        VBox leftCol = new VBox(10,
            lbl("Accession No. *", lblStyle),  accessionField,
            lbl("Title *",         lblStyle),  titleField,
            lbl("Author *",        lblStyle),  authorField,
            lbl("ISBN",            lblStyle),  isbnField,
            lbl("Category",        lblStyle),  catBox,
            lbl("No. of Copies *", lblStyle),  copiesSpinner
        );
        leftCol.setPrefWidth(260);

        // Right column
        VBox rightCol = new VBox(10,
            lbl("Classification No.", lblStyle), classificationField,
            lbl("Cutter Number",      lblStyle), cutterField,
            lbl("Edition",            lblStyle), editionField,
            lbl("Publisher",          lblStyle), publisherField,
            lbl("Place of Publication", lblStyle), placeField,
            lbl("Year of Publication", lblStyle), yearField,
            lbl("Number of Pages",    lblStyle), pagesField
        );
        rightCol.setPrefWidth(260);

        HBox columns = new HBox(20, leftCol, rightCol);
        columns.setPadding(new Insets(20, 24, 10, 24));

        // Section headers
        Label leftHeader = sectionHeader("📚 Basic Information");
        Label rightHeader = sectionHeader("🔖 Classification & Publication");

        HBox headers = new HBox(20,
            withWidth(leftHeader, 260),
            withWidth(rightHeader, 260)
        );
        headers.setPadding(new Insets(20, 24, 0, 24));

        // ── Buttons ───────────────────────────────────────────────────
        Button saveBtn   = new Button(isEdit ? "💾  Update Book"
                                             : "➕  Add Book");
        Button cancelBtn = new Button("Cancel");

        saveBtn.setPrefHeight(42); saveBtn.setPrefWidth(160);
        saveBtn.setStyle(
            "-fx-background-color: #4361ee; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 14px;" +
            "-fx-background-radius: 8; -fx-cursor: hand;");

        cancelBtn.setPrefHeight(42); cancelBtn.setPrefWidth(100);
        cancelBtn.setStyle(
            "-fx-background-color: #eef1fb; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand;");

        HBox btnBox = new HBox(12, saveBtn, cancelBtn);
        btnBox.setPadding(new Insets(10, 24, 20, 24));
        btnBox.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(0, headers, columns,
            withPadding(errorLabel, new Insets(0, 24, 0, 24)), btnBox);
        root.setStyle("-fx-background-color: white;");

        // ── Dialog ────────────────────────────────────────────────────
        Stage dialog = new Stage();
        dialog.setTitle(isEdit ? "Edit Book" : "Add New Book");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(Window.getWindows().stream()
            .filter(Window::isShowing).findFirst().orElse(null));
        dialog.setScene(new Scene(root));
        dialog.setMinWidth(580);
        dialog.setResizable(false);

        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            errorLabel.setText("");

            // ── Collect values ────────────────────────────────────────
            String accession = accessionField.getText().trim();
            String title     = titleField.getText().trim();
            String author    = authorField.getText().trim();
            String isbn      = isbnField.getText().trim();
            String classNo   = classificationField.getText().trim();
            String cutter    = cutterField.getText().trim();
            String edition   = editionField.getText().trim();
            String publisher = publisherField.getText().trim();
            String place     = placeField.getText().trim();
            String yearStr   = yearField.getText().trim();
            String pagesStr  = pagesField.getText().trim();
            String category  = catBox.getValue();
            int    copies    = copiesSpinner.getValue();

            // ── Validation ────────────────────────────────────────────
            if (accession.isEmpty()) {
                errorLabel.setText("⚠ Accession Number is required.");
                return;
            }
            if (title.isEmpty()) {
                errorLabel.setText("⚠ Title is required.");
                return;
            }
            if (author.isEmpty()) {
                errorLabel.setText("⚠ Author is required.");
                return;
            }
            if (bookService.accessionExists(accession,
                    isEdit ? existing.getId() : 0)) {
                errorLabel.setText(
                    "⚠ Accession Number already exists. Must be unique.");
                return;
            }
            if (!isbn.isEmpty() && bookService.isbnExists(isbn,
                    isEdit ? existing.getId() : 0)) {
                errorLabel.setText("⚠ This ISBN already exists.");
                return;
            }

            // Year validation
            int year = 0;
            if (!yearStr.isEmpty()) {
                try {
                    year = Integer.parseInt(yearStr);
                    int currentYear = LocalDate.now().getYear();
                    if (year < 1000 || year > currentYear + 1) {
                        errorLabel.setText(
                            "⚠ Enter a valid year (1000–" +
                            (currentYear + 1) + ").");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    errorLabel.setText("⚠ Year must be a number.");
                    return;
                }
            }

            // Pages validation
            int pages = 0;
            if (!pagesStr.isEmpty()) {
                try {
                    pages = Integer.parseInt(pagesStr);
                    if (pages <= 0) {
                        errorLabel.setText("⚠ Pages must be greater than 0.");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    errorLabel.setText("⚠ Pages must be a number.");
                    return;
                }
            }

            Book book = new Book(
                isEdit ? existing.getId() : 0,
                title, author, isbn,
                category != null ? category : "Other",
                copies, copies,
                accession, classNo, cutter, edition,
                publisher, place, year, pages
            );

            boolean success = isEdit
                ? bookService.updateBook(book)
                : bookService.addBook(book);

            if (success) {
                dialog.close();
                loadBooks();
                AlertHelper.showSuccess("Success",
                    isEdit ? "Book updated successfully!"
                           : "\"" + title + "\" added!");
            } else {
                errorLabel.setText("⚠ Failed to save. Try again.");
            }
        });

        dialog.showAndWait();
    }

    // ── UI Helpers ────────────────────────────────────────────────────
    private TextField field(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefHeight(36);
        tf.setPrefWidth(Double.MAX_VALUE);
        return tf;
    }

    private Label lbl(String text, String style) {
        Label l = new Label(text);
        l.setStyle(style);
        return l;
    }

    private Label sectionHeader(String text) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-font-size: 13px; -fx-font-weight: bold;" +
            "-fx-text-fill: #4361ee; -fx-padding: 0 0 4 0;");
        return l;
    }

    private Region withWidth(javafx.scene.Node node, double width) {
        if (node instanceof Region r) r.setPrefWidth(width);
        return (Region) node;
    }

    private Label withPadding(Label label, Insets insets) {
        label.setPadding(insets);
        return label;
    }
}
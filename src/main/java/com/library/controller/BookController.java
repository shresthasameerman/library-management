package com.library.controller;

import com.library.model.Book;
import com.library.model.BookCopyDetail;
import com.library.service.BookCopyService;
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
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class BookController implements Initializable {
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("0.00");

    @FXML private TableView<Book>           booksTable;
    @FXML private TableColumn<Book,Integer> colId;
    @FXML private TableColumn<Book,String>  colTitle;
    @FXML private TableColumn<Book,String>  colAuthor;
    @FXML private TableColumn<Book,String>  colClassification;
    @FXML private TableColumn<Book,Integer> colTotal;
    @FXML private TableColumn<Book,Integer> colAvailable;
    @FXML private TableColumn<Book,Void>    colActions;
    @FXML private TextField                 searchField;
    @FXML private ComboBox<String>          categoryFilter;
    @FXML private Label                     statusLabel;

    private final BookService          bookService = new BookService();
    private final BookCopyService      copyService = new BookCopyService();
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
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colTitle.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Hyperlink link = new Hyperlink(value);
                link.setStyle("-fx-text-fill: #4361ee; -fx-font-weight: bold;");
                link.setOnAction(e -> {
                    Book b = getTableView().getItems().get(getIndex());
                    showBookDetails(b);
                });
                setGraphic(link);
                setText(null);
            }
        });
        colAuthor.setCellValueFactory(new PropertyValueFactory<>("author"));
        colClassification.setCellValueFactory(
            new PropertyValueFactory<>("classificationNumber"));
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

    private void showBookDetails(Book book) {
        Label titleLabel = new Label(book.getTitle());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1a1a2e;");

        Label authorChip = new Label("Author: " + safe(book.getAuthor()));
        authorChip.setStyle(
            "-fx-background-color: #eef2ff; -fx-text-fill: #334155;" +
            "-fx-padding: 6 10; -fx-background-radius: 8; -fx-font-size: 12px;"
        );

        Label classChip = new Label(
            "Class No: " + safe(book.getClassificationNumber())
        );
        classChip.setStyle(
            "-fx-background-color: #eef2ff; -fx-text-fill: #334155;" +
            "-fx-padding: 6 10; -fx-background-radius: 8; -fx-font-size: 12px;"
        );

        Label totalChip = new Label("Total: " + book.getTotalCopies());
        totalChip.setStyle(
            "-fx-background-color: #eef2ff; -fx-text-fill: #334155;" +
            "-fx-padding: 6 10; -fx-background-radius: 8; -fx-font-size: 12px;"
        );

        Label availableChip = new Label("Available: " + book.getAvailableCopies());
        availableChip.setStyle(
            "-fx-background-color: #e8f9ef; -fx-text-fill: #1f7a3d;" +
            "-fx-padding: 6 10; -fx-background-radius: 8;" +
            "-fx-font-size: 12px; -fx-font-weight: bold;"
        );

        Label priceChip = new Label("Price: " + formatPrice(book.getPrice()));
        priceChip.setStyle(
            "-fx-background-color: #fff7e6; -fx-text-fill: #8a4b00;" +
            "-fx-padding: 6 10; -fx-background-radius: 8;" +
            "-fx-font-size: 12px; -fx-font-weight: bold;"
        );

        FlowPane infoRow = new FlowPane(10, 8,
            authorChip, classChip, totalChip, availableChip, priceChip
        );
        infoRow.setPrefWrapLength(700);

        TableView<BookCopyDetail> copyTable = new TableView<>();
        copyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<BookCopyDetail, String> accCol = new TableColumn<>("Accession No.");
        accCol.setCellValueFactory(new PropertyValueFactory<>("accessionNumber"));

        TableColumn<BookCopyDetail, String> spineCol = new TableColumn<>("Spine Level");
        spineCol.setCellValueFactory(new PropertyValueFactory<>("spineLevel"));

        TableColumn<BookCopyDetail, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(value);
                if ("ISSUED".equals(value)) {
                    setStyle("-fx-text-fill: #e63946; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #2dc653; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<BookCopyDetail, String> issuedToCol = new TableColumn<>("Issued To");
        issuedToCol.setCellValueFactory(new PropertyValueFactory<>("issuedTo"));

        TableColumn<BookCopyDetail, String> dueDateCol = new TableColumn<>("Return Date");
        dueDateCol.setCellValueFactory(new PropertyValueFactory<>("dueDate"));

        copyTable.getColumns().addAll(accCol, spineCol, statusCol, issuedToCol, dueDateCol);
        copyTable.setItems(FXCollections.observableArrayList(
            copyService.getCopyDetailsForBook(book.getId())
        ));

        Label sheetTitle = new Label("Copy Availability Sheet");
        sheetTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4361ee;");

        Button closeBtn = new Button("Close");
        closeBtn.setStyle(
            "-fx-background-color: #eef1fb; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand;"
        );

        VBox root = new VBox(12, titleLabel, infoRow, sheetTitle, copyTable, closeBtn);
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: white;");

        Stage dialog = new Stage();
        dialog.setTitle("Book Details");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(Window.getWindows().stream()
            .filter(Window::isShowing).findFirst().orElse(null));
        dialog.setScene(new Scene(root, 760, 520));
        dialog.setMinWidth(720);
        dialog.setMinHeight(480);

        closeBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
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
        List<com.library.model.BookCopy> copies = copyService.getCopiesForBook(book.getId());
        String selectedAccession;
        String selectedStatus;
        String selectedSpineLevel;

        if (!copies.isEmpty()) {
            ChoiceDialog<com.library.model.BookCopy> chooseCopy =
                new ChoiceDialog<>(copies.get(0), copies);
            chooseCopy.setTitle("Select Accession QR");
            chooseCopy.setHeaderText("Generate QR for a specific accession copy");
            chooseCopy.setContentText("Accession:");
            chooseCopy.initOwner(Window.getWindows().stream()
                .filter(Window::isShowing).findFirst().orElse(null));

            Optional<com.library.model.BookCopy> chosen = chooseCopy.showAndWait();
            if (chosen.isEmpty()) {
                return;
            }

            selectedAccession = safe(chosen.get().getAccessionNumber());
            selectedStatus = safe(chosen.get().getStatus());
            selectedSpineLevel = safe(chosen.get().getSpineLevel());
        } else {
            // Backward-compatible fallback for older rows with no copy records.
            selectedAccession = safe(book.getAccessionNumber());
            selectedStatus = book.getAvailableCopies() > 0 ? "AVAILABLE" : "ISSUED";
            selectedSpineLevel = buildSpineLevel(
                book.getClassificationNumber(),
                book.getCutterNumber(),
                String.valueOf(book.getYearOfPublication())
            );
        }

        String content = String.format(
            "THE BRITISH COLLEGE LIBRARY\n" +
            "Title: %s\n" +
            "Author: %s\n" +
            "Accession: %s\n" +
            "Copy Status: %s\n" +
            "Spine Level: %s\n" +
            "Class No: %s\n" +
            "Book Number: %s\n" +
            "Edition: %s\n" +
            "Publisher: %s\n" +
            "Category: %s",
            safe(book.getTitle()),
            safe(book.getAuthor()),
            selectedAccession,
            selectedStatus,
            selectedSpineLevel,
            safe(book.getClassificationNumber()),
            safe(book.getCutterNumber()),
            safe(book.getEdition()),
            safe(book.getPublisher()),
            safe(book.getCategory())
        );

        Image qrImage = QRCodeUtil.generateQRImage(content);
        if (qrImage == null) {
            AlertHelper.showError("QR Error", "Failed to generate QR code.");
            return;
        }

        ImageView imageView = new ImageView(qrImage);
        imageView.setFitWidth(250);
        imageView.setFitHeight(250);
        imageView.setSmooth(true);

        Label titleLbl = new Label(book.getTitle());
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;" +
                          "-fx-text-fill: #1a1a2e;");
        titleLbl.setWrapText(true);
        titleLbl.setMaxWidth(280);

        Label infoLbl = new Label(
            "Accession : " + selectedAccession + "\n" +
            "Status    : " + selectedStatus + "\n" +
            "Spine     : " + selectedSpineLevel + "\n" +
            "Class No  : " + book.getClassificationNumber() + "\n" +
            "Book No   : " + book.getCutterNumber() + "\n" +
            "Edition   : " + book.getEdition() + "\n" +
            "Publisher : " + book.getPublisher()
        );
        infoLbl.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: #333333;" +
            "-fx-font-family: monospace;");

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
                content, selectedAccession);
            if (path != null) {
                AlertHelper.showSuccess("QR Saved!", "Saved to:\n" + path);
            } else {
                AlertHelper.showError("Save Failed", "Could not save QR.");
            }
        });

        closeBtn.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    // ── Book Form (Add / Edit) ────────────────────────────────────────
    private void openBookForm(Book existing) {
        boolean isEdit = existing != null;
        BookCopyService copyService = new BookCopyService();

        // ── Fields ───────────────────────────────────────────────────
        TextField titleField          = field("e.g. Introduction to Algorithms");
        TextField authorField         = field("e.g. Thomas H. Cormen");
        TextField isbnField           = field("e.g. 978-0262033848");
        TextField classificationField = field("e.g. 005.1 COR");
        TextField cutterField         = field("e.g. B813");
        TextField editionField        = field("e.g. 3rd Edition");
        TextField publisherField      = field("e.g. MIT Press");
        TextField placeField          = field("e.g. Cambridge, MA");
        TextField yearField           = field("e.g. " + LocalDate.now().getYear());
        TextField pagesField          = field("e.g. 1292");
        TextField priceField          = field("e.g. 699.00");
        ComboBox<String> catBox       = new ComboBox<>();
        Label errorLabel              = new Label();

        catBox.setItems(FXCollections.observableArrayList(
            "Fiction", "Non-Fiction", "Science", "Technology",
            "History", "Mathematics", "Literature", "Reference", "Other"
        ));
        catBox.setPromptText("Select category");
        catBox.setPrefWidth(Double.MAX_VALUE);
        catBox.setPrefHeight(36);

        errorLabel.setStyle(
            "-fx-text-fill: #e63946; -fx-font-size: 12px;");
        errorLabel.setWrapText(true);

        // ── Accession Number List ─────────────────────────────────────
        // Each copy gets its own accession number entered in a list
        ObservableList<CopyEntry> accessionList =
            FXCollections.observableArrayList();

        // Pre-load existing copies if editing
        if (isEdit) {
            copyService.getCopiesForBook(existing.getId())
                .forEach(c -> accessionList.add(new CopyEntry(
                    c.getAccessionNumber(),
                    c.getStatus(),
                    c.getSpineLevel(),
                    true
                )));
        }

        ListView<CopyEntry> accessionListView = new ListView<>(accessionList);
        accessionListView.setPrefHeight(120);
        accessionListView.setStyle(
            "-fx-background-color: #f8f9fa;" +
            "-fx-border-color: #e2e8f0; -fx-border-radius: 6;");
        accessionListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CopyEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String spine = item.spineLevel == null || item.spineLevel.isBlank()
                    ? "-"
                    : item.spineLevel;
                setText(item.accessionNumber + " [" + item.status + "]  •  Spine: " + spine);
            }
        });

        TextField newAccessionField = field("Enter starting accession number");
        newAccessionField.setPrefWidth(200);

        Spinner<Integer> copyCountSpinner = new Spinner<>(1, 999, 1);
        copyCountSpinner.setEditable(true);
        copyCountSpinner.setPrefHeight(36);
        copyCountSpinner.setPrefWidth(120);

        Button addAccBtn = new Button("⚡ Generate");
        addAccBtn.setStyle(
            "-fx-background-color: #4361ee; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-background-radius: 6;" +
            "-fx-cursor: hand; -fx-padding: 6 14 6 14;");

        Button removeAccBtn = new Button("🗑 Remove");
        removeAccBtn.setStyle(
            "-fx-background-color: #e63946; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-background-radius: 6;" +
            "-fx-cursor: hand; -fx-padding: 6 14 6 14;");

        Label accStatusLabel = new Label();
        accStatusLabel.setStyle(
            "-fx-font-size: 11px; -fx-text-fill: #a0aec0;");

        // Add accession number to list
        addAccBtn.setOnAction(e -> {
            String startAcc = newAccessionField.getText().trim();
            int count = copyCountSpinner.getValue();

            if (startAcc.isEmpty()) {
                accStatusLabel.setText("⚠ Enter a starting accession number.");
                accStatusLabel.setStyle(
                    "-fx-font-size: 11px; -fx-text-fill: #e63946;");
                return;
            }

            List<String> generated = new ArrayList<>();
            String numericPart = startAcc.replaceAll("[^0-9]", "");
            String prefix = startAcc.replaceAll("[0-9]", "");

            if (count > 1 && numericPart.isEmpty()) {
                accStatusLabel.setText(
                    "⚠ Starting accession must contain a number for multiple copies.");
                accStatusLabel.setStyle(
                    "-fx-font-size: 11px; -fx-text-fill: #e63946;");
                return;
            }

            if (numericPart.isEmpty()) {
                generated.add(startAcc);
            } else {
                int startNum;
                try {
                    startNum = Integer.parseInt(numericPart);
                } catch (NumberFormatException ex) {
                    accStatusLabel.setText("⚠ Invalid accession number.");
                    accStatusLabel.setStyle(
                        "-fx-font-size: 11px; -fx-text-fill: #e63946;");
                    return;
                }

                int width = numericPart.length();
                for (int i = 0; i < count; i++) {
                    int n = startNum + i;
                    generated.add(prefix + String.format("%0" + width + "d", n));
                }
            }

            // Check duplicates in list and DB before adding anything
            for (String acc : generated) {
                boolean dupInList = accessionList.stream()
                    .anyMatch(item -> item.accessionNumber.equals(acc));
                if (dupInList) {
                    accStatusLabel.setText("⚠ " + acc + " already in list.");
                    accStatusLabel.setStyle(
                        "-fx-font-size: 11px; -fx-text-fill: #e63946;");
                    return;
                }

                if (copyService.accessionExists(acc, 0)) {
                    accStatusLabel.setText("⚠ " + acc + " already exists in DB.");
                    accStatusLabel.setStyle(
                        "-fx-font-size: 11px; -fx-text-fill: #e63946;");
                    return;
                }
            }

            String spineLevel = buildSpineLevel(
                classificationField.getText().trim(),
                cutterField.getText().trim(),
                yearField.getText().trim()
            );
            for (String acc : generated) {
                accessionList.add(new CopyEntry(acc, "AVAILABLE", spineLevel, false));
            }
            newAccessionField.clear();
            accStatusLabel.setText(
                "✓ Added " + generated.size() + " copies (Spine: " + spineLevel + "). Total: " +
                accessionList.size() + " copies.");
            accStatusLabel.setStyle(
                "-fx-font-size: 11px; -fx-text-fill: #2dc653;");
        });

        // Allow pressing Enter to add
        newAccessionField.setOnAction(e -> addAccBtn.fire());

        // Remove selected
        removeAccBtn.setOnAction(e -> {
            CopyEntry selected = accessionListView.getSelectionModel()
                .getSelectedItem();
            if (selected != null) {
                // Don't allow removing ISSUED copies
                if ("ISSUED".equals(selected.status)) {
                    accStatusLabel.setText("⚠ Cannot remove - copy is issued.");
                    accStatusLabel.setStyle(
                        "-fx-font-size: 11px; -fx-text-fill: #e63946;");
                    return;
                }
                accessionList.remove(selected);
                accStatusLabel.setText(
                    "Total: " + accessionList.size() + " copies.");
                accStatusLabel.setStyle(
                    "-fx-font-size: 11px; -fx-text-fill: #a0aec0;");
            }
        });

        Label startAccLabel = new Label("Starting Accession Number *");
        startAccLabel.setStyle(
            "-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #4a5568;");

        VBox startAccBox = new VBox(4, startAccLabel, newAccessionField);

        Label copyCountLabel = new Label("Number of Copies *");
        copyCountLabel.setStyle(
            "-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #4a5568;");

        VBox copyCountBox = new VBox(4, copyCountLabel, copyCountSpinner);

        HBox actionRow = new HBox(8, addAccBtn, removeAccBtn);

        VBox accessionBox = new VBox(6,
            new Label("Accession Numbers *") {{
                setStyle("-fx-font-weight: bold; -fx-font-size: 12px;" +
                         "-fx-text-fill: #333;");
            }},
            accessionListView,
            startAccBox,
            copyCountBox,
            actionRow,
            accStatusLabel
        );

        // Pre-fill if editing
        if (isEdit) {
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
            if (existing.getPrice() > 0)
                priceField.setText(formatPrice(existing.getPrice()));
            catBox.setValue(existing.getCategory());
        }

        String lblStyle =
            "-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #333;";

        // ── Left column ───────────────────────────────────────────────
        VBox leftCol = new VBox(10,
            accessionBox,
            lbl("Title *",   lblStyle), titleField,
            lbl("Author *",  lblStyle), authorField,
            lbl("ISBN",      lblStyle), isbnField,
            lbl("Category",  lblStyle), catBox
        );
        leftCol.setPrefWidth(280);

        // ── Right column ──────────────────────────────────────────────
        VBox rightCol = new VBox(10,
            lbl("Classification No.",   lblStyle), classificationField,
            lbl("Book Number",          lblStyle), cutterField,
            lbl("Edition",              lblStyle), editionField,
            lbl("Publisher",            lblStyle), publisherField,
            lbl("Place of Publication", lblStyle), placeField,
            lbl("Year of Publication",  lblStyle), yearField,
            lbl("Number of Pages",      lblStyle), pagesField,
            lbl("Price",                lblStyle), priceField
        );
        rightCol.setPrefWidth(260);

        HBox columns = new HBox(20, leftCol, rightCol);
        columns.setPadding(new Insets(20, 24, 10, 24));

        // ── Section headers ───────────────────────────────────────────
        HBox headers = new HBox(20,
            withWidth(sectionHeader("📚 Basic Information"),  280),
            withWidth(sectionHeader("🔖 Classification & Publication"), 260)
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

        Stage dialog = new Stage();
        dialog.setTitle(isEdit ? "Edit Book" : "Add New Book");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(Window.getWindows().stream()
            .filter(Window::isShowing).findFirst().orElse(null));
        dialog.setScene(new Scene(root));
        dialog.setMinWidth(920);
        dialog.setMinHeight(700);
        dialog.setResizable(true);

        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            errorLabel.setText("");

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
            String priceStr  = priceField.getText().trim();
            String category  = catBox.getValue();

            // Collect NEW accession numbers (not already in DB)
            List<String> newAccessions = new ArrayList<>();
            List<String> newSpineLevels = new ArrayList<>();
            String autoSpineLevel = buildSpineLevel(classNo, cutter, yearStr);
            for (CopyEntry item : accessionList) {
                if (!item.existing) {
                    newAccessions.add(item.accessionNumber.trim());
                    newSpineLevels.add(autoSpineLevel);
                }
            }

            // Validation
            if (title.isEmpty()) {
                errorLabel.setText("⚠ Title is required."); return;
            }
            if (author.isEmpty()) {
                errorLabel.setText("⚠ Author is required."); return;
            }
            if (!isEdit && accessionList.isEmpty()) {
                errorLabel.setText(
                    "⚠ Add at least one accession number.");
                return;
            }

            // Year validation
            int year = 0;
            if (!yearStr.isEmpty()) {
                try {
                    year = Integer.parseInt(yearStr);
                    int currentYear = LocalDate.now().getYear();
                    if (year < 1000 || year > currentYear + 1) {
                        errorLabel.setText("⚠ Enter a valid year.");
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
                        errorLabel.setText("⚠ Pages must be > 0.");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    errorLabel.setText("⚠ Pages must be a number.");
                    return;
                }
            }

            double price = 0.0;
            if (!priceStr.isEmpty()) {
                try {
                    price = Double.parseDouble(priceStr);
                    if (price < 0) {
                        errorLabel.setText("⚠ Price cannot be negative.");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    errorLabel.setText("⚠ Price must be a valid number.");
                    return;
                }
            }

            if (!isbn.isEmpty() && bookService.isbnExists(isbn,
                    isEdit ? existing.getId() : 0)) {
                errorLabel.setText("⚠ This ISBN already exists.");
                return;
            }

            // Use first accession as book's main accession
            String mainAccession = accessionList.isEmpty() ? ""
                : accessionList.get(0).accessionNumber;

            Book book = new Book(
                isEdit ? existing.getId() : 0,
                title, author, isbn,
                category != null ? category : "Other",
                accessionList.size(),
                accessionList.size(),
                mainAccession, classNo, cutter, edition,
                publisher, place, year, pages, price
            );

            boolean success = isEdit
                ? bookService.updateBook(book)
                : bookService.addBook(book);

            if (success) {
                int bookId = isEdit ? existing.getId()
                    : bookService.getLastInsertedId();

                // Save new copies to book_copies table
                if (!newAccessions.isEmpty()) {
                    boolean copiesSaved = copyService.addCopies(
                        bookId,
                        newAccessions,
                        newSpineLevels
                    );
                    if (!copiesSaved) {
                        if (!isEdit) {
                            // Avoid leaving a branch book row without its copy-sheet data.
                            bookService.deleteBook(bookId);
                        }
                        errorLabel.setText("⚠ " + copyService.getLastErrorMessage());
                        return;
                    }
                }

                dialog.close();
                loadBooks();
                AlertHelper.showSuccess("Success",
                    isEdit ? "Book updated!"
                           : "\"" + title + "\" added with " +
                             accessionList.size() + " copies!");
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

    private String buildSpineLevel(String classificationNumber,
                                   String bookNumber,
                                   String publishedYear) {
        String cls = normalizeSpinePart(classificationNumber, "NA");
        String book = normalizeSpinePart(bookNumber, "NA");
        String year = normalizeSpinePart(publishedYear, "NA");
        return cls + "-" + book + "-" + year;
    }

    private String normalizeSpinePart(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.trim().replaceAll("\\s+", "").toUpperCase();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private String formatPrice(double price) {
        return PRICE_FORMAT.format(price);
    }

    private static final class CopyEntry {
        private final String accessionNumber;
        private final String status;
        private final String spineLevel;
        private final boolean existing;

        private CopyEntry(String accessionNumber, String status,
                          String spineLevel, boolean existing) {
            this.accessionNumber = accessionNumber;
            this.status = status;
            this.spineLevel = spineLevel;
            this.existing = existing;
        }
    }
}
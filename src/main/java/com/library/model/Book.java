package com.library.model;

import javafx.beans.property.*;

public class Book {

    private final IntegerProperty id;
    private final StringProperty  title;
    private final StringProperty  author;
    private final StringProperty  isbn;
    private final StringProperty  category;
    private final IntegerProperty totalCopies;
    private final IntegerProperty availableCopies;

    // ── New fields ────────────────────────────────────────────────────
    private final StringProperty  accessionNumber;
    private final StringProperty  classificationNumber;
    private final StringProperty  cutterNumber;
    private final StringProperty  edition;
    private final StringProperty  publisher;
    private final StringProperty  placeOfPublication;
    private final IntegerProperty yearOfPublication;
    private final IntegerProperty numberOfPages;

    public Book(int id, String title, String author, String isbn,
                String category, int totalCopies, int availableCopies,
                String accessionNumber, String classificationNumber,
                String cutterNumber, String edition, String publisher,
                String placeOfPublication, int yearOfPublication,
                int numberOfPages) {
        this.id                   = new SimpleIntegerProperty(id);
        this.title                = new SimpleStringProperty(title);
        this.author               = new SimpleStringProperty(author);
        this.isbn                 = new SimpleStringProperty(isbn);
        this.category             = new SimpleStringProperty(category);
        this.totalCopies          = new SimpleIntegerProperty(totalCopies);
        this.availableCopies      = new SimpleIntegerProperty(availableCopies);
        this.accessionNumber      = new SimpleStringProperty(accessionNumber);
        this.classificationNumber = new SimpleStringProperty(classificationNumber);
        this.cutterNumber         = new SimpleStringProperty(cutterNumber);
        this.edition              = new SimpleStringProperty(edition);
        this.publisher            = new SimpleStringProperty(publisher);
        this.placeOfPublication   = new SimpleStringProperty(placeOfPublication);
        this.yearOfPublication    = new SimpleIntegerProperty(yearOfPublication);
        this.numberOfPages        = new SimpleIntegerProperty(numberOfPages);
    }

    // ── Convenience constructor (minimal — for backward compat) ───────
    public Book(int id, String title, String author, String isbn,
                String category, int totalCopies, int availableCopies) {
        this(id, title, author, isbn, category,
             totalCopies, availableCopies,
             "", "", "", "", "", "", 0, 0);
    }

    // ── Getters / Setters ─────────────────────────────────────────────
    public int    getId()             { return id.get(); }
    public IntegerProperty idProperty() { return id; }

    public String getTitle()               { return title.get(); }
    public void   setTitle(String v)       { title.set(v); }
    public StringProperty titleProperty()  { return title; }

    public String getAuthor()              { return author.get(); }
    public void   setAuthor(String v)      { author.set(v); }
    public StringProperty authorProperty() { return author; }

    public String getIsbn()               { return isbn.get(); }
    public void   setIsbn(String v)       { isbn.set(v); }
    public StringProperty isbnProperty()  { return isbn; }

    public String getCategory()              { return category.get(); }
    public void   setCategory(String v)      { category.set(v); }
    public StringProperty categoryProperty() { return category; }

    public int getTotalCopies()                  { return totalCopies.get(); }
    public void setTotalCopies(int v)            { totalCopies.set(v); }
    public IntegerProperty totalCopiesProperty() { return totalCopies; }

    public int getAvailableCopies()                   { return availableCopies.get(); }
    public void setAvailableCopies(int v)             { availableCopies.set(v); }
    public IntegerProperty availableCopiesProperty()  { return availableCopies; }

    public String getAccessionNumber()              { return accessionNumber.get(); }
    public void   setAccessionNumber(String v)      { accessionNumber.set(v); }
    public StringProperty accessionNumberProperty() { return accessionNumber; }

    public String getClassificationNumber()              { return classificationNumber.get(); }
    public void   setClassificationNumber(String v)      { classificationNumber.set(v); }
    public StringProperty classificationNumberProperty() { return classificationNumber; }

    public String getCutterNumber()              { return cutterNumber.get(); }
    public void   setCutterNumber(String v)      { cutterNumber.set(v); }
    public StringProperty cutterNumberProperty() { return cutterNumber; }

    public String getEdition()              { return edition.get(); }
    public void   setEdition(String v)      { edition.set(v); }
    public StringProperty editionProperty() { return edition; }

    public String getPublisher()              { return publisher.get(); }
    public void   setPublisher(String v)      { publisher.set(v); }
    public StringProperty publisherProperty() { return publisher; }

    public String getPlaceOfPublication()              { return placeOfPublication.get(); }
    public void   setPlaceOfPublication(String v)      { placeOfPublication.set(v); }
    public StringProperty placeOfPublicationProperty() { return placeOfPublication; }

    public int getYearOfPublication()                    { return yearOfPublication.get(); }
    public void setYearOfPublication(int v)              { yearOfPublication.set(v); }
    public IntegerProperty yearOfPublicationProperty()   { return yearOfPublication; }

    public int getNumberOfPages()                   { return numberOfPages.get(); }
    public void setNumberOfPages(int v)             { numberOfPages.set(v); }
    public IntegerProperty numberOfPagesProperty()  { return numberOfPages; }

    // Returns accession display range, e.g. "100 -> 109".
    public String getAccessionRange() {
        String acc    = getAccessionNumber();
        int    copies = getTotalCopies();
        if (acc == null || acc.isBlank() || copies <= 1) return acc;

        try {
            String numericPart = acc.replaceAll("[^0-9]", "");
            String prefix      = acc.replaceAll("[0-9]", "");
            if (numericPart.isEmpty()) return acc;

            int startNum = Integer.parseInt(numericPart);
            int endNum   = startNum + copies - 1;
            return prefix + startNum + " -> " + prefix + endNum;
        } catch (NumberFormatException e) {
            return acc;
        }
    }

    
    @Override
    public String toString() {
        return title.get() + " by " + author.get();
    }
}
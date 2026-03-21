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

    public Book(int id, String title, String author, String isbn,
                String category, int totalCopies, int availableCopies) {
        this.id              = new SimpleIntegerProperty(id);
        this.title           = new SimpleStringProperty(title);
        this.author          = new SimpleStringProperty(author);
        this.isbn            = new SimpleStringProperty(isbn);
        this.category        = new SimpleStringProperty(category);
        this.totalCopies     = new SimpleIntegerProperty(totalCopies);
        this.availableCopies = new SimpleIntegerProperty(availableCopies);
    }

    // ── ID ────────────────────────────────────────────────────────────
    public int getId()                   { return id.get(); }
    public IntegerProperty idProperty()  { return id; }

    // ── Title ─────────────────────────────────────────────────────────
    public String getTitle()                 { return title.get(); }
    public void   setTitle(String v)         { title.set(v); }
    public StringProperty titleProperty()    { return title; }

    // ── Author ────────────────────────────────────────────────────────
    public String getAuthor()                { return author.get(); }
    public void   setAuthor(String v)        { author.set(v); }
    public StringProperty authorProperty()   { return author; }

    // ── ISBN ──────────────────────────────────────────────────────────
    public String getIsbn()                  { return isbn.get(); }
    public void   setIsbn(String v)          { isbn.set(v); }
    public StringProperty isbnProperty()     { return isbn; }

    // ── Category ──────────────────────────────────────────────────────
    public String getCategory()              { return category.get(); }
    public void   setCategory(String v)      { category.set(v); }
    public StringProperty categoryProperty() { return category; }

    // ── Total Copies ──────────────────────────────────────────────────
    public int getTotalCopies()                    { return totalCopies.get(); }
    public void setTotalCopies(int v)              { totalCopies.set(v); }
    public IntegerProperty totalCopiesProperty()   { return totalCopies; }

    // ── Available Copies ──────────────────────────────────────────────
    public int getAvailableCopies()                { return availableCopies.get(); }
    public void setAvailableCopies(int v)          { availableCopies.set(v); }
    public IntegerProperty availableCopiesProperty(){ return availableCopies; }

    @Override
    public String toString() {
        return title.get() + " by " + author.get();
    }
}
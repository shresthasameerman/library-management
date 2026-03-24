package com.library.model;

import javafx.beans.property.*;

public class BookCopy {

    private final IntegerProperty id;
    private final IntegerProperty bookId;
    private final StringProperty  accessionNumber;
    private final StringProperty  status;

    public BookCopy(int id, int bookId,
                    String accessionNumber, String status) {
        this.id              = new SimpleIntegerProperty(id);
        this.bookId          = new SimpleIntegerProperty(bookId);
        this.accessionNumber = new SimpleStringProperty(accessionNumber);
        this.status          = new SimpleStringProperty(status);
    }

    public int    getId()              { return id.get(); }
    public int    getBookId()          { return bookId.get(); }
    public String getAccessionNumber() { return accessionNumber.get(); }
    public String getStatus()          { return status.get(); }

    public IntegerProperty idProperty()              { return id; }
    public StringProperty  accessionNumberProperty() { return accessionNumber; }
    public StringProperty  statusProperty()          { return status; }

    public boolean isAvailable() {
        return "AVAILABLE".equals(status.get());
    }

    @Override
    public String toString() {
        return accessionNumber.get() + " [" + status.get() + "]";
    }
}
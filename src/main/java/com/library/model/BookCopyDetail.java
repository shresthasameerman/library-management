package com.library.model;

public class BookCopyDetail {

    private final String accessionNumber;
    private final String status;
    private final String issuedTo;
    private final String dueDate;

    public BookCopyDetail(String accessionNumber, String status,
                          String issuedTo, String dueDate) {
        this.accessionNumber = accessionNumber;
        this.status = status;
        this.issuedTo = issuedTo;
        this.dueDate = dueDate;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getIssuedTo() {
        return issuedTo;
    }

    public String getDueDate() {
        return dueDate;
    }
}

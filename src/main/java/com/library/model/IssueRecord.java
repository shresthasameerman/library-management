package com.library.model;

import javafx.beans.property.*;

public class IssueRecord {

    private final IntegerProperty id;
    private final IntegerProperty bookId;
    private final IntegerProperty memberId;
    private final StringProperty  bookTitle;
    private final StringProperty  memberName;
    private final StringProperty  memberId2;   // member_id text e.g. TBC0001
    private final StringProperty  issueDate;
    private final StringProperty  dueDate;
    private final StringProperty  returnDate;
    private final DoubleProperty  fineAmount;
    private final StringProperty  status;

    public IssueRecord(int id, int bookId, int memberId,
                       String bookTitle, String memberName, String memberId2,
                       String issueDate, String dueDate, String returnDate,
                       double fineAmount, String status) {
        this.id         = new SimpleIntegerProperty(id);
        this.bookId     = new SimpleIntegerProperty(bookId);
        this.memberId   = new SimpleIntegerProperty(memberId);
        this.bookTitle  = new SimpleStringProperty(bookTitle);
        this.memberName = new SimpleStringProperty(memberName);
        this.memberId2  = new SimpleStringProperty(memberId2);
        this.issueDate  = new SimpleStringProperty(issueDate);
        this.dueDate    = new SimpleStringProperty(dueDate);
        this.returnDate = new SimpleStringProperty(returnDate);
        this.fineAmount = new SimpleDoubleProperty(fineAmount);
        this.status     = new SimpleStringProperty(status);
    }

    // ── Getters ───────────────────────────────────────────────────────
    public int    getId()          { return id.get(); }
    public int    getBookId()      { return bookId.get(); }
    public int    getMemberId()    { return memberId.get(); }
    public String getBookTitle()   { return bookTitle.get(); }
    public String getMemberName()  { return memberName.get(); }
    public String getMemberId2()   { return memberId2.get(); }
    public String getIssueDate()   { return issueDate.get(); }
    public String getDueDate()     { return dueDate.get(); }
    public String getReturnDate()  { return returnDate.get(); }
    public double getFineAmount()  { return fineAmount.get(); }
    public String getStatus()      { return status.get(); }

    // ── Properties ────────────────────────────────────────────────────
    public IntegerProperty idProperty()         { return id; }
    public StringProperty  bookTitleProperty()  { return bookTitle; }
    public StringProperty  memberNameProperty() { return memberName; }
    public StringProperty  memberId2Property()  { return memberId2; }
    public StringProperty  issueDateProperty()  { return issueDate; }
    public StringProperty  dueDateProperty()    { return dueDate; }
    public StringProperty  returnDateProperty() { return returnDate; }
    public DoubleProperty  fineAmountProperty() { return fineAmount; }
    public StringProperty  statusProperty()     { return status; }

    // ── Fine display ──────────────────────────────────────────────────
    public String getFineDisplay() {
        return fineAmount.get() > 0
            ? String.format("Rs. %.0f", fineAmount.get())
            : "-";
    }
}


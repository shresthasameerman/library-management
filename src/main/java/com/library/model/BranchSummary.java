package com.library.model;

public class BranchSummary {

    private final int id;
    private final String name;
    private final String department;
    private final String code;
    private final int totalBooks;
    private final int totalMembers;
    private final int issuedBooks;
    private final int librarians;

    public BranchSummary(int id, String name, String department, String code,
                         int totalBooks, int totalMembers,
                         int issuedBooks, int librarians) {
        this.id = id;
        this.name = name;
        this.department = department;
        this.code = code;
        this.totalBooks = totalBooks;
        this.totalMembers = totalMembers;
        this.issuedBooks = issuedBooks;
        this.librarians = librarians;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public String getCode() { return code; }
    public int getTotalBooks() { return totalBooks; }
    public int getTotalMembers() { return totalMembers; }
    public int getIssuedBooks() { return issuedBooks; }
    public int getLibrarians() { return librarians; }
}

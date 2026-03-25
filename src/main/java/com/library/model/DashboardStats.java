package com.library.model;

public class DashboardStats {

    private final int totalBooks;
    private final int totalMembers;
    private final int issuedBooks;
    private final int overdueBooks;
    private final int totalBranches;

    public DashboardStats(int totalBooks, int totalMembers, int issuedBooks,
                          int overdueBooks, int totalBranches) {
        this.totalBooks = totalBooks;
        this.totalMembers = totalMembers;
        this.issuedBooks = issuedBooks;
        this.overdueBooks = overdueBooks;
        this.totalBranches = totalBranches;
    }

    public int getTotalBooks() { return totalBooks; }
    public int getTotalMembers() { return totalMembers; }
    public int getIssuedBooks() { return issuedBooks; }
    public int getOverdueBooks() { return overdueBooks; }
    public int getTotalBranches() { return totalBranches; }
}

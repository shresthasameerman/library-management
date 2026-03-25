package com.library.util;

import com.library.model.User;

public class SessionManager {

    // Holds the currently logged-in user for the whole app session
    private static User currentUser = null;

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.isLibrarian();
    }

    public static boolean isSuperAdmin() {
        return currentUser != null && currentUser.isSuperAdmin();
    }

    public static Integer getCurrentBranchId() {
        return currentUser != null ? currentUser.getBranchId() : null;
    }

    public static boolean isBranchScopedUser() {
        return isLoggedIn() && !isSuperAdmin() && getCurrentBranchId() != null;
    }

    public static void logout() {
        currentUser = null;
        System.out.println("✓ User logged out.");
    }
}
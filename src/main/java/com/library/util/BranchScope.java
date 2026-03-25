package com.library.util;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class BranchScope {

    private BranchScope() {}

    public static boolean isScoped() {
        return SessionManager.isBranchScopedUser();
    }

    public static Integer branchId() {
        return SessionManager.getCurrentBranchId();
    }

    public static String andClause(String column) {
        return isScoped() ? " AND " + column + " = ?" : "";
    }

    public static String whereClause(String column) {
        return isScoped() ? " WHERE " + column + " = ?" : "";
    }

    public static int bind(PreparedStatement stmt, int parameterIndex)
            throws SQLException {
        if (isScoped()) {
            stmt.setInt(parameterIndex, branchId());
            return parameterIndex + 1;
        }
        return parameterIndex;
    }
}

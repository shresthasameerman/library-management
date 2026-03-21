package com.library.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Paths;
import java.io.File;

public class DatabaseConnection {

    private static Connection connection = null;

    // Stores DB in user's home folder → works on Windows too
    private static final String DB_PATH = Paths.get(
        System.getProperty("user.home"), "LibraryApp", "library.db"
    ).toString();

    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {

                // Create folder if it doesn't exist
                new File(DB_PATH).getParentFile().mkdirs();

                connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

                // Enable foreign keys (important for data integrity)
                connection.createStatement().execute("PRAGMA foreign_keys = ON");

                System.out.println("DB connected at: " + DB_PATH);
            }
        } catch (SQLException e) {
            System.err.println("DB Connection failed: " + e.getMessage());
            throw new RuntimeException("Cannot connect to database", e);
        }
        return connection;
    }
}
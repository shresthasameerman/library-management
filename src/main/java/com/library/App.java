package com.library;

import com.library.database.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class App extends Application {

    private static final long START_TIME_MILLIS = System.currentTimeMillis();

    public static long getStartTimeMillis() {
        return START_TIME_MILLIS;
    }

    @Override
    public void start(Stage stage) throws Exception {

        // Initialize app directories FIRST (before anything else)
        initializeAppDirectories();

        // Initialize DB on every launch (safe — uses CREATE IF NOT EXISTS)
        DatabaseInitializer.initialize();

        // Load Login screen
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/com/library/fxml/Login.fxml")
        );
        Scene scene = new Scene(loader.load(), 900, 600);
        scene.getStylesheets().add(
            getClass().getResource("/com/library/css/style.css").toExternalForm()
        );

        stage.setTitle("Library Management System — Login");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private static void initializeAppDirectories() {
        String home = System.getProperty("user.home");
        String[] dirs = {
                home + "/LibraryApp",
                home + "/LibraryApp/reports",
                home + "/LibraryApp/reports/excel",
                home + "/LibraryApp/reports/pdf",
                home + "/LibraryApp/reports/csv",
                home + "/LibraryApp/backups"
        };
        for (String dir : dirs) {
            try {
                Files.createDirectories(Paths.get(dir));
            } catch (IOException e) {
                System.err.println("⚠ Could not create dir: " + dir);
            }
        }
        System.out.println("✓ App directories ready.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
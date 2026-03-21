package com.library;

import com.library.database.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {

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

    public static void main(String[] args) {
        launch(args);
    }
}
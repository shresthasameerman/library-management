package com.library.util;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

public class AlertHelper {

    // ── Get main window (owner) ───────────────────────────────────────
    private static Window getOwner() {
        return Window.getWindows().stream()
            .filter(Window::isShowing)
            .findFirst()
            .orElse(null);
    }

    // ── Animated Success Dialog ───────────────────────────────────────
    public static void showSuccess(String title, String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.initOwner(getOwner());          // ← fixes background issue
        dialog.setResizable(false);

        Canvas canvas = new Canvas(100, 100);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#2dc653"));
        gc.fillOval(0, 0, 100, 100);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(6);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        gc.strokePolyline(
            new double[]{25, 42, 75},
            new double[]{52, 68, 32},
            3
        );

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-font-size: 18px; -fx-font-weight: bold;" +
            "-fx-text-fill: #1a1a2e;"
        );

        Label msgLabel = new Label(message);
        msgLabel.setStyle(
            "-fx-font-size: 13px; -fx-text-fill: #555555;"
        );
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(260);
        msgLabel.setAlignment(Pos.CENTER);

        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPrefSize(320, 260);
        root.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 16;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0, 0, 4);" +
            "-fx-padding: 36 28 36 28;"
        );
        root.getChildren().addAll(canvas, titleLabel, msgLabel);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);

        // Pop-in animation
        root.setScaleX(0.3);
        root.setScaleY(0.3);
        root.setOpacity(0);

        ScaleTransition scaleUp = new ScaleTransition(
            Duration.millis(300), root);
        scaleUp.setFromX(0.3); scaleUp.setFromY(0.3);
        scaleUp.setToX(1.0);   scaleUp.setToY(1.0);
        scaleUp.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(
            Duration.millis(250), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ParallelTransition popIn = new ParallelTransition(scaleUp, fadeIn);

        // Auto-close after 2 seconds
        FadeTransition fadeOut = new FadeTransition(
            Duration.millis(300), root);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.millis(1800));
        fadeOut.setOnFinished(e -> dialog.close());

        popIn.setOnFinished(e -> fadeOut.play());

        dialog.show();
        popIn.play();
    }

    // ── Animated Error Dialog ─────────────────────────────────────────
    public static void showError(String title, String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.initOwner(getOwner());          // ← fixes background issue
        dialog.setResizable(false);

        Canvas canvas = new Canvas(100, 100);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#ef233c"));
        gc.fillOval(0, 0, 100, 100);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(6);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.strokeLine(30, 30, 70, 70);
        gc.strokeLine(70, 30, 30, 70);

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
            "-fx-font-size: 18px; -fx-font-weight: bold;" +
            "-fx-text-fill: #1a1a2e;"
        );

        Label msgLabel = new Label(message);
        msgLabel.setStyle(
            "-fx-font-size: 13px; -fx-text-fill: #555555;"
        );
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(260);
        msgLabel.setAlignment(Pos.CENTER);

        Button okBtn = new Button("OK");
        okBtn.setStyle(
            "-fx-background-color: #ef233c; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-padding: 8 32;" +
            "-fx-background-radius: 6; -fx-cursor: hand;"
        );
        okBtn.setOnAction(e -> dialog.close());

        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPrefSize(320, 280);
        root.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 16;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 20, 0, 0, 4);" +
            "-fx-padding: 36 28 36 28;"
        );
        root.getChildren().addAll(canvas, titleLabel, msgLabel, okBtn);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);

        // Pop-in animation
        root.setScaleX(0.3);
        root.setScaleY(0.3);
        root.setOpacity(0);

        ScaleTransition scaleUp = new ScaleTransition(
            Duration.millis(250), root);
        scaleUp.setFromX(0.3); scaleUp.setFromY(0.3);
        scaleUp.setToX(1.0);   scaleUp.setToY(1.0);

        FadeTransition fadeIn = new FadeTransition(
            Duration.millis(200), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        new ParallelTransition(scaleUp, fadeIn).play();
        dialog.show();
    }
}
package com.library.util;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class AlertHelper {

    // ── Animated Success Dialog ───────────────────────────────────────
    public static void showSuccess(String title, String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setResizable(false);

        // ── Green circle background ───────────────────────────────────
        Canvas canvas = new Canvas(100, 100);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Draw green circle
        gc.setFill(Color.web("#2dc653"));
        gc.fillOval(0, 0, 100, 100);

        // Draw checkmark
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(6);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        gc.strokePolyline(
            new double[]{25, 42, 75},
            new double[]{52, 68, 32},
            3
        );

        // ── Labels ────────────────────────────────────────────────────
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

        // ── Layout ────────────────────────────────────────────────────
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

        // ── Animation ─────────────────────────────────────────────────

        // 1. Start small and scale up (pop effect)
        root.setScaleX(0.3);
        root.setScaleY(0.3);
        root.setOpacity(0);

        ScaleTransition scaleUp = new ScaleTransition(
            Duration.millis(300), root
        );
        scaleUp.setFromX(0.3);
        scaleUp.setFromY(0.3);
        scaleUp.setToX(1.0);
        scaleUp.setToY(1.0);
        scaleUp.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(
            Duration.millis(250), root
        );
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ParallelTransition popIn = new ParallelTransition(scaleUp, fadeIn);

        // 2. Canvas: bounce the checkmark circle
        ScaleTransition iconPop = new ScaleTransition(
            Duration.millis(200), canvas
        );
        iconPop.setFromX(0.5);
        iconPop.setFromY(0.5);
        iconPop.setToX(1.0);
        iconPop.setToY(1.0);
        iconPop.setInterpolator(Interpolator.EASE_OUT);

        // 3. Auto-close after 2 seconds with fade out
        FadeTransition fadeOut = new FadeTransition(
            Duration.millis(300), root
        );
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.millis(1800));
        fadeOut.setOnFinished(e -> dialog.close());

        // Play sequence
        popIn.setOnFinished(e -> {
            iconPop.play();
            fadeOut.play();
        });

        dialog.show();
        popIn.play();
    }

    // ── Animated Error Dialog ─────────────────────────────────────────
    public static void showError(String title, String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setResizable(false);

        // Red circle with X
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

        // OK button to dismiss manually
        javafx.scene.control.Button okBtn =
            new javafx.scene.control.Button("OK");
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

        // Shake animation for error
        root.setScaleX(0.3);
        root.setScaleY(0.3);
        root.setOpacity(0);

        ScaleTransition scaleUp = new ScaleTransition(
            Duration.millis(250), root
        );
        scaleUp.setFromX(0.3);
        scaleUp.setFromY(0.3);
        scaleUp.setToX(1.0);
        scaleUp.setToY(1.0);

        FadeTransition fadeIn = new FadeTransition(
            Duration.millis(200), root
        );
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        new ParallelTransition(scaleUp, fadeIn).play();

        dialog.show();
    }
}
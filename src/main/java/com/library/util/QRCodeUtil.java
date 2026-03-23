package com.library.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class QRCodeUtil {

    private static final int QR_SIZE = 300;

    // ── Generate QR content string ────────────────────────────────────
    public static String buildQRContent(
            String accessionNumber, String title,
            String author, String classificationNumber,
            int bookId) {
        return String.format(
            "THE BRITISH COLLEGE LIBRARY\n" +
            "Accession: %s\n" +
            "Title: %s\n" +
            "Author: %s\n" +
            "Class No: %s\n" +
            "Book ID: %d",
            accessionNumber, title, author,
            classificationNumber, bookId
        );
    }

    // ── Generate JavaFX Image from QR content ─────────────────────────
    public static Image generateQRImage(String content) {
        try {
            BitMatrix bitMatrix = createBitMatrix(content);
            return toFxImage(bitMatrix);
        } catch (WriterException e) {
            System.err.println("QR generation failed: " + e.getMessage());
            return null;
        }
    }

    // ── Save QR code as PNG file ──────────────────────────────────────
    public static String saveQRCode(String content,
                                     String accessionNumber) {
        String dir  = Paths.get(System.getProperty("user.home"),
                                "LibraryApp", "QRCodes").toString();
        String path = dir + "/" + sanitize(accessionNumber) + "_QR.png";

        try {
            Files.createDirectories(Path.of(dir));
            BitMatrix     bitMatrix = createBitMatrix(content);
            BufferedImage image     = toBufferedImage(bitMatrix);
            ImageIO.write(image, "PNG", new File(path));
            System.out.println("✓ QR saved: " + path);
            return path;
        } catch (Exception e) {
            System.err.println("QR save failed: " + e.getMessage());
            return null;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────
    private static BitMatrix createBitMatrix(String content)
            throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        return new QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE,
            QR_SIZE, QR_SIZE, hints
        );
    }

    private static Image toFxImage(BitMatrix matrix) {
        int width  = matrix.getWidth();
        int height = matrix.getHeight();
        WritableImage image = new WritableImage(width, height);
        PixelWriter   pw    = image.getPixelWriter();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pw.setColor(x, y, matrix.get(x, y)
                    ? javafx.scene.paint.Color.BLACK
                    : javafx.scene.paint.Color.WHITE);
            }
        }
        return image;
    }

    private static BufferedImage toBufferedImage(BitMatrix matrix) {
        int width  = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(
            width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, matrix.get(x, y)
                    ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return image;
    }

    private static String sanitize(String input) {
        return input == null ? "BOOK"
            : input.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
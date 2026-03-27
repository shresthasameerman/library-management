package com.library.util;

import javafx.scene.control.TableView;
import javafx.collections.ObservableList;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;

/**
 * Utility class for exporting dashboard data to various formats
 */
public class ExportService {
    
    private static final String EXPORT_DIR = System.getProperty("user.home") + "/.LibraryApp/exports/";
    
    static {
        // Create exports directory if it doesn't exist
        new File(EXPORT_DIR).mkdirs();
    }

    /**
     * Export TableView data to CSV file
     */
    public static String exportToCSV(TableView<?> table, String filename) {
        try {
            String filePath = EXPORT_DIR + filename + "_" + getTimestamp() + ".csv";
            PrintWriter writer = new PrintWriter(filePath);
            
            // Write header
            StringBuilder header = new StringBuilder();
            table.getColumns().forEach(col -> 
                header.append(col.getText()).append(",")
            );
            writer.println(header.deleteCharAt(header.length() - 1).toString());
            
            // Write data rows
            table.getItems().forEach(item -> {
                StringBuilder row = new StringBuilder();
                table.getColumns().forEach(col -> {
                    Object cellData = col.getCellData(table.getItems().indexOf(item));
                    row.append(cellData != null ? cellData.toString() : "").append(",");
                });
                writer.println(row.deleteCharAt(row.length() - 1).toString());
            });
            
            writer.close();
            System.out.println("✓ CSV exported: " + filePath);
            return filePath;
        } catch (IOException e) {
            System.err.println("CSV export failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Export data to JSON format
     */
    public static String exportToJSON(ObservableList<?> items, String filename) {
        try {
            String filePath = EXPORT_DIR + filename + "_" + getTimestamp() + ".json";
            PrintWriter writer = new PrintWriter(filePath);
            
            writer.println("[");
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                // Convert object to JSON string representation
                writer.println("  " + objectToJSON(item));
                if (i < items.size() - 1) {
                    writer.println(",");
                }
            }
            writer.println("]");
            
            writer.close();
            System.out.println("✓ JSON exported: " + filePath);
            return filePath;
        } catch (IOException e) {
            System.err.println("JSON export failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Export data to Excel format (basic CSV compatible format)
     */
    public static String exportToExcel(TableView<?> table, String filename) {
        // For Excel compatibility, use CSV format with Excel-specific headers
        try {
            String filePath = EXPORT_DIR + filename + "_" + getTimestamp() + ".xlsx";
            PrintWriter writer = new PrintWriter(filePath);
            
            // Excel compatible CSV header
            writer.println("sep=,");
            
            // Write table headers
            StringBuilder header = new StringBuilder();
            table.getColumns().forEach(col -> 
                header.append("\"").append(col.getText()).append("\",")
            );
            if (header.length() > 0) {
                writer.println(header.deleteCharAt(header.length() - 1).toString());
            }
            
            // Write data
            table.getItems().forEach(item -> {
                StringBuilder row = new StringBuilder();
                table.getColumns().forEach(col -> {
                    Object cellData = col.getCellData(table.getItems().indexOf(item));
                    String value = cellData != null ? cellData.toString().replace("\"", "\"\"") : "";
                    row.append("\"").append(value).append("\",");
                });
                if (row.length() > 0) {
                    writer.println(row.deleteCharAt(row.length() - 1).toString());
                }
            });
            
            writer.close();
            System.out.println("✓ Excel exported: " + filePath);
            return filePath;
        } catch (IOException e) {
            System.err.println("Excel export failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Export report with title and metadata
     */
    public static String exportReport(String title, String type, String[] headers, String[][] data, String format) {
        try {
            String filename = title.replaceAll(" ", "_") + "_" + getTimestamp();
            String filePath = EXPORT_DIR + filename;
            
            if ("CSV".equalsIgnoreCase(format)) {
                filePath += ".csv";
                PrintWriter writer = new PrintWriter(filePath);
                writer.println("Report: " + title);
                writer.println("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.println("Type: " + type);
                writer.println("");
                
                // Headers
                StringBuilder header = new StringBuilder();
                for (String h : headers) {
                    header.append(h).append(",");
                }
                writer.println(header.deleteCharAt(header.length() - 1).toString());
                
                // Data
                for (String[] row : data) {
                    StringBuilder rowStr = new StringBuilder();
                    for (String cell : row) {
                        rowStr.append(cell != null ? cell : "").append(",");
                    }
                    writer.println(rowStr.deleteCharAt(rowStr.length() - 1).toString());
                }
                
                writer.close();
            } else if ("JSON".equalsIgnoreCase(format)) {
                filePath += ".json";
                PrintWriter writer = new PrintWriter(filePath);
                writer.println("{");
                writer.println("  \"title\": \"" + title + "\",");
                writer.println("  \"type\": \"" + type + "\",");
                writer.println("  \"generated\": \"" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\",");
                writer.println("  \"data\": [");
                
                for (int i = 0; i < data.length; i++) {
                    writer.println("    {");
                    for (int j = 0; j < headers.length; j++) {
                        writer.print("      \"" + headers[j] + "\": \"" + (data[i][j] != null ? data[i][j] : "") + "\"");
                        if (j < headers.length - 1) writer.println(",");
                        else writer.println();
                    }
                    writer.print("    }");
                    if (i < data.length - 1) writer.println(",");
                    else writer.println();
                }
                
                writer.println("  ]");
                writer.println("}");
                writer.close();
            }
            
            System.out.println("✓ Report exported: " + filePath);
            return filePath;
        } catch (IOException e) {
            System.err.println("Report export failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get timestamp for file naming
     */
    private static String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
    }

    /**
     * Convert object to JSON representation
     */
    private static String objectToJSON(Object obj) {
        // Basic implementation - can be enhanced with Gson/Jackson
        return "{\"data\": \"" + obj.toString() + "\"}";
    }

    /**
     * Get export directory path
     */
    public static String getExportDirectory() {
        return EXPORT_DIR;
    }

    /**
     * Clear old exports (older than 30 days)
     */
    public static void cleanupOldExports() {
        File dir = new File(EXPORT_DIR);
        if (dir.exists() && dir.isDirectory()) {
            long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
            File[] files = dir.listFiles();
            
            if (files != null) {
                for (File file : files) {
                    if (file.lastModified() < thirtyDaysAgo) {
                        if (file.delete()) {
                            System.out.println("✓ Deleted old export: " + file.getName());
                        }
                    }
                }
            }
        }
    }

    /**
     * Get list of available exports
     */
    public static File[] getAvailableExports() {
        File dir = new File(EXPORT_DIR);
        if (dir.exists() && dir.isDirectory()) {
            return dir.listFiles();
        }
        return new File[0];
    }
}

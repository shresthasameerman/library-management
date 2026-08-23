package com.library.service;

import com.library.database.DatabaseConnection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class BackupService {

    private static final String DB_FILE_NAME = "library.db";
    private static final String APP_DIR = Paths.get(System.getProperty("user.home"), "LibraryApp").toString();
    private static final String BACKUP_DIR = Paths.get(APP_DIR, "backups").toString();
    private static final String DB_PATH = Paths.get(APP_DIR, DB_FILE_NAME).toString();

    private ScheduledExecutorService scheduler;

    public BackupService() {
        createBackupDirIfNotExists();
    }

    private void createBackupDirIfNotExists() {
        File dir = new File(BACKUP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Performs a manual backup of the database.
     * @return The path to the created backup file, or null if it failed.
     */
    public String performManualBackup() {
        return backupDatabase("manual");
    }

    /**
     * Starts the automatic backup scheduler. It takes a backup once a day.
     */
    public void startAutoBackupScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return; // Already running
        }
        
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoBackupThread");
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        });

        // Run backup once a day. Initial delay can be set to 0 to take one on startup, 
        // or longer if you only want it running after 24 hours. We'll do an initial backup if one doesn't exist today.
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Starting scheduled database backup...");
                backupDatabase("auto");
                cleanupOldBackups(30); // Keep last 30 days of backups
            } catch (Exception e) {
                System.err.println("Scheduled backup failed: " + e.getMessage());
            }
        }, 0, 24, TimeUnit.HOURS);
    }

    public void stopAutoBackupScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Backs up the SQLite database file safely.
     */
    private String backupDatabase(String prefix) {
        createBackupDirIfNotExists();
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupFileName = prefix + "_library_backup_" + timestamp + ".db";
        Path sourcePath = Paths.get(DB_PATH);
        Path destPath = Paths.get(BACKUP_DIR, backupFileName);

        File sourceFile = sourcePath.toFile();
        if (!sourceFile.exists()) {
            System.err.println("Cannot backup: Database file not found at " + sourcePath);
            return null;
        }

        // Before copying, ensure SQLite writes any pending data by obtaining a connection and creating a checkpoint if using WAL mode.
        // Even for standard rollback journal, it's good practice.
        try (Connection conn = DatabaseConnection.getConnection()) {
            // A simple query to ensure the DB is not locked and is in a consistent state
            conn.createStatement().execute("PRAGMA wal_checkpoint(FULL);");
        } catch (SQLException e) {
            System.err.println("Warning: Could not checkpoint DB before backup: " + e.getMessage());
        }

        try {
            Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("✅ Backup successful: " + destPath.toString());
            return destPath.toString();
        } catch (IOException e) {
            System.err.println("❌ Backup failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cleans up backup files older than a certain number of days to save disk space.
     */
    private void cleanupOldBackups(int keepDays) {
        long cutoffMillis = System.currentTimeMillis() - ((long) keepDays * 24 * 60 * 60 * 1000);
        
        try (Stream<Path> files = Files.list(Paths.get(BACKUP_DIR))) {
            files.filter(path -> path.toString().endsWith(".db"))
                 .forEach(path -> {
                     File file = path.toFile();
                     if (file.lastModified() < cutoffMillis) {
                         if (file.delete()) {
                             System.out.println("Deleted old backup: " + file.getName());
                         }
                     }
                 });
        } catch (IOException e) {
            System.err.println("Failed to clean up old backups: " + e.getMessage());
        }
    }
}

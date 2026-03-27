package com.library.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import java.time.LocalDateTime;

public class ReportRecord {
    private final StringProperty reportName;
    private final StringProperty reportType;
    private final StringProperty format;
    private final StringProperty generatedBy;
    private final StringProperty generatedDate;
    private final StringProperty rowCount;
    private final StringProperty filePath;
    private final LocalDateTime timestamp;

    public ReportRecord(String reportName, String reportType, String format,
                        String generatedBy, LocalDateTime generatedDate,
                        String rowCount, String filePath) {
        this.reportName = new SimpleStringProperty(reportName);
        this.reportType = new SimpleStringProperty(reportType);
        this.format = new SimpleStringProperty(format);
        this.generatedBy = new SimpleStringProperty(generatedBy);
        this.timestamp = generatedDate;
        this.generatedDate = new SimpleStringProperty(
            generatedDate.format(java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm"))
        );
        this.rowCount = new SimpleStringProperty(rowCount);
        this.filePath = new SimpleStringProperty(filePath);
    }

    // Properties
    public StringProperty reportNameProperty() { return reportName; }
    public StringProperty reportTypeProperty() { return reportType; }
    public StringProperty formatProperty() { return format; }
    public StringProperty generatedByProperty() { return generatedBy; }
    public StringProperty generatedDateProperty() { return generatedDate; }
    public StringProperty rowCountProperty() { return rowCount; }
    public StringProperty filePathProperty() { return filePath; }

    // Getters
    public String getReportName() { return reportName.get(); }
    public String getReportType() { return reportType.get(); }
    public String getFormat() { return format.get(); }
    public String getGeneratedBy() { return generatedBy.get(); }
    public String getGeneratedDate() { return generatedDate.get(); }
    public String getRowCount() { return rowCount.get(); }
    public String getFilePath() { return filePath.get(); }
    public LocalDateTime getTimestamp() { return timestamp; }
}

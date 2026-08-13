package com.jobtracker.entity.enums;

/**
 * File formats supported by the application export module.
 *
 * <p>Adding a new format means adding a constant here plus one
 * {@link com.jobtracker.service.export.ExportWriter} implementation — no other part of the
 * export pipeline needs to change.
 */
public enum ExportFormat {

    CSV("csv", "text/csv; charset=UTF-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String fileExtension;
    private final String contentType;

    ExportFormat(String fileExtension, String contentType) {
        this.fileExtension = fileExtension;
        this.contentType = contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public String getContentType() {
        return contentType;
    }
}

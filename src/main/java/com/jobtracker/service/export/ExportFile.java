package com.jobtracker.service.export;

import com.jobtracker.entity.enums.ExportFormat;

/**
 * A generated export file held in memory, ready to be streamed to the client or handed to an
 * {@link ExportDestination}.
 */
public record ExportFile(
        String fileName,
        ExportFormat format,
        byte[] content,
        int recordCount,
        boolean truncated
) {

    public String contentType() {
        return format.getContentType();
    }

    /** Same file under a different name — scheduled runs stamp the time into the name. */
    public ExportFile withFileName(String newFileName) {
        return new ExportFile(newFileName, format, content, recordCount, truncated);
    }
}

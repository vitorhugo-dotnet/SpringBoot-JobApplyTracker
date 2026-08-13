package com.jobtracker.service.export;

import com.jobtracker.entity.enums.ExportFormat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Serializes exported rows into one file format.
 *
 * <p>Supporting a new format (JSON, PDF, …) means adding an {@link ExportFormat} constant and one
 * implementation of this interface — the querying, filtering, scheduling, destination and history
 * logic is format-agnostic.
 */
public interface ExportWriter {

    ExportFormat format();

    /**
     * Writes the header and every row to {@code out}.
     *
     * @return the number of data rows written
     */
    int write(OutputStream out, List<ExportColumn> columns, ExportRowSource rows) throws IOException;
}

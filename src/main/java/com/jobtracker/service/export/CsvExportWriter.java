package com.jobtracker.service.export;

import com.jobtracker.entity.enums.ExportFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RFC 4180 CSV writer.
 *
 * <p>The file starts with a UTF-8 BOM so that Excel — notably in pt-BR installs — opens it with the
 * right encoding and keeps accented characters intact. Values are quoted whenever they contain the
 * delimiter, quotes or line breaks, and quotes inside a value are doubled.
 */
@Component
public class CsvExportWriter implements ExportWriter {

    /** UTF-8 byte order mark: makes Excel detect UTF-8 instead of the system ANSI codepage. */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final char DELIMITER = ',';
    private static final String LINE_SEPARATOR = "\r\n";

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public int write(OutputStream out, List<ExportColumn> columns, ExportRowSource rows) throws IOException {
        out.write(UTF8_BOM);
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        writeRow(writer, columns.stream().map(ExportColumn::getHeader).toList());

        int written;
        try {
            written = rows.forEachRow(application -> {
                try {
                    writeRow(writer, columns.stream()
                            .map(column -> ExportCellSanitizer.sanitize(column.valueOf(application)))
                            .toList());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        writer.flush();
        return written;
    }

    private void writeRow(Writer writer, List<String> values) throws IOException {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                row.append(DELIMITER);
            }
            row.append(escape(values.get(i)));
        }
        row.append(LINE_SEPARATOR);
        writer.write(row.toString());
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean mustQuote = value.indexOf(DELIMITER) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!mustQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}

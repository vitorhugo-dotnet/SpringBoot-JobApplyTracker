package com.jobtracker.service.export;

import com.jobtracker.entity.enums.ExportFormat;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * XLSX writer built on Apache POI's streaming workbook ({@link SXSSFWorkbook}): only a small window
 * of rows is kept in memory, the rest is flushed to a temporary file as it is written.
 *
 * <p>Values are text cells and go through {@link ExportCellSanitizer}, so a note starting with
 * {@code =} is never evaluated when the sheet is opened.
 */
@Component
public class XlsxExportWriter implements ExportWriter {

    private static final int ROW_ACCESS_WINDOW = 100;
    private static final String SHEET_NAME = "Applications";

    @Override
    public ExportFormat format() {
        return ExportFormat.XLSX;
    }

    @Override
    public int write(OutputStream out, List<ExportColumn> columns, ExportRowSource rows) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW)) {
            workbook.setCompressTempFiles(true);
            SXSSFSheet sheet = workbook.createSheet(SHEET_NAME);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).getHeader());
                cell.setCellStyle(headerStyle);
            }
            sheet.createFreezePane(0, 1);

            AtomicInteger rowIndex = new AtomicInteger(1);
            int written = rows.forEachRow(application -> {
                Row row = sheet.createRow(rowIndex.getAndIncrement());
                for (int i = 0; i < columns.size(); i++) {
                    row.createCell(i).setCellValue(
                            ExportCellSanitizer.sanitize(columns.get(i).valueOf(application)));
                }
            });

            workbook.write(out);
            workbook.dispose();
            return written;
        }
    }
}

package com.jobtracker.unit.export;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ExportFormat;
import com.jobtracker.service.export.CsvExportWriter;
import com.jobtracker.service.export.ExportColumn;
import com.jobtracker.service.export.ExportRowSource;
import com.jobtracker.service.export.XlsxExportWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExportWritersTest {

    private final CsvExportWriter csvWriter = new CsvExportWriter();
    private final XlsxExportWriter xlsxWriter = new XlsxExportWriter();

    @Test
    void csv_shouldStartWithUtf8BomAndKeepAccents() throws Exception {
        JobApplication application = application("Analista de Sistemas Sênior", "Ação Ltda");

        byte[] bytes = writeCsv(List.of(ExportColumn.VACANCY_NAME, ExportColumn.ORGANIZATION), application);

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);

        String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).contains("Analista de Sistemas Sênior");
        assertThat(content).contains("Ação Ltda");
    }

    @Test
    void csv_shouldEscapeDelimitersQuotesAndNewlines() throws Exception {
        JobApplication application = application("Dev, Backend", "ACME");
        application.setNote("Line one\nLine \"two\"");

        String content = writeCsvAsString(
                List.of(ExportColumn.VACANCY_NAME, ExportColumn.NOTE), application);

        assertThat(content).contains("\"Dev, Backend\"");
        assertThat(content).contains("\"Line one\nLine \"\"two\"\"\"");
    }

    @Test
    void csv_shouldNeutralizeFormulaInjection() throws Exception {
        JobApplication application = application("=cmd|'/c calc'!A1", "ACME");
        application.setNote("@SUM(1+1)");

        String content = writeCsvAsString(
                List.of(ExportColumn.VACANCY_NAME, ExportColumn.NOTE), application);

        assertThat(content).contains("'=cmd|'/c calc'!A1");
        assertThat(content).contains("'@SUM(1+1)");
        assertThat(content).doesNotContain("\n=cmd");
    }

    @Test
    void csv_shouldKeepPlainNegativeNumbersIntact() throws Exception {
        JobApplication application = application("-5", "ACME");

        String content = writeCsvAsString(List.of(ExportColumn.VACANCY_NAME), application);

        assertThat(content).contains("-5");
        assertThat(content).doesNotContain("'-5");
    }

    @Test
    void csv_shouldWriteHeadersInTheRequestedColumnOrder() throws Exception {
        String content = writeCsvAsString(
                List.of(ExportColumn.STATUS, ExportColumn.VACANCY_NAME), application("Dev", "ACME"));

        // The first line carries the BOM, so compare after it.
        assertThat(content.lines().findFirst().orElseThrow()).endsWith("Status,Vacancy");
    }

    @Test
    void csv_shouldWriteToSendLaterForDrafts() throws Exception {
        JobApplication draft = application("Draft", "ACME");
        draft.setStatus(null);

        String content = writeCsvAsString(List.of(ExportColumn.STATUS), draft);

        assertThat(content).contains("TO_SEND_LATER");
    }

    @Test
    void xlsx_shouldWriteHeaderAndRows() throws Exception {
        JobApplication application = application("Data Engineer", "Ação Ltda");
        List<ExportColumn> columns = List.of(ExportColumn.VACANCY_NAME, ExportColumn.ORGANIZATION);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int written = xlsxWriter.write(out, columns, source(application));

        assertThat(written).isEqualTo(1);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Vacancy");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Organization");

            Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("Data Engineer");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("Ação Ltda");
        }
    }

    @Test
    void xlsx_shouldNeutralizeFormulaInjection() throws Exception {
        JobApplication application = application("=1+1", "ACME");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        xlsxWriter.write(out, List.of(ExportColumn.VACANCY_NAME), source(application));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("'=1+1");
        }
    }

    @Test
    void writers_shouldDeclareTheirFormat() {
        assertThat(csvWriter.format()).isEqualTo(ExportFormat.CSV);
        assertThat(xlsxWriter.format()).isEqualTo(ExportFormat.XLSX);
    }

    private byte[] writeCsv(List<ExportColumn> columns, JobApplication... applications) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        csvWriter.write(out, columns, source(applications));
        return out.toByteArray();
    }

    private String writeCsvAsString(List<ExportColumn> columns, JobApplication... applications) throws Exception {
        return new String(writeCsv(columns, applications), StandardCharsets.UTF_8);
    }

    private static ExportRowSource source(JobApplication... applications) {
        return consumer -> {
            for (JobApplication application : applications) {
                consumer.accept(application);
            }
            return applications.length;
        };
    }

    private static JobApplication application(String vacancyName, String organization) {
        JobApplication application = new JobApplication();
        application.setId(UUID.randomUUID());
        application.setVacancyName(vacancyName);
        application.setOrganization(organization);
        application.setStatus("RH");
        application.setApplicationDate(LocalDate.of(2026, 7, 16));
        application.setCreatedAt(LocalDateTime.of(2026, 7, 16, 10, 30));
        application.setUpdatedAt(LocalDateTime.of(2026, 7, 16, 10, 30));
        return application;
    }
}

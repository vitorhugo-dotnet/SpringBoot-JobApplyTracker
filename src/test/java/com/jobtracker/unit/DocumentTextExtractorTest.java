package com.jobtracker.unit;

import com.jobtracker.service.DocumentTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsMarkdownAsUtf8Text() {
        byte[] bytes = "# About Me\n\n- Java\n- Spring Boot".getBytes(StandardCharsets.UTF_8);
        assertThat(extractor.extractMarkdown(bytes))
                .contains("# About Me")
                .contains("Spring Boot");
    }

    @Test
    void extractsTextFromDocx() throws Exception {
        byte[] docxBytes;
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Senior Java Developer with Spring Boot experience");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            docxBytes = out.toByteArray();
        }

        assertThat(extractor.extractDocx(docxBytes))
                .contains("Senior Java Developer with Spring Boot experience");
    }

    @Test
    void extractsTextFromPdf() throws Exception {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 720);
                cs.showText("Backend engineer profile");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdfBytes = out.toByteArray();
        }

        assertThat(extractor.extractPdf(pdfBytes))
                .contains("Backend engineer profile");
    }
}

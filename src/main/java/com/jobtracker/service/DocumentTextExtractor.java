package com.jobtracker.service;

import com.jobtracker.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Centralises plain-text extraction from the binary document formats supported as base information:
 * PDF (PDFBox), DOCX (Apache POI), and Markdown/plain-text (UTF-8). Google Docs are read separately
 * through {@link GoogleDriveApiClient#readGoogleDocText}.
 */
@Component
public class DocumentTextExtractor {

    public String extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to extract text from PDF document: " + ex.getMessage());
        }
    }

    public String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException ex) {
            throw new BadRequestException("Failed to extract text from DOCX document: " + ex.getMessage());
        }
    }

    public String extractMarkdown(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

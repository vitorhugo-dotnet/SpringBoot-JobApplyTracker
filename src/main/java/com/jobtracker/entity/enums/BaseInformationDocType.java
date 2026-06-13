package com.jobtracker.entity.enums;

import com.jobtracker.exception.BadRequestException;
import com.jobtracker.service.GoogleDriveApiClient;

/**
 * Supported document types for a "base information about me" document hosted on Google Drive.
 *
 * <p>Unlike base resumes (which are either editable Google Docs templates or read-only PDFs),
 * base information documents are always read-only knowledge sources and may additionally be
 * Word (DOCX) or Markdown files. The concrete type determines which text-extraction strategy
 * {@code DocumentTextExtractor} applies when the content is read.
 */
public enum BaseInformationDocType {

    GOOGLE_DOC,
    PDF,
    DOCX,
    MARKDOWN;

    /**
     * Classifies a Drive file into a supported base-information document type using its MIME type,
     * falling back to the filename extension for Markdown (which Drive frequently reports as
     * {@code text/plain} or an opaque binary type).
     *
     * @throws BadRequestException when the file is not a supported base-information document type.
     */
    public static BaseInformationDocType fromDriveFile(String mimeType, String fileName) {
        String mime = mimeType == null ? "" : mimeType.trim().toLowerCase();
        String name = fileName == null ? "" : fileName.trim().toLowerCase();

        if (GoogleDriveApiClient.GOOGLE_DOC_MIME_TYPE.equals(mime)) {
            return GOOGLE_DOC;
        }
        if (GoogleDriveApiClient.PDF_MIME_TYPE.equals(mime)) {
            return PDF;
        }
        if (GoogleDriveApiClient.DOCX_MIME_TYPE.equals(mime)) {
            return DOCX;
        }
        if (GoogleDriveApiClient.MARKDOWN_MIME_TYPE.equals(mime)
                || "text/plain".equals(mime)
                || name.endsWith(".md")
                || name.endsWith(".markdown")) {
            return MARKDOWN;
        }
        throw new BadRequestException(
                "Unsupported base information document type. Only Google Docs, PDF, DOCX, and Markdown files are supported.");
    }
}

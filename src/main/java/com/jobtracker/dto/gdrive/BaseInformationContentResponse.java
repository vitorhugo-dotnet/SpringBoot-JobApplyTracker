package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Base information document metadata and plain text content extracted from Google Docs, PDF, DOCX, or Markdown. " +
        "This is the authoritative, highest-priority source of truth about the candidate.")
public record BaseInformationContentResponse(
        @Schema(description = "UUID of the base information document. Use this ID in API calls — filenames and Google file IDs are NOT valid here.",
                format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Display name of the document", example = "About Me - Vitor Hugo")
        String name,

        @Schema(description = "Document type: GOOGLE_DOC, PDF, DOCX, or MARKDOWN", example = "MARKDOWN")
        String docType,

        @Schema(description = "Plain text content extracted from the document")
        String content
) {}

package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Lightweight metadata of a configured base information document, optimised for GPT/frontend discovery")
public record BaseInformationResponse(
        @Schema(description = "UUID of the base information document. Use this ID in API calls — filenames and Google file IDs are NOT valid here.",
                format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Display name of the document", example = "About Me - Vitor Hugo")
        String name,

        @Schema(description = "Document type: GOOGLE_DOC, PDF, DOCX, or MARKDOWN", example = "MARKDOWN")
        String docType,

        @Schema(description = "Google Drive web view link")
        String webViewLink,

        @Schema(description = "Timestamp when this base information document was registered")
        LocalDateTime createdAt
) {}

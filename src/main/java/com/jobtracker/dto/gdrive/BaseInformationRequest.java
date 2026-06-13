package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to register a Google Drive document (Google Docs, PDF, DOCX, or Markdown) as base information about the candidate")
public record BaseInformationRequest(
        @Schema(description = "Google Drive document ID or URL",
                example = "https://drive.google.com/file/d/1234567890abcdef/view")
        @NotBlank(message = "documentIdOrUrl is required")
        String documentIdOrUrl
) {}

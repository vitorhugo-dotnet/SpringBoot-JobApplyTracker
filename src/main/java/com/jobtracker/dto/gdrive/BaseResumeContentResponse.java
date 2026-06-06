package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Base resume metadata and plain text content extracted from Google Docs or PDF. " +
        "Template placeholders such as {{SUMMARY}} and {{SKILLS}} are preserved as-is for AI analysis.")
public record BaseResumeContentResponse(
        @Schema(description = "UUID of the base resume. Use this ID in API calls — filenames and Google file IDs are NOT valid here.",
                format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Display name of the resume document", example = "BASE - CV - Vitor Hugo EN")
        String name,

        @Schema(description = "Language code of the resume (e.g. EN, PT). Null when not configured.", example = "EN")
        String language,

        @Schema(description = "Whether this resume is a reusable template", example = "true")
        boolean template,

        @Schema(description = "Whether this resume is read-only (PDF file — content extracted from the PDF)", example = "false")
        boolean readOnly,

        @Schema(description = "Plain text content extracted from the Google Docs document or PDF. " +
                "Template placeholders such as {{SUMMARY}} and {{SKILLS}} are preserved.")
        String content
) {}

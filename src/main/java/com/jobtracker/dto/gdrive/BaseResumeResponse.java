package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Lightweight metadata of a configured Google Docs base resume, optimised for GPT/frontend discovery")
public record BaseResumeResponse(
        @Schema(description = "UUID of the base resume. Use this ID in API calls — filenames and Google file IDs are NOT valid here.",
                format = "uuid", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Display name of the resume document", example = "BASE - CV - Vitor Hugo EN")
        String name,

        @Schema(description = "Language code of the resume (e.g. EN, PT). Null when not configured.", example = "EN")
        String language,

        @Schema(description = "Whether this resume is a reusable template", example = "true")
        boolean template,

        @Schema(description = "Whether this resume is read-only (PDF file — cannot be used for generation, placeholder detection, or copying)", example = "false")
        boolean readOnly,

        @Schema(description = "Timestamp when this base resume was registered")
        LocalDateTime createdAt
) {}

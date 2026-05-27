package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Generated resume from template placeholders")
public record ResumePlaceholderResponse(
        UUID applicationId,
        UUID baseResumeId,
        String copiedFileId,
        String pdfFileId,
        String documentUrl,
        String pdfUrl,
        Map<String, String> values,
        List<String> placeholders,
        LocalDateTime generatedAt
) {}

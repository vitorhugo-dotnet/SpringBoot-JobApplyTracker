package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Resume placeholder detection or generation result")
public record ResumePlaceholderResponse(
        UUID applicationId,
        UUID baseResumeId,
        List<String> placeholders,
        Map<String, String> values,
        String copiedFileId,
        String copiedFileName,
        String documentWebViewLink,
        String pdfFileId,
        String pdfFileName,
        String pdfWebViewLink,
        String vacancyFolderId,
        String vacancyFolderName,
        String vacancyFolderWebViewLink,
        LocalDateTime generatedAt
) {}

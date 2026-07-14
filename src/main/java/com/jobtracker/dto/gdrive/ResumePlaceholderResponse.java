package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "Generated resume from template placeholders")
public record ResumePlaceholderResponse(
        @Schema(description = "ID of the application this resume is associated with", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID applicationId,

        @Schema(description = "ID of the base resume used as a template", example = "123e4567-e89b-12d3-a456-426614174001")
        UUID baseResumeId,

        @Schema(description = "ID of the copied resume file in Google Drive", example = "1a2b3c4d5e6f7g8h9i0j")
        String copiedFileId,

        @Schema(description = "ID of the generated PDF file in Google Drive", example = "0j9i8h7g6f5e4d3c2b1a")
        String pdfFileId,

        @Schema(description = "URL to view the generated resume document in Google Drive", example = "https://drive.google.com/file/d/1a2b3c4d5e6f7g8h9i0j/view")
        String documentUrl,

        @Schema(description = "URL to download the generated resume PDF from Google Drive", example = "https://drive.google.com/uc?id=0j9i8h7g6f5e4d3c2b1a&export=download")
        String pdfUrl,

        @Schema(
                description = """
                        REQUIRED.
                        
                        Map of placeholder values.
                        
                        Keys must match the placeholder names returned by the placeholder detection endpoint.
                        
                        Do NOT include curly braces.
                        """,
                example = "{\"RESUMO\":\"Senior Java Developer\",\"SOFTSKILL1\":\"Communication\",\"HARDSKILL1\":\"Java\"}"
        )
        Map<String, String> values,

        @Schema(description = "List of placeholders that were replaced in the generated resume", example = "[\"RESUMO\", \"SOFTSKILL1\", \"HARDSKILL1\"]")
        List<String> placeholders,

        @Schema(description = "Timestamp when the resume was generated", example = "2024-06-01T12:34:56")
        LocalDateTime generatedAt,

        @Schema(description = "True once the document and PDF have both been generated and linked to the application", example = "true")
        boolean workflowCompleted
) {
}

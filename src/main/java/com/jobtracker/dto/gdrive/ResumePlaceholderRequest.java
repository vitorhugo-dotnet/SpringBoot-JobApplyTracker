package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

@Schema(description = "Request for resume placeholder detection or generation")
public record ResumePlaceholderRequest(
        @Schema(description = "Configured base resume identifier")
        @NotNull(message = "baseResumeId is required")
        UUID baseResumeId,

        @Schema(
                description = """
                        REQUIRED, placeholder values keyed by placeholder name without braces.
                        
                        Map of placeholder values.
                        
                        Keys must match the placeholder names returned by the placeholder detection endpoint.
                        
                        Do NOT include curly braces.
                        """,
                example = "{\"RESUMO\":\"Senior Java Developer\",\"SOFTSKILL1\":\"Communication\",\"HARDSKILL1\":\"Java\"}"
        )
        @NotNull(message = "values map is required")
        Map<String, String> values
) {}

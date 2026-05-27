package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Resume template placeholder detection result")
public record ResumePlaceholderDetectionResponse(
        UUID baseResumeId,
        List<String> placeholders
) {}

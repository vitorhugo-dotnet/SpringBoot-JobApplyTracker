package com.jobtracker.dto.export;

import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.entity.enums.ExportExecutionStatus;
import com.jobtracker.entity.enums.ExportFormat;
import com.jobtracker.entity.enums.ExportTrigger;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Result of one export attempt")
public record ExportExecutionResponse(
        UUID id,
        UUID scheduleId,
        String scheduleName,
        ExportTrigger trigger,
        ExportFormat format,
        ExportDestinationType destination,
        ExportExecutionStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer recordCount,
        @Schema(description = "True when the record limit capped the export")
        boolean truncated,
        String fileName,
        @Schema(description = "Link to the stored file, when the destination provides one")
        String fileUrl,
        @Schema(description = "Sanitized failure reason; never contains payloads or credentials")
        String errorMessage
) {}

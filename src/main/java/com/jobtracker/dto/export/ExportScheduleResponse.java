package com.jobtracker.dto.export;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.entity.enums.ExportFormat;
import com.jobtracker.entity.enums.ExportFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "A recurring export configuration")
public record ExportScheduleResponse(
        UUID id,
        String name,
        ExportFormat format,
        ExportFrequency frequency,
        @JsonFormat(pattern = "HH:mm")
        @Schema(type = "string", example = "20:00")
        LocalTime time,
        Integer dayOfWeek,
        Integer dayOfMonth,
        String timezone,
        boolean enabled,
        ExportDestinationType destination,
        ExportFilters filters,
        List<String> columns,
        @Schema(description = "Next execution instant in UTC")
        LocalDateTime nextRunAt,
        @Schema(description = "Last execution instant in UTC")
        LocalDateTime lastRunAt,
        @Schema(description = "Whether an execution is currently in progress")
        boolean running,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

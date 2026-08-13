package com.jobtracker.dto.export;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jobtracker.entity.enums.ExportDestinationType;
import com.jobtracker.entity.enums.ExportFormat;
import com.jobtracker.entity.enums.ExportFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/**
 * Recurring export configuration.
 *
 * <p>Deliberately not a cron expression: the recurrence is described in domain terms and the
 * schedule is derived internally, so a user can never store an invalid or abusive cron.
 */
@Schema(description = "Recurring export configuration")
public record ExportScheduleRequest(
        @NotBlank
        @Size(max = 120)
        @Schema(description = "Human-readable name", example = "Daily applications backup",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotNull
        @Schema(description = "Output format", example = "XLSX", requiredMode = Schema.RequiredMode.REQUIRED)
        ExportFormat format,

        @NotNull
        @Schema(description = "Recurrence", example = "DAILY", requiredMode = Schema.RequiredMode.REQUIRED)
        ExportFrequency frequency,

        @NotNull
        @JsonFormat(pattern = "HH:mm")
        @Schema(description = "Local time of day (HH:mm) in the configured timezone", example = "20:00",
                type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalTime time,

        @Min(1)
        @Max(7)
        @Schema(description = "ISO day of week (1 = Monday … 7 = Sunday). Required for WEEKLY.", example = "1")
        Integer dayOfWeek,

        @Min(1)
        @Max(28)
        @Schema(description = "Day of month (1–28). Required for MONTHLY.", example = "1")
        Integer dayOfMonth,

        @Schema(description = "IANA timezone; defaults to the server's configured export timezone",
                example = "America/Sao_Paulo")
        String timezone,

        @Schema(description = "Whether the schedule runs (defaults to true)", example = "true")
        Boolean enabled,

        @Schema(description = "Filters applied before exporting")
        ExportFilters filters,

        @Schema(description = "Column keys to include, in order. Empty means every column.")
        List<String> columns,

        @NotNull
        @Schema(description = "Where the generated file is delivered", example = "GOOGLE_DRIVE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ExportDestinationType destination
) {}

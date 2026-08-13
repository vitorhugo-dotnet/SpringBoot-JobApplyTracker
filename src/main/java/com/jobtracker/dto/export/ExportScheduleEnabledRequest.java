package com.jobtracker.dto.export;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Enable or disable a schedule")
public record ExportScheduleEnabledRequest(
        @NotNull
        @Schema(description = "New enabled state", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean enabled
) {}

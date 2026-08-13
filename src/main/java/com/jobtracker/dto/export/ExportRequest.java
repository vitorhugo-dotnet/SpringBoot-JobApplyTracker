package com.jobtracker.dto.export;

import com.jobtracker.entity.enums.ExportFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Manual export request")
public record ExportRequest(
        @NotNull
        @Schema(description = "Output format", example = "CSV", requiredMode = Schema.RequiredMode.REQUIRED)
        ExportFormat format,

        @Schema(description = "Filters applied before exporting")
        ExportFilters filters,

        @Schema(description = "Column keys to include, in order. Empty means every column.",
                example = "[\"id\",\"vacancyName\",\"organization\",\"status\"]")
        List<String> columns
) {}

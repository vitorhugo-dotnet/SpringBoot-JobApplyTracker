package com.jobtracker.dto.export;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An exportable column")
public record ExportColumnResponse(
        @Schema(description = "Key accepted in the 'columns' request field", example = "vacancyName")
        String key,
        @Schema(description = "Header written to the file", example = "Vacancy")
        String header
) {}

package com.jobtracker.dto.export;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated export history")
public record ExportExecutionPageResponse(
        List<ExportExecutionResponse> executions,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {}

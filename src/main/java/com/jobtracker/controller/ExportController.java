package com.jobtracker.controller;

import com.jobtracker.dto.export.ExportColumnResponse;
import com.jobtracker.dto.export.ExportExecutionPageResponse;
import com.jobtracker.dto.export.ExportExecutionResponse;
import com.jobtracker.dto.export.ExportRequest;
import com.jobtracker.entity.User;
import com.jobtracker.service.export.ApplicationExportService;
import com.jobtracker.service.export.ExportColumn;
import com.jobtracker.service.export.ExportExecutionService;
import com.jobtracker.service.export.ExportFile;
import com.jobtracker.util.SecurityUtils;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Exports", description = "Download job applications and inspect the export history")
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    /** Signals that the record ceiling capped the file, so the client can warn the user. */
    private static final String TRUNCATED_HEADER = "X-Export-Truncated";
    private static final String RECORD_COUNT_HEADER = "X-Export-Record-Count";

    private final ApplicationExportService applicationExportService;
    private final ExportExecutionService executionService;
    private final SecurityUtils securityUtils;

    public ExportController(ApplicationExportService applicationExportService,
                            ExportExecutionService executionService,
                            SecurityUtils securityUtils) {
        this.applicationExportService = applicationExportService;
        this.executionService = executionService;
        this.securityUtils = securityUtils;
    }

    @Operation(
            summary = "Export job applications",
            description = "Generates a CSV or XLSX file with the authenticated user's applications, "
                    + "honouring the same filters as the listing endpoint. The file is returned as an attachment.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Generated file",
                            content = @Content(mediaType = "application/octet-stream")),
                    @ApiResponse(responseCode = "400", description = "Invalid format, column or filter"),
                    @ApiResponse(responseCode = "429", description = "Too many export requests")
            }
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read:applications')")
    @PostMapping(value = "/applications", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @RateLimiter(name = "exportManual")
    public ResponseEntity<byte[]> exportApplications(@Valid @RequestBody ExportRequest request) {
        User user = securityUtils.getCurrentUser();
        LocalDateTime startedAt = LocalDateTime.now();

        ExportFile file;
        try {
            file = applicationExportService.export(user.getId(), request.format(), request.filters(), request.columns());
        } catch (RuntimeException e) {
            executionService.recordManualFailure(user, request.format(), startedAt, e);
            throw e;
        }
        executionService.recordManualSuccess(user, file, startedAt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(file.content().length);
        headers.add(RECORD_COUNT_HEADER, String.valueOf(file.recordCount()));
        headers.add(TRUNCATED_HEADER, String.valueOf(file.truncated()));
        // Content-Disposition and both headers above are exposed to cross-origin callers in CorsConfig.

        return ResponseEntity.ok().headers(headers).body(file.content());
    }

    @Operation(
            summary = "List exportable columns",
            description = "Returns every column key accepted in the 'columns' field, with its file header",
            responses = @ApiResponse(responseCode = "200", description = "Available columns")
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read:applications')")
    @GetMapping("/columns")
    public ResponseEntity<List<ExportColumnResponse>> listColumns() {
        return ResponseEntity.ok(ExportColumn.defaults().stream()
                .map(column -> new ExportColumnResponse(column.getKey(), column.getHeader()))
                .toList());
    }

    @Operation(
            summary = "Export history",
            description = "Paginated history of every export attempt made by the authenticated user",
            responses = @ApiResponse(responseCode = "200", description = "Page of executions",
                    content = @Content(schema = @Schema(implementation = ExportExecutionPageResponse.class)))
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read:applications')")
    @GetMapping("/history")
    public ResponseEntity<ExportExecutionPageResponse> history(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(executionService.history(securityUtils.getCurrentUserId(), page, size));
    }

    @Operation(
            summary = "Get one export execution",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Execution found",
                            content = @Content(schema = @Schema(implementation = ExportExecutionResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Execution not found")
            }
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read:applications')")
    @GetMapping("/history/{id}")
    public ResponseEntity<ExportExecutionResponse> getExecution(
            @Parameter(description = "Execution ID", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(executionService.get(securityUtils.getCurrentUserId(), id));
    }
}

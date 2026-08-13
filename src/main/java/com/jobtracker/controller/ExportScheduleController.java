package com.jobtracker.controller;

import com.jobtracker.dto.export.ExportExecutionResponse;
import com.jobtracker.dto.export.ExportScheduleEnabledRequest;
import com.jobtracker.dto.export.ExportScheduleRequest;
import com.jobtracker.dto.export.ExportScheduleResponse;
import com.jobtracker.service.export.ExportScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Export Schedules", description = "Recurring export configuration")
@RestController
@RequestMapping("/api/v1/export-schedules")
public class ExportScheduleController {

    private final ExportScheduleService exportScheduleService;

    public ExportScheduleController(ExportScheduleService exportScheduleService) {
        this.exportScheduleService = exportScheduleService;
    }

    @Operation(
            summary = "Create an export schedule",
            description = "Stores a validated recurrence (daily, weekly or monthly at a given local time). "
                    + "Cron expressions are never accepted from the client.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Schedule created",
                            content = @Content(schema = @Schema(implementation = ExportScheduleResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error")
            }
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write:applications')")
    @PostMapping
    public ResponseEntity<ExportScheduleResponse> create(@Valid @RequestBody ExportScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exportScheduleService.create(request));
    }

    @Operation(
            summary = "List export schedules",
            responses = @ApiResponse(responseCode = "200", description = "Schedules owned by the authenticated user")
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read:applications')")
    @GetMapping
    public ResponseEntity<List<ExportScheduleResponse>> list() {
        return ResponseEntity.ok(exportScheduleService.list());
    }

    @Operation(
            summary = "Get an export schedule",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Schedule found"),
                    @ApiResponse(responseCode = "404", description = "Schedule not found")
            }
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read:applications')")
    @GetMapping("/{id}")
    public ResponseEntity<ExportScheduleResponse> get(
            @Parameter(description = "Schedule ID", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(exportScheduleService.get(id));
    }

    @Operation(
            summary = "Update an export schedule",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Schedule updated"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "404", description = "Schedule not found")
            }
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write:applications')")
    @PutMapping("/{id}")
    public ResponseEntity<ExportScheduleResponse> update(
            @Parameter(description = "Schedule ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody ExportScheduleRequest request) {
        return ResponseEntity.ok(exportScheduleService.update(id, request));
    }

    @Operation(
            summary = "Enable or disable an export schedule",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Schedule updated"),
                    @ApiResponse(responseCode = "404", description = "Schedule not found")
            }
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write:applications')")
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ExportScheduleResponse> setEnabled(
            @Parameter(description = "Schedule ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody ExportScheduleEnabledRequest request) {
        return ResponseEntity.ok(exportScheduleService.setEnabled(id, request.enabled()));
    }

    @Operation(
            summary = "Delete an export schedule",
            description = "Past executions stay in the history",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Schedule deleted"),
                    @ApiResponse(responseCode = "404", description = "Schedule not found")
            }
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write:applications')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @Parameter(description = "Schedule ID", required = true) @PathVariable UUID id) {
        exportScheduleService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Export schedule deleted successfully"));
    }

    @Operation(
            summary = "Run an export schedule now",
            description = "Starts the schedule immediately and asynchronously. Returns 409 when an "
                    + "execution of the same schedule is already in progress.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Execution accepted",
                            content = @Content(schema = @Schema(implementation = ExportExecutionResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Schedule not found"),
                    @ApiResponse(responseCode = "409", description = "An execution is already running")
            }
    )
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write:applications')")
    @PostMapping("/{id}/run-now")
    public ResponseEntity<ExportExecutionResponse> runNow(
            @Parameter(description = "Schedule ID", required = true) @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(exportScheduleService.runNow(id));
    }
}

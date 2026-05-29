package com.jobtracker.controller;

import com.jobtracker.dto.application.ApplicationPageResponse;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.application.ApplicationResponse;
import com.jobtracker.dto.application.UpdateStatusRequest;
import com.jobtracker.dto.auth.UserResponse;
import com.jobtracker.dto.dashboard.DashboardSummaryResponse;
import com.jobtracker.dto.gdrive.BaseResumeContentResponse;
import com.jobtracker.dto.gdrive.BaseResumeResponse;
import com.jobtracker.dto.gdrive.GoogleDriveStatusResponse;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.service.ApplicationService;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.service.ResumeGenerationService;
import com.jobtracker.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "GPT Actions", description = "OAuth-protected endpoints tailored for GPT Actions")
@RestController
@RequestMapping("/api/v1/gpt")
@SecurityRequirement(name = "gptOAuth")
public class GptActionController {

    private final ApplicationService applicationService;
    private final DashboardService dashboardService;
    private final GoogleDriveService googleDriveService;
    private final ResumeGenerationService resumeGenerationService;
    private final AuthMapper authMapper;
    private final SecurityUtils securityUtils;

    public GptActionController(ApplicationService applicationService,
                               DashboardService dashboardService,
                               GoogleDriveService googleDriveService,
                               ResumeGenerationService resumeGenerationService,
                               AuthMapper authMapper,
                               SecurityUtils securityUtils) {
        this.applicationService = applicationService;
        this.dashboardService = dashboardService;
        this.googleDriveService = googleDriveService;
        this.resumeGenerationService = resumeGenerationService;
        this.authMapper = authMapper;
        this.securityUtils = securityUtils;
    }

    @Operation(summary = "Get the authenticated GPT user's profile")
    @PreAuthorize("hasAuthority('SCOPE_read:profile')")
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> profile() {
        return ResponseEntity.ok(authMapper.toUserResponse(securityUtils.getCurrentUser()));
    }

    @Operation(summary = "List the authenticated GPT user's applications")
    @PreAuthorize("hasAuthority('SCOPE_read:applications')")
    @GetMapping("/applications")
    public ResponseEntity<ApplicationPageResponse> applications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String recruiterName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationDateTo,
            @RequestParam(required = false) Boolean interviewScheduled,
            @RequestParam(required = false) Boolean recruiterDmReminderEnabled,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(applicationService.getAll(
                status,
                recruiterName,
                applicationDateFrom,
                applicationDateTo,
                interviewScheduled,
                recruiterDmReminderEnabled,
                archived,
                page,
                size,
                sort
        ));
    }

    @Operation(summary = "Get one application for the authenticated GPT user")
    @PreAuthorize("hasAuthority('SCOPE_read:applications')")
    @GetMapping("/applications/{id}")
    public ResponseEntity<ApplicationResponse> applicationById(@PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getById(id));
    }

    @Operation(summary = "Create a job application through GPT Actions")
    @PreAuthorize("hasAuthority('SCOPE_write:applications')")
    @PostMapping("/applications")
    public ResponseEntity<ApplicationResponse> createApplication(@Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(request));
    }

    @Operation(summary = "Update only the status of an application through GPT Actions")
    @PreAuthorize("hasAuthority('SCOPE_write:applications')")
    @PatchMapping("/applications/{id}/status")
    public ResponseEntity<ApplicationResponse> updateApplicationStatus(@PathVariable UUID id,
                                                                       @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(applicationService.updateStatus(id, request));
    }

    @Operation(summary = "List configured Google Drive base resumes for the authenticated GPT user")
    @PreAuthorize("hasAuthority('SCOPE_read:resume') and hasRole('BETA')")
    @GetMapping("/resumes/base")
    public ResponseEntity<List<BaseResumeResponse>> baseResumes() {
        return ResponseEntity.ok(googleDriveService.listBaseResumes());
    }

    @Operation(summary = "Get plain text content of a configured base resume for GPT use")
    @PreAuthorize("hasAuthority('SCOPE_read:resume') and hasRole('BETA')")
    @GetMapping("/resumes/base/{resumeId}/content")
    public ResponseEntity<BaseResumeContentResponse> baseResumeContent(
            @Parameter(description = "UUID of the base resume") @PathVariable UUID resumeId) {
        return ResponseEntity.ok(resumeGenerationService.getBaseResumeContent(resumeId));
    }

    @Operation(summary = "Get plain text content of a generated resume for GPT use")
    @PreAuthorize("hasAuthority('SCOPE_read:resume') and hasRole('BETA')")
    @GetMapping("/resumes/generated/{applicationId}/content")
    public ResponseEntity<ResumeGenerationService.GeneratedResumeContentResponse> generatedResumeContent(
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(resumeGenerationService.getGeneratedResumeContent(applicationId));
    }

    @Operation(summary = "Get Google Drive integration status for the authenticated GPT user")
    @PreAuthorize("hasAuthority('SCOPE_read:google-drive') and hasRole('BETA')")
    @GetMapping("/google-drive/status")
    public ResponseEntity<GoogleDriveStatusResponse> googleDriveStatus() {
        return ResponseEntity.ok(googleDriveService.getStatus());
    }

    @Operation(summary = "Get dashboard metrics for the authenticated GPT user")
    @PreAuthorize("hasAuthority('SCOPE_read:metrics')")
    @GetMapping("/metrics/summary")
    public ResponseEntity<DashboardSummaryResponse> metricsSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}

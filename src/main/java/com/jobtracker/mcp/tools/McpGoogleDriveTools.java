package com.jobtracker.mcp.tools;

import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyRequest;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderDetectionResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderRequest;
import com.jobtracker.dto.gdrive.ResumePlaceholderResponse;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService.DownloadedFile;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.service.ResumeGenerationService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for Google Drive resume automation.
 * Mirrors the @PreAuthorize("hasRole('BETA')") guard that GoogleDriveController applies at the
 * REST layer. Because these tools call GoogleDriveService directly (bypassing the controller),
 * the class-level @PreAuthorize replicates the same restriction so non-BETA users receive 403.
 */
@PreAuthorize("hasRole('BETA')")
@Component
public class McpGoogleDriveTools {

    private final GoogleDriveService googleDriveService;
    private final ResumeGenerationService resumeGenerationService;
    private final GoogleDriveGeneratedResumeDownloadService generatedResumeDownloadService;

    public McpGoogleDriveTools(GoogleDriveService googleDriveService,
                               ResumeGenerationService resumeGenerationService,
                               GoogleDriveGeneratedResumeDownloadService generatedResumeDownloadService) {
        this.googleDriveService = googleDriveService;
        this.resumeGenerationService = resumeGenerationService;
        this.generatedResumeDownloadService = generatedResumeDownloadService;
    }

    @McpTool(
            name = "Copy-Resume-To-Application",
            title = "Copy Resume To Application",
            description = "Copy a base resume template into an application folder and return the new Google Doc link.",
            annotations = @McpAnnotations(
                    title = "Copy Resume To Application",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = true))
    public GoogleDriveResumeCopyResponse copyResumeToApplication(
            @McpToolParam(required = true, description = "UUID of the job application") String applicationId,
            @McpToolParam(required = true, description = "UUID of the base resume template to copy") String baseResumeId) {
        return googleDriveService.copyBaseResumeToApplication(
                UUID.fromString(applicationId),
                new GoogleDriveResumeCopyRequest(UUID.fromString(baseResumeId)));
    }

    @McpTool(
            name = "Detect-Resume-Placeholders",
            title = "Detect Resume Placeholders",
            description = "Detect placeholder names present in a base resume template.",
            annotations = @McpAnnotations(
                    title = "Detect Resume Placeholders",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true))
    public ResumePlaceholderDetectionResponse detectResumePlaceholders(
            @McpToolParam(required = true, description = "UUID of the base resume (not the filename)") String baseResumeId) {
        return resumeGenerationService.detectPlaceholders(UUID.fromString(baseResumeId));
    }

    @McpTool(
            name = "Generate-Resume",
            title = "Generate Resume",
            description = "Generate a tailored resume by copying a template and replacing its placeholders.",
            annotations = @McpAnnotations(
                    title = "Generate Resume",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = true))
    public ResumePlaceholderResponse generateResume(
            @McpToolParam(required = true, description = "UUID of the job application") String applicationId,
            @McpToolParam(required = true, description = "UUID of the base resume template to use") String baseResumeId,
            @McpToolParam(required = true, description = "Placeholder values keyed by name without braces, e.g. {\"RESUMO\":\"...\"}")
            Map<String, String> values) {
        return resumeGenerationService.generateTemplateResume(
                UUID.fromString(applicationId),
                new ResumePlaceholderRequest(UUID.fromString(baseResumeId), values));
    }

    @McpTool(
            name = "Download-Generated-Resume-PDF",
            title = "Download Generated Resume PDF",
            description = "Download the generated resume PDF for an application as Base64-encoded content.",
            annotations = @McpAnnotations(
                    title = "Download Generated Resume PDF",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true))
    public GeneratedResumePdf downloadGeneratedResumePdf(
            @McpToolParam(required = true, description = "UUID of the job application") String applicationId) {
        DownloadedFile file = generatedResumeDownloadService.downloadAsPdf(UUID.fromString(applicationId));
        return new GeneratedResumePdf(
                file.fileName(),
                file.contentType(),
                Base64.getEncoder().encodeToString(file.content()));
    }

    /** Base64-encoded PDF payload returned by {@link #downloadGeneratedResumePdf(String)}. */
    public record GeneratedResumePdf(String fileName, String contentType, String base64Content) {}
}

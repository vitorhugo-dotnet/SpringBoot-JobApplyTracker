package com.jobtracker.mcp.tools;

import com.jobtracker.dto.gdrive.BaseResumeResponse;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyRequest;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderDetectionResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderRequest;
import com.jobtracker.dto.gdrive.ResumePlaceholderResponse;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService.DownloadedFile;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.service.ResumeGenerationService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
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
            name = "List-Base-Resumes",
            title = "List Base Resumes",
            description = """
                    List all base resume templates configured by the current user.

                    Each entry contains:
                    - id (UUID): the baseResumeId required by Copy-Resume-To-Application, Generate-Resume, \
                    and Detect-Resume-Placeholders. Never use a Google Drive fileId here.
                    - name: display name of the document (e.g. "BASE - CV - Vitor Hugo PT-BR").
                    - language: language code of the resume (e.g. "PT", "EN"). Use to select the correct \
                    template for the vacancy language (PT → PT-BR template, EN → EN-US template).
                    - template: true if this is a reusable placeholder template.
                    - readOnly: true if this is a read-only PDF resume (cannot be used for template \
                    generation, placeholder detection, or copying to applications — only content \
                    reading is supported).
                    - createdAt: registration timestamp.

                    Call this tool before any resume operation to obtain a valid baseResumeId.""",
            annotations = @McpAnnotations(
                    title = "List Base Resumes",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    @AuditMcpOperation(action = "List-Base-Resumes")
    public List<BaseResumeResponse> listBaseResumes(McpSyncRequestContext ctx) {
        return googleDriveService.listBaseResumes();
    }

    @McpTool(
            name = "Copy-Resume-To-Application",
            title = "Copy Resume To Application",
            description = """
                    Copy a base resume template into the application's Google Drive folder and return \
                    the new Google Doc link and folder details.

                    Use List-Base-Resumes to obtain a valid baseResumeId before calling this tool. \
                    The baseResumeId is a Job Apply Tracker UUID — it is NOT a Google Drive file ID. \
                    The application folder is created automatically inside the configured Drive root folder \
                    if it does not yet exist.

                    This is a low-level, advanced-use tool. For the normal "generate a tailored resume for \
                    this application" request, call Generate-Application-Resume instead — it copies, fills \
                    placeholders, and exports the PDF in one atomic call. Do NOT fall back to local DOCX/PDF \
                    generation, filesystem, or non-MCP tools for job-related resumes.""",
            annotations = @McpAnnotations(
                    title = "Copy Resume To Application",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Copy-Resume-To-Application")
    public GoogleDriveResumeCopyResponse copyResumeToApplication(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the job application (from Create-Application or List-Applications)") String applicationId,
            @McpToolParam(required = true, description = "UUID of the base resume template — obtain from List-Base-Resumes, NOT a Google Drive file ID") String baseResumeId) {
        return googleDriveService.copyBaseResumeToApplication(
                UUID.fromString(applicationId),
                new GoogleDriveResumeCopyRequest(UUID.fromString(baseResumeId)));
    }

    @McpTool(
            name = "Detect-Resume-Placeholders",
            title = "Detect Resume Placeholders",
            description = """
                    Scan a base resume template and return the list of placeholder names found inside \
                    double curly braces (e.g. {{RESUMO}}, {{STACK}}).

                    Always call this before Generate-Resume so you know which keys to supply. \
                    Use List-Base-Resumes to obtain the baseResumeId. \
                    The returned placeholder names must be used without braces as keys in Generate-Resume.""",
            annotations = @McpAnnotations(
                    title = "Detect Resume Placeholders",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Detect-Resume-Placeholders")
    public ResumePlaceholderDetectionResponse detectResumePlaceholders(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the base resume template — obtain from List-Base-Resumes") String baseResumeId) {
        return resumeGenerationService.detectPlaceholders(UUID.fromString(baseResumeId));
    }

    @McpTool(
            name = "Generate-Resume",
            title = "Generate Resume",
            description = """
                    Copy a base resume template into the application's Drive folder, replace all \
                    placeholders with the supplied values, export a PDF, and return links to both \
                    the Google Doc and the PDF.

                    This is a low-level, advanced-use tool for callers that already ran List-Base-Resumes and \
                    Detect-Resume-Placeholders themselves (e.g. to inspect placeholders before submitting values). \
                    For the normal case, call Generate-Application-Resume instead — it selects the template, \
                    detects and validates placeholders, and generates the resume in a single atomic call.

                    Prerequisites (call in order before this tool):
                    1. List-Base-Information + Get-Base-Information-Content — you MUST read the candidate's base \
                       information first. It is the authoritative source of truth about the candidate; never generate \
                       values from the template/vacancy alone and never invent experience, skills, or projects.
                    2. List-Base-Resumes — obtain the baseResumeId for the vacancy language.
                    3. Detect-Resume-Placeholders — retrieve the exact placeholder key names.
                    4. Provide a value for every detected placeholder key (keys without braces, \
                       e.g. "RESUMO", "STACK", "PROJETO_1").

                    Do NOT call this tool before Create-Application — you need the applicationId first. \
                    Local DOCX/PDF/filesystem generation is never a valid substitute for this workflow.""",
            annotations = @McpAnnotations(
                    title = "Generate Resume",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Generate-Resume")
    public ResumePlaceholderResponse generateResume(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the job application — must already exist (from Create-Application)") String applicationId,
            @McpToolParam(required = true, description = "UUID of the base resume template — obtain from List-Base-Resumes, NOT a Google Drive file ID") String baseResumeId,
            @McpToolParam(required = true, description = "Placeholder values keyed by the exact names returned by Detect-Resume-Placeholders (without braces), e.g. {\"RESUMO\":\"...\", \"STACK\":\"Java, Spring Boot\"}")
            Map<String, String> values) {
        return resumeGenerationService.generateTemplateResume(
                UUID.fromString(applicationId),
                new ResumePlaceholderRequest(UUID.fromString(baseResumeId), values));
    }

    @McpTool(
            name = "Generate-Application-Resume",
            title = "Generate Application Resume",
            description = """
                    MANDATORY entry point for "generate/tailor a resume for this application" requests. \
                    Runs the full resume workflow atomically on the backend: selects the base resume template, \
                    detects placeholders, validates every placeholder has a value, copies the template, replaces \
                    placeholders, exports the PDF, and links both to the application — in one call.

                    You MUST use this tool (or the low-level Copy-Resume-To-Application / \
                    Detect-Resume-Placeholders / Generate-Resume sequence) for every job-related CV/resume request. \
                    Never fall back to generic document, filesystem, Python, DOCX, PDF, or non-MCP Google Drive \
                    tools to produce a resume — a request is not complete unless it returns a Google Doc \
                    (documentUrl) and PDF (pdfUrl) linked to the application via this MCP server.

                    Prerequisites before calling this tool:
                    1. Create-Application — the application must already exist.
                    2. List-Base-Information + Get-Base-Information-Content — read the candidate's base information \
                       first; it is the authoritative source of truth. Never invent experience, skills, or projects.

                    Template selection: pass baseResumeId (from List-Base-Resumes) to use a specific template, \
                    or pass language (e.g. "PT", "EN") to let the backend pick the single matching reusable \
                    template automatically. If zero or more than one template matches the language, the call \
                    fails with a clear error asking you to pass baseResumeId explicitly — it never guesses.

                    Placeholder values: supply a value for every placeholder in the template (keys without \
                    braces). The call fails with a clear error listing any missing placeholder instead of \
                    generating an incomplete resume. Read-only PDF resumes and mismatched applications also \
                    fail with clear errors.

                    Calling this tool again for the same application regenerates the resume: a new Google Doc \
                    and PDF are created in the application's Drive folder and become the new linked resume; \
                    the previous files remain in Drive but are no longer referenced by the application.

                    On success the response always includes documentUrl, pdfUrl, baseResumeId (the template \
                    used), and workflowCompleted: true.""",
            annotations = @McpAnnotations(
                    title = "Generate Application Resume",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Generate-Application-Resume")
    public ResumePlaceholderResponse generateApplicationResume(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the job application — must already exist (from Create-Application)") String applicationId,
            @McpToolParam(required = false, description = "UUID of the base resume template — obtain from List-Base-Resumes, NOT a Google Drive file ID. Omit to select automatically by language.") String baseResumeId,
            @McpToolParam(required = false, description = "Vacancy language code (e.g. \"PT\", \"EN\") used to auto-select the template when baseResumeId is omitted. Ignored if baseResumeId is provided.") String language,
            @McpToolParam(required = true, description = "Placeholder values keyed by the exact names returned by Detect-Resume-Placeholders (without braces), e.g. {\"RESUMO\":\"...\", \"STACK\":\"Java, Spring Boot\"}. Every detected placeholder must have a value.")
            Map<String, String> values) {
        return resumeGenerationService.generateApplicationResume(
                UUID.fromString(applicationId),
                baseResumeId == null ? null : UUID.fromString(baseResumeId),
                language,
                values);
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
    @AuditMcpOperation(action = "Download-Generated-Resume-PDF")
    public GeneratedResumePdf downloadGeneratedResumePdf(
            McpSyncRequestContext ctx,
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

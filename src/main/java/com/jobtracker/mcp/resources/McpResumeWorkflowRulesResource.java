package com.jobtracker.mcp.resources;

import com.jobtracker.mcp.McpResourcesConfig;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.stereotype.Service;

@Service
public class McpResumeWorkflowRulesResource {

    private static final String LAST_MODIFIED = "2026-06-04";

    @McpResource(
            uri = McpResourcesConfig.URI_RESUME_WORKFLOW_RULES,
            name = "Resume Workflow Rules",
            title = "Resume Workflow Rules",
            description = "Markdown instructions for the resume tailoring workflow.",
            mimeType = "text/markdown",
            annotations = @McpAnnotations(
                    audience = {Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 1.0d))
    public String resumeWorkflowRules() {
        return """
                # Resume Workflow Rules

                Execute these steps in order:

                1. List-Applications — find the newest application with a non-empty driveResumeFileId.
                2. Read resource://job-apply-tracker/generated-resume/{applicationId} — use that resume as the real CV source.
                3. Call List-Base-Resumes — pick the template by vacancy language (PT → PT-BR template, EN → EN-US template).
                   Alternatively read resource://job-apply-tracker/base-resumes if the tool is unavailable.
                4. Detect-Resume-Placeholders — call this before Generate-Resume every time.
                5. Ask only for missing information that is required to fill placeholders or log the application.
                6. Create-Application — save the application before generating the tailored resume.
                7. Generate-Resume — provide every detected placeholder value.

                Rules:
                - Never use a base resume template as the data source.
                - Never invent experience, technologies, projects, or certifications.
                - Placeholder keys must match Detect-Resume-Placeholders exactly, without curly braces.
                - If no prior generated resume exists, ask the user for their CV text because this MCP
                  setup does not expose generic Drive search or file-read tools.
                - If Generate-Resume fails, keep the application record and return the placeholder values.

                ID disambiguation:
                - baseResumeId: a Job Apply Tracker UUID returned by List-Base-Resumes or
                  resource://job-apply-tracker/base-resumes. This is NOT a Google Drive fileId.
                - Google Drive fileId: a Drive string (e.g. "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms").
                  These two IDs are not interchangeable — always use the Job Apply Tracker UUID.
                """;
    }
}

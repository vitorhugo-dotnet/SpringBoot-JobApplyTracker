package com.jobtracker.mcp;

import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

/**
 * Registers MCP resources for reusable reference data.
 */
@Service
public class McpResourcesConfig {

    static final String URI_APPLICATION_CREATION_RULES = "resource://job-tracker/application-creation-rules";
    static final String URI_RESUME_WORKFLOW_RULES = "resource://job-tracker/resume-workflow-rules";
    static final String URI_APPLICATION_STATUSES = "resource://job-tracker/application-statuses";

    @McpResource(uri = URI_APPLICATION_CREATION_RULES, mimeType = "text/plain")
    public String applicationCreationRules() {
        return """
                # Application Creation Rules

                Apply on every createApplication or updateApplication call:

                - applicationDate: always TODAY's date (yyyy-MM-dd). Never the vacancy's posting date.
                - nextStepDateTime: do NOT auto-fill. Set only when the user explicitly provides a date/time.
                - status: omit (null) for a fresh cold-outreach. Use "RH" only when already in process.
                - recruiterDmReminderEnabled: true only when a recruiter email or contact exists.
                - rhAcceptedConnection: false unless the LinkedIn connection is confirmed accepted.
                - interviewScheduled: false unless an interview is confirmed.
                """;
    }

    @McpResource(uri = URI_RESUME_WORKFLOW_RULES, mimeType = "text/plain")
    public String resumeWorkflowRules() {
        return """
                # Resume Generation Rules

                Execute these steps in order — never skip or reorder:

                1. listBaseResumes — pick template by vacancy language (PT→PT-BR, EN→EN-US).
                2. detectResumePlaceholders(baseResumeId) — call before generateResume every time.
                   Never assume which placeholders exist; always detect them fresh.
                3. Read the user's real CV from Google Drive (a previously generated resume or the master CV file).
                   NEVER use a base resume template as the data source — templates contain placeholders, not facts.
                4. Generate a value for every detected placeholder. Zero omissions allowed.
                   Keys must match exactly the names from detectResumePlaceholders (no curly braces).
                5. generateResume(applicationId, baseResumeId, values) — only after steps 1–4 are complete.

                Error handling: if generateResume fails, keep the application record and return the
                placeholder values to the user for manual substitution.

                ID disambiguation:
                - baseResumeId: a Job Apply Tracker UUID (from listBaseResumes).
                - Google Drive fileId: a Drive string (from the Drive API). These are NOT interchangeable.
                """;
    }

    @McpResource(uri = URI_APPLICATION_STATUSES, mimeType = "text/plain")
    public String applicationStatuses() {
        return """
                # Valid Application Status Values

                Use the exact display names below (case-sensitive):

                  RH
                  Entrevista marcada
                  Fiz a RH - Aguardando Atualização
                  Fiz a Hiring Manager - Aguardando Atualização
                  Teste Técnico
                  Fiz teste Técnico - aguardando atualização
                  RH (Negociação)
                  Rejeitado
                  Tarde demais
                  Ghosting

                Omit status (pass null) when logging a fresh cold-outreach application.
                """;
    }
}

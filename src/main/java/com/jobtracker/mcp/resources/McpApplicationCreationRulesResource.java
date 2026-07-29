package com.jobtracker.mcp.resources;

import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.stereotype.Service;

@Service
public class McpApplicationCreationRulesResource {

    private static final String LAST_MODIFIED = "2026-07-29";

    @McpResource(
            uri = McpResourcesConfig.URI_APPLICATION_CREATION_RULES,
            name = "Application Creation Rules",
            title = "Application Creation Rules",
            description = "Markdown defaults and invariants for creating or updating applications.",
            mimeType = "text/markdown",
            annotations = @McpAnnotations(
                    audience = {Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 1.0d))
    @AuditMcpOperation(action = "Application Creation Rules")
    public String applicationCreationRules(McpSyncServerExchange exchange) {
        return """
                # Application Creation Rules

                MANDATORY REGISTRATION RULE: creation is mandatory for all application-related actions —
                generating or adapting a resume, drafting an email/WhatsApp/LinkedIn message, contacting a
                recruiter, evaluating compatibility with a vacancy, or preparing any application materials.
                Whenever the user provides a vacancy and shows application intent, register it before
                performing the requested action. Never silently skip vacancy registration.

                MANDATORY DUPLICATE CHECK: you MUST search before creating any application.
                Extract every available identifier: vacancy URL, vacancy title, organization, and recruiter.
                Search active applications separately using the available title, organization, and recruiter terms.
                Then search archived applications as well; archived records MUST participate in duplicate detection.
                Inspect all returned records and compare every available identifier, including vacancyLink when present.

                An empty search result is not sufficient when only one weak, incomplete, or unrelated query was executed.
                Do not rely only on vacancyLink because it is optional. Run multiple searches using the available identifiers.

                Treat a matching record as a confirmed duplicate when the URL matches or when title, organization,
                and recruiter identify the same vacancy with high confidence. Reuse the existing record and do not call
                Create-Application.
                Treat a matching record as a possible duplicate when title and organization match but other information
                is missing, different, or inconclusive. Show the matching records and ask the user to confirm whether the
                vacancy is distinct.

                Do not call Create-Application until searches of active and archived applications are complete and no
                confirmed or possible duplicate remains unresolved. When the matching record is archived, identify it as
                archived and prefer Restore-Application when the user wants to continue the same application record.

                Registration must happen before resume or outreach generation: call Create-Application (after
                List-Statuses) first, then perform the requested resume, message, evaluation, or outreach
                action only once the application exists.

                Always confirm to the user, explicitly, that the vacancy was registered (or that an existing record was
                reused) before or alongside delivering the requested output.

                Apply these defaults on every Create-Application or Update-Application call:

                - applicationDate: always today's date in yyyy-MM-dd format. Never the vacancy posting date.
                - nextStepDateTime: do not auto-fill. Set only when the user explicitly provides it.
                - status: omit (null) for a fresh cold outreach. Use "RH" only when already in process.
                - recruiterDmReminderEnabled: true only when a recruiter email or contact exists.
                - rhAcceptedConnection: false unless the LinkedIn connection is confirmed accepted.
                - interviewScheduled: false unless an interview is confirmed.
                """;
    }
}

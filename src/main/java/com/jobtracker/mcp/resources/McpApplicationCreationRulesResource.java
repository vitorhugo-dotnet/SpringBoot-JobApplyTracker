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

    private static final String LAST_MODIFIED = "2026-07-15";

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

                Duplicate detection must prioritize the exact vacancy URL: search existing applications by
                the exact vacancy URL first. Similar vacancy names, recruiters, organizations, salaries, or
                technology stacks are not sufficient evidence of a duplicate — treat the vacancy as a
                duplicate only when the URL is identical or the user explicitly confirms it is the same
                vacancy. Reposts or new vacancy URLs must be registered as separate applications, even when
                title, recruiter, salary, and stack are identical to an existing application.

                Registration must happen before resume or outreach generation: call Create-Application (after
                List-Statuses) first, then perform the requested resume, message, evaluation, or outreach
                action only once the application exists.

                Always confirm to the user, explicitly, that the vacancy was registered (or that it was
                already registered under the same URL) before or alongside delivering the requested output.

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

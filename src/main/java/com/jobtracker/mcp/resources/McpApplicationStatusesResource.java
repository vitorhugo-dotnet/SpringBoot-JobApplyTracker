package com.jobtracker.mcp.resources;

import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.mcp.McpResourcesConfig;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.stereotype.Service;

@Service
public class McpApplicationStatusesResource {

    private static final String LAST_MODIFIED = "2026-06-04";

    @McpResource(
            uri = McpResourcesConfig.URI_APPLICATION_STATUSES,
            name = "Application Statuses",
            title = "Application Statuses",
            description = "Markdown catalog of valid application statuses and their meanings.",
            mimeType = "text/markdown",
            annotations = @McpAnnotations(
                    audience = {Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.9d))
    public String applicationStatuses() {
        StringBuilder text = new StringBuilder("""
                # Valid Application Status Values

                Use the exact display names below (case-sensitive):

                """);

        for (ApplicationStatus status : ApplicationStatus.values()) {
            text.append("- ")
                    .append(status.getDisplayName())
                    .append(" — ")
                    .append(status.getDescription())
                    .append('\n');
        }

        text.append("\nOmit status (pass null) when logging a fresh cold outreach application.\n");
        return text.toString();
    }
}

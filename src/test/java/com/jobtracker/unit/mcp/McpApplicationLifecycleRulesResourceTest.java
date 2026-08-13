package com.jobtracker.unit.mcp;

import com.jobtracker.mcp.resources.McpApplicationLifecycleRulesResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpApplicationLifecycleRulesResourceTest {

    private final McpApplicationLifecycleRulesResource resource =
            new McpApplicationLifecycleRulesResource();

    @Test
    void applicationLifecycleRules_separateStatusFromArchiveAndRequireLeastDestructiveMutation() {
        String text = resource.applicationLifecycleRules(null);

        assertThat(text)
                .contains("different concepts")
                .contains("Rejected")
                .contains("MUST NOT archive")
                .contains("dar baixa")
                .contains("soft-delete")
                .contains("least destructive mutation")
                .contains("Restore-Application")
                .contains("permanent deletion")
                .contains("Do not delete and recreate");
    }

    @Test
    void applicationLifecycleRules_documentPatchApplicationAsThePartialUpdateTool() {
        String text = resource.applicationLifecycleRules(null);

        assertThat(text)
                .contains("Patch-Application")
                .contains("archived = false")
                .contains("only the fields you send");
    }
}

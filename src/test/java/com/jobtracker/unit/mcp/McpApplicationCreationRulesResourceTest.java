package com.jobtracker.unit.mcp;

import com.jobtracker.mcp.resources.McpApplicationCreationRulesResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpApplicationCreationRulesResourceTest {

    private final McpApplicationCreationRulesResource resource = new McpApplicationCreationRulesResource();

    @Test
    void applicationCreationRules_mandatesRegistrationForEveryApplicationRelatedAction() {
        String text = resource.applicationCreationRules(null);

        assertThat(text)
                .contains("mandatory for all application-related actions")
                .contains("exact vacancy URL")
                .contains("are not sufficient evidence of a duplicate")
                .contains("Reposts or new vacancy URLs must be registered as separate applications")
                .contains("before resume or outreach generation")
                .contains("confirm");
    }
}

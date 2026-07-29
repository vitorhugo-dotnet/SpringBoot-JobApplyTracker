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
                .containsIgnoringCase("must search before creating")
                .containsIgnoringCase("active and archived")
                .containsIgnoringCase("vacancy URL")
                .containsIgnoringCase("vacancy title")
                .containsIgnoringCase("organization")
                .containsIgnoringCase("recruiter")
                .containsIgnoringCase("possible duplicate")
                .containsIgnoringCase("an empty search result is not sufficient")
                .containsIgnoringCase("do not call Create-Application until");

        assertThat(text)
                .doesNotContain("duplicate only when the URL is identical")
                .doesNotContain("Reposts or new vacancy URLs must be registered as separate applications");
    }
}

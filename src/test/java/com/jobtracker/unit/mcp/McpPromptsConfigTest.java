package com.jobtracker.unit.mcp;

import com.jobtracker.mcp.McpPromptsConfig;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpPromptsConfigTest {

    private final McpPromptsConfig prompts = new McpPromptsConfig();

    @Test
    void intakeVacancyPrompt_enforcesMandatoryUrlBasedRegistrationWorkflow() {
        GetPromptResult result = prompts.intakeVacancyPrompt("Senior Java Backend - Julia Argueiro - https://example.com/vaga/123");

        String text = ((TextContent) result.messages().get(0).content()).text();

        assertThat(text)
                .contains("List-Statuses → Search by exact vacancy URL → Create-Application when absent → Continue requested workflow")
                .contains("Call List-Statuses")
                .contains("exact vacancy URL")
                .contains("Similar vacancy names, recruiters, organizations, salaries, or technology stacks are NOT sufficient")
                .contains("resume, message, evaluation, or outreach");
    }

    @Test
    void everyPromptHasAMatchingCompletionHandler() {
        Set<String> promptNames = promptMethods().stream()
                .map(method -> method.getAnnotation(McpPrompt.class).name())
                .collect(java.util.stream.Collectors.toSet());

        Set<String> completionNames = java.util.Arrays.stream(McpPromptsConfig.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpComplete.class))
                .map(method -> method.getAnnotation(McpComplete.class).prompt())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(completionNames).containsAll(promptNames);
    }

    @Test
    void promptMetadataIsFullyNamed() {
        promptMethods().forEach(method -> {
            McpPrompt prompt = method.getAnnotation(McpPrompt.class);
            assertThat(prompt.name()).isNotBlank();
            assertThat(prompt.title()).isNotBlank();
            assertThat(prompt.description()).isNotBlank();
        });
    }

    private static Set<Method> promptMethods() {
        return java.util.Arrays.stream(McpPromptsConfig.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpPrompt.class))
                .collect(java.util.stream.Collectors.toSet());
    }
}

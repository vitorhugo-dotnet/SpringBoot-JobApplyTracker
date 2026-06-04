package com.jobtracker.unit.mcp;

import com.jobtracker.mcp.McpPromptsConfig;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpPrompt;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpPromptsConfigTest {

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

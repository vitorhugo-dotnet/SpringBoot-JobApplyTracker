package com.jobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class JobTrackerApplicationTests {

    @Autowired
    private ToolExecutionExceptionProcessor toolExecutionExceptionProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void toolExecutionErrorsAreJsonSafeForGemini() throws Exception {
        ToolDefinition toolDefinition = mock(ToolDefinition.class);
        when(toolDefinition.name()).thenReturn("searchApplications");
        ToolExecutionException exception = new ToolExecutionException(
                toolDefinition, new IllegalArgumentException("query is required"));

        String response = toolExecutionExceptionProcessor.process(exception);
        JsonNode json = objectMapper.readTree(response);

        assertThat(json.isObject()).isTrue();
        assertThat(json.path("error").asText()).contains("query is required");
    }
}

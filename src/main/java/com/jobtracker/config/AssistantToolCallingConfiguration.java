package com.jobtracker.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class AssistantToolCallingConfiguration {

    @Bean
    ToolExecutionExceptionProcessor jsonSafeToolExecutionExceptionProcessor(
            ObjectMapper objectMapper,
            @Value("${spring.ai.tools.throw-exception-on-error:false}") boolean alwaysThrow) {
        DefaultToolExecutionExceptionProcessor delegate = DefaultToolExecutionExceptionProcessor.builder()
                .alwaysThrow(alwaysThrow)
                .rethrowExceptions(defaultRethrownExceptions())
                .build();

        return exception -> {
            String message = delegate.process(exception);
            try {
                return objectMapper.writeValueAsString(Map.of("error", message));
            } catch (JsonProcessingException serializationFailure) {
                return "{\"error\":\"Tool execution failed\"}";
            }
        };
    }

    private List<Class<? extends RuntimeException>> defaultRethrownExceptions() {
        List<Class<? extends RuntimeException>> exceptions = new ArrayList<>();
        Class<? extends RuntimeException> oauth2Exception = runtimeExceptionClassOrNull(
                "org.springframework.security.oauth2.client.ClientAuthorizationException");
        if (oauth2Exception != null) {
            exceptions.add(oauth2Exception);
        }
        return exceptions;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends RuntimeException> runtimeExceptionClassOrNull(String className) {
        try {
            Class<?> type = ClassUtils.forName(className, null);
            if (RuntimeException.class.isAssignableFrom(type)) {
                return (Class<? extends RuntimeException>) type;
            }
        } catch (ClassNotFoundException ignored) {
            // Match Spring AI's optional OAuth2 exception handling without requiring the dependency.
        }
        return null;
    }
}

package com.jobtracker.dto.assistant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantErrorPayload(
        String code,
        String message,
        Integer retryAfterSeconds
) {}

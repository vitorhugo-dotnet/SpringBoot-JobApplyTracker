package com.jobtracker.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 4000, message = "Message must have at most 4000 characters")
        String message
) {}

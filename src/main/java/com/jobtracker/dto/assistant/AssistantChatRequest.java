package com.jobtracker.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssistantChatRequest(
        @NotNull(message = "Conversation ID is required")
        UUID conversationId,
        @NotBlank(message = "Message is required")
        @Size(max = 4000, message = "Message must have at most 4000 characters")
        String message
) {}

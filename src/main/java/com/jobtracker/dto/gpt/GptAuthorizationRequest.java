package com.jobtracker.dto.gpt;

import jakarta.validation.constraints.NotBlank;

public record GptAuthorizationRequest(
        @NotBlank(message = "response_type is required")
        String response_type,
        @NotBlank(message = "client_id is required")
        String client_id,
        @NotBlank(message = "redirect_uri is required")
        String redirect_uri,
        String scope,
        String state,
        String code_challenge,
        String code_challenge_method
) {
}

package com.jobtracker.dto.auth;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PasskeyVerifyRequest(
        @NotNull UUID challengeId,
        @NotNull JsonNode credential
) {
}

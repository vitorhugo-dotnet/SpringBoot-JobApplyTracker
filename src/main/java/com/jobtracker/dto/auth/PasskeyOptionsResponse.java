package com.jobtracker.dto.auth;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record PasskeyOptionsResponse(
        boolean passkeyAvailable,
        UUID challengeId,
        JsonNode publicKey
) {
}

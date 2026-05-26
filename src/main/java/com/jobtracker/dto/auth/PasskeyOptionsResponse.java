package com.jobtracker.dto.auth;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record PasskeyOptionsResponse(
        boolean passkeyAvailable,
        UUID challengeId,
        JsonNode publicKey
) {
}

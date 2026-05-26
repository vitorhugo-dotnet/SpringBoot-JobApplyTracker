package com.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.webauthn")
public record WebAuthnProperties(
        String rpId,
        String rpName,
        List<String> origins,
        long challengeTimeoutSeconds
) {
}

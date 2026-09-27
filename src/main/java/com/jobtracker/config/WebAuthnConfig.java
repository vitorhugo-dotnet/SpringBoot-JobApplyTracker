package com.jobtracker.config;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(WebAuthnProperties.class)
public class WebAuthnConfig {

    @Bean
    public RelyingParty relyingParty(WebAuthnProperties properties, CredentialRepository credentialRepository) {
        validateRpIdAgainstOrigins(properties);

        RelyingPartyIdentity identity = RelyingPartyIdentity.builder()
                .id(properties.rpId())
                .name(properties.rpName())
                .build();

        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(credentialRepository)
                .origins(Set.copyOf(properties.origins()))
                .build();
    }

    private static void validateRpIdAgainstOrigins(WebAuthnProperties properties) {
        String rpId = properties.rpId().toLowerCase(Locale.ROOT);

        for (String origin : properties.origins()) {
            URI originUri;
            try {
                originUri = URI.create(origin);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid WebAuthn origin: " + origin, exception);
            }

            String host = originUri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("Invalid WebAuthn origin: " + origin);
            }

            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!normalizedHost.equals(rpId) && !normalizedHost.endsWith("." + rpId)) {
                throw new IllegalArgumentException(
                        "WebAuthn RP ID '" + properties.rpId()
                                + "' is not valid for origin '" + origin + "'"
                );
            }
        }
    }
}

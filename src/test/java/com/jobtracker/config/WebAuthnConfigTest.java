package com.jobtracker.config;

import com.yubico.webauthn.CredentialRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebAuthnConfigTest {

    private final WebAuthnConfig config = new WebAuthnConfig();
    private final CredentialRepository credentialRepository = mock(CredentialRepository.class);

    @Test
    void relyingParty_acceptsProductionRpIdForApplywellOrigin() {
        WebAuthnProperties properties = new WebAuthnProperties(
                "applywell.hugojava.dev",
                "ApplyWell",
                List.of("https://applywell.hugojava.dev"),
                300
        );

        assertDoesNotThrow(() -> config.relyingParty(properties, credentialRepository));
    }

    @Test
    void relyingParty_rejectsRpIdThatIsNotTheOriginHostOrDomainSuffix() {
        WebAuthnProperties properties = new WebAuthnProperties(
                "jobapply-api.hugojava.dev",
                "ApplyWell",
                List.of("https://applywell.hugojava.dev"),
                300
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> config.relyingParty(properties, credentialRepository)
        );

        assertTrue(error.getMessage().contains("jobapply-api.hugojava.dev"));
        assertTrue(error.getMessage().contains("https://applywell.hugojava.dev"));
    }
}

package com.jobtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.config.WebAuthnProperties;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.MessageResponse;
import com.jobtracker.dto.auth.PasskeyLoginOptionsRequest;
import com.jobtracker.dto.auth.PasskeyOptionsResponse;
import com.jobtracker.dto.auth.PasskeyStatusResponse;
import com.jobtracker.dto.auth.PasskeyVerifyRequest;
import com.jobtracker.entity.User;
import com.jobtracker.entity.WebAuthnChallenge;
import com.jobtracker.entity.WebAuthnCredential;
import com.jobtracker.entity.enums.WebAuthnChallengeType;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.UnauthorizedException;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.repository.WebAuthnChallengeRepository;
import com.jobtracker.repository.WebAuthnCredentialRepository;
import com.jobtracker.util.SecurityUtils;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PasskeyAuthService {

    private final RelyingParty relyingParty;
    private final ObjectMapper objectMapper;
    private final WebAuthnProperties webAuthnProperties;
    private final WebAuthnChallengeRepository webAuthnChallengeRepository;
    private final WebAuthnCredentialRepository webAuthnCredentialRepository;
    private final SecurityUtils securityUtils;
    private final AuthService authService;
    private final UserRepository userRepository;

    public PasskeyAuthService(RelyingParty relyingParty,
                              ObjectMapper objectMapper,
                              WebAuthnProperties webAuthnProperties,
                              WebAuthnChallengeRepository webAuthnChallengeRepository,
                              WebAuthnCredentialRepository webAuthnCredentialRepository,
                              SecurityUtils securityUtils,
                              AuthService authService,
                              UserRepository userRepository) {
        this.relyingParty = relyingParty;
        this.objectMapper = objectMapper;
        this.webAuthnProperties = webAuthnProperties;
        this.webAuthnChallengeRepository = webAuthnChallengeRepository;
        this.webAuthnCredentialRepository = webAuthnCredentialRepository;
        this.securityUtils = securityUtils;
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @Transactional
    public PasskeyOptionsResponse registerOptions() {
        User user = securityUtils.getCurrentUser();
        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getName())
                .id(uuidToByteArray(user.getId()))
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                        .timeout(webAuthnProperties.challengeTimeoutSeconds() * 1000L)
                        .build()
        );

        WebAuthnChallenge challenge = persistChallenge(
                user,
                WebAuthnChallengeType.REGISTRATION,
                toJsonSafely(options),
                options.getChallenge().getBase64Url()
        );

        return new PasskeyOptionsResponse(true, challenge.getId(), readJson(toCredentialsCreateJsonSafely(options)));
    }

    @Transactional
    public MessageResponse registerVerify(PasskeyVerifyRequest request) {
        User user = securityUtils.getCurrentUser();
        WebAuthnChallenge challenge = resolveUsableChallenge(request.challengeId(), WebAuthnChallengeType.REGISTRATION, user);
        PublicKeyCredentialCreationOptions options = parseRegistrationOptions(challenge.getRequestJson());
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> parsedCredential =
                parseRegistrationCredential(request.credential());

        try {
            RegistrationResult result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(options)
                            .response(parsedCredential)
                            .build()
            );

            String credentialId = result.getKeyId().getId().getBase64Url();
            if (webAuthnCredentialRepository.findByCredentialId(credentialId).isPresent()) {
                throw new BadRequestException("Passkey already registered");
            }

            WebAuthnCredential credential = new WebAuthnCredential();
            credential.setUser(user);
            credential.setCredentialId(credentialId);
            credential.setPublicKeyCose(result.getPublicKeyCose().getBase64Url());
            credential.setSignCount(result.getSignatureCount());
            credential.setTransports(parsedCredential.getResponse().getTransports().stream()
                    .map(AuthenticatorTransport::getId)
                    .collect(Collectors.joining(",")));
            credential.setUserHandle(options.getUser().getId().getBase64Url());
            webAuthnCredentialRepository.save(credential);
            consumeChallenge(challenge);
            return new MessageResponse("Passkey registered successfully");
        } catch (RegistrationFailedException e) {
            throw new BadRequestException("Passkey registration verification failed");
        }
    }

    @Transactional
    public PasskeyOptionsResponse loginOptions(PasskeyLoginOptionsRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || webAuthnCredentialRepository.countByUser(user) == 0) {
            return new PasskeyOptionsResponse(false, null, null);
        }

        AssertionRequest assertionRequest = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(user.getEmail())
                        .timeout(webAuthnProperties.challengeTimeoutSeconds() * 1000L)
                        .build()
        );

        WebAuthnChallenge challenge = persistChallenge(
                user,
                WebAuthnChallengeType.AUTHENTICATION,
                toJsonSafely(assertionRequest),
                assertionRequest.getPublicKeyCredentialRequestOptions().getChallenge().getBase64Url()
        );

        return new PasskeyOptionsResponse(true, challenge.getId(), readJson(toCredentialsGetJsonSafely(assertionRequest)));
    }

    @Transactional
    public AuthResponse loginVerify(PasskeyVerifyRequest request) {
        WebAuthnChallenge challenge = resolveUsableChallenge(request.challengeId(), WebAuthnChallengeType.AUTHENTICATION, null);
        AssertionRequest assertionRequest = parseAssertionRequest(challenge.getRequestJson());
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> parsedCredential =
                parseAssertionCredential(request.credential());

        try {
            AssertionResult assertionResult = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(assertionRequest)
                            .response(parsedCredential)
                            .build()
            );

            if (!assertionResult.isSuccess() || !assertionResult.isSignatureCounterValid()) {
                throw new UnauthorizedException("Invalid passkey assertion");
            }

            User user = userRepository.findByEmail(assertionResult.getUsername())
                    .orElseThrow(() -> new UnauthorizedException("User not found"));
            WebAuthnCredential credential = webAuthnCredentialRepository
                    .findByCredentialId(assertionResult.getCredentialId().getBase64Url())
                    .orElseThrow(() -> new UnauthorizedException("Passkey not registered"));
            credential.setSignCount(assertionResult.getSignatureCount());
            webAuthnCredentialRepository.save(credential);
            consumeChallenge(challenge);
            return authService.issueAuthTokens(user);
        } catch (AssertionFailedException e) {
            throw new UnauthorizedException("Invalid passkey assertion");
        }
    }

    @Transactional(readOnly = true)
    public PasskeyStatusResponse me() {
        User user = securityUtils.getCurrentUser();
        return new PasskeyStatusResponse(webAuthnCredentialRepository.countByUser(user) > 0);
    }

    private WebAuthnChallenge persistChallenge(User user, WebAuthnChallengeType type, String requestJson, String challengeValue) {
        webAuthnChallengeRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        WebAuthnChallenge challenge = new WebAuthnChallenge();
        challenge.setUser(user);
        challenge.setType(type);
        challenge.setRequestJson(requestJson);
        challenge.setChallenge(challengeValue);
        challenge.setUsed(false);
        challenge.setExpiresAt(LocalDateTime.now().plusSeconds(webAuthnProperties.challengeTimeoutSeconds()));
        return webAuthnChallengeRepository.save(challenge);
    }

    private void consumeChallenge(WebAuthnChallenge challenge) {
        challenge.setUsed(true);
        webAuthnChallengeRepository.save(challenge);
    }

    private WebAuthnChallenge resolveUsableChallenge(UUID challengeId, WebAuthnChallengeType type, User expectedUser) {
        WebAuthnChallenge challenge = webAuthnChallengeRepository.findByIdAndTypeAndUsedFalse(challengeId, type)
                .orElseThrow(() -> new BadRequestException("Invalid or already used challenge"));
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Challenge has expired");
        }
        if (expectedUser != null && (challenge.getUser() == null || !expectedUser.getId().equals(challenge.getUser().getId()))) {
            throw new UnauthorizedException("Challenge does not belong to authenticated user");
        }
        return challenge;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WebAuthn options", e);
        }
    }

    private String toJsonSafely(PublicKeyCredentialCreationOptions options) {
        try {
            return options.toJson();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WebAuthn registration request", e);
        }
    }

    private String toJsonSafely(AssertionRequest assertionRequest) {
        try {
            return assertionRequest.toJson();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WebAuthn assertion request", e);
        }
    }

    private String toCredentialsCreateJsonSafely(PublicKeyCredentialCreationOptions options) {
        try {
            return options.toCredentialsCreateJson();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WebAuthn registration options", e);
        }
    }

    private String toCredentialsGetJsonSafely(AssertionRequest assertionRequest) {
        try {
            return assertionRequest.toCredentialsGetJson();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WebAuthn assertion options", e);
        }
    }

    private PublicKeyCredentialCreationOptions parseRegistrationOptions(String json) {
        try {
            return PublicKeyCredentialCreationOptions.fromJson(json);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid registration request payload");
        }
    }

    private AssertionRequest parseAssertionRequest(String json) {
        try {
            return AssertionRequest.fromJson(json);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid authentication request payload");
        }
    }

    private PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> parseRegistrationCredential(JsonNode credential) {
        try {
            return PublicKeyCredential.parseRegistrationResponseJson(objectMapper.writeValueAsString(credential));
        } catch (IOException e) {
            throw new BadRequestException("Invalid registration credential payload");
        }
    }

    private PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> parseAssertionCredential(JsonNode credential) {
        try {
            return PublicKeyCredential.parseAssertionResponseJson(objectMapper.writeValueAsString(credential));
        } catch (IOException e) {
            throw new BadRequestException("Invalid authentication credential payload");
        }
    }

    private ByteArray uuidToByteArray(UUID uuid) {
        byte[] bytes = new byte[16];
        long mostSignificant = uuid.getMostSignificantBits();
        long leastSignificant = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (mostSignificant >>> (56 - (i * 8)));
            bytes[8 + i] = (byte) (leastSignificant >>> (56 - (i * 8)));
        }
        return new ByteArray(bytes);
    }
}

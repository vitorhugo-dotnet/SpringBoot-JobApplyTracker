package com.jobtracker.service;

import com.jobtracker.entity.User;
import com.jobtracker.entity.WebAuthnCredential;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.repository.WebAuthnCredentialRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.AuthenticatorTransport;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class JpaWebAuthnCredentialRepository implements CredentialRepository {

    private final WebAuthnCredentialRepository webAuthnCredentialRepository;
    private final UserRepository userRepository;

    public JpaWebAuthnCredentialRepository(WebAuthnCredentialRepository webAuthnCredentialRepository,
                                           UserRepository userRepository) {
        this.webAuthnCredentialRepository = webAuthnCredentialRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userRepository.findByEmail(username)
                .map(user -> webAuthnCredentialRepository.findByUser(user).stream()
                        .map(this::toDescriptor)
                        .collect(Collectors.toSet()))
                .orElseGet(Collections::emptySet);
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByEmail(username)
                .map(User::getId)
                .map(this::uuidToByteArray);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return userRepository.findById(byteArrayToUuid(userHandle))
                .map(User::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return webAuthnCredentialRepository.findByCredentialId(credentialId.getBase64Url())
                .filter(stored -> stored.getUserHandle().equals(userHandle.getBase64Url()))
                .map(this::toRegisteredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return webAuthnCredentialRepository.findByCredentialId(credentialId.getBase64Url())
                .map(stored -> Set.of(toRegisteredCredential(stored)))
                .orElseGet(Collections::emptySet);
    }

    private PublicKeyCredentialDescriptor toDescriptor(WebAuthnCredential credential) {
        PublicKeyCredentialDescriptor.PublicKeyCredentialDescriptorBuilder builder = PublicKeyCredentialDescriptor.builder()
                .id(parseBase64Url(credential.getCredentialId()));
        parseTransports(credential.getTransports()).ifPresent(builder::transports);
        return builder.build();
    }

    private Optional<SortedSet<AuthenticatorTransport>> parseTransports(String transports) {
        if (transports == null || transports.isBlank()) {
            return Optional.empty();
        }
        SortedSet<AuthenticatorTransport> parsed = Arrays.stream(transports.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(this::toTransportOrNull)
                .filter(value -> value != null)
                .collect(Collectors.toCollection(TreeSet::new));
        return parsed.isEmpty() ? Optional.empty() : Optional.of(parsed);
    }

    private AuthenticatorTransport toTransportOrNull(String value) {
        try {
            return AuthenticatorTransport.of(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private RegisteredCredential toRegisteredCredential(WebAuthnCredential credential) {
        return RegisteredCredential.builder()
                .credentialId(parseBase64Url(credential.getCredentialId()))
                .userHandle(parseBase64Url(credential.getUserHandle()))
                .publicKeyCose(parseBase64Url(credential.getPublicKeyCose()))
                .signatureCount(credential.getSignCount())
                .build();
    }

    private ByteArray parseBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (Base64UrlException e) {
            throw new IllegalArgumentException("Invalid stored WebAuthn credential encoding", e);
        }
    }

    private ByteArray uuidToByteArray(java.util.UUID uuid) {
        byte[] bytes = new byte[16];
        long mostSignificant = uuid.getMostSignificantBits();
        long leastSignificant = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (mostSignificant >>> (56 - (i * 8)));
            bytes[8 + i] = (byte) (leastSignificant >>> (56 - (i * 8)));
        }
        return new ByteArray(bytes);
    }

    private java.util.UUID byteArrayToUuid(ByteArray userHandle) {
        byte[] bytes = userHandle.getBytes();
        if (bytes.length != 16) {
            throw new IllegalArgumentException("Invalid user handle length");
        }
        long mostSignificant = 0;
        long leastSignificant = 0;
        for (int i = 0; i < 8; i++) {
            mostSignificant = (mostSignificant << 8) | (bytes[i] & 0xff);
            leastSignificant = (leastSignificant << 8) | (bytes[8 + i] & 0xff);
        }
        return new java.util.UUID(mostSignificant, leastSignificant);
    }
}

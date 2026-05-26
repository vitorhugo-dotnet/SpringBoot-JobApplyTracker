package com.jobtracker.repository;

import com.jobtracker.entity.User;
import com.jobtracker.entity.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, UUID> {
    List<WebAuthnCredential> findByUser(User user);
    long countByUser(User user);
    Optional<WebAuthnCredential> findByCredentialId(String credentialId);
}

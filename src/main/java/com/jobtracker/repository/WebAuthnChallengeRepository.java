package com.jobtracker.repository;

import com.jobtracker.entity.WebAuthnChallenge;
import com.jobtracker.entity.enums.WebAuthnChallengeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface WebAuthnChallengeRepository extends JpaRepository<WebAuthnChallenge, UUID> {
    Optional<WebAuthnChallenge> findByIdAndTypeAndUsedFalse(UUID id, WebAuthnChallengeType type);
    void deleteAllByExpiresAtBefore(LocalDateTime threshold);
}

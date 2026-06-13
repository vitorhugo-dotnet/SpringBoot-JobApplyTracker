package com.jobtracker.repository;

import com.jobtracker.entity.GoogleDriveBaseInformation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoogleDriveBaseInformationRepository extends JpaRepository<GoogleDriveBaseInformation, UUID> {

    List<GoogleDriveBaseInformation> findAllByConnectionIdOrderByCreatedAtAsc(UUID connectionId);

    List<GoogleDriveBaseInformation> findAllByConnectionUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<GoogleDriveBaseInformation> findByIdAndConnectionUserId(UUID id, UUID userId);

    Optional<GoogleDriveBaseInformation> findByConnectionIdAndGoogleFileId(UUID connectionId, String googleFileId);
}

package com.jobtracker.service;

import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists Google Drive connection token-state changes in their own, independent transaction.
 *
 * <p>{@link GoogleDriveCredentialService} is normally invoked from within a caller's larger
 * {@code @Transactional} business operation (e.g. generating a resume). If that operation later
 * fails for an unrelated reason — including because the credential service itself just threw a
 * reauthorization-required error — the caller's transaction rolls back. A token refresh or a
 * reauthorization-required flag must survive that rollback regardless, since it reflects the real
 * state of the external Google grant, not the outcome of the business operation that triggered it.
 * {@code REQUIRES_NEW} commits the write in a separate transaction before control returns to the
 * caller, so it is durable even though the caller's own transaction is doomed to roll back.
 */
@Service
public class GoogleDriveConnectionStateWriter {

    private final GoogleDriveConnectionRepository connectionRepository;

    public GoogleDriveConnectionStateWriter(GoogleDriveConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GoogleDriveConnection save(GoogleDriveConnection connection) {
        return connectionRepository.save(connection);
    }
}

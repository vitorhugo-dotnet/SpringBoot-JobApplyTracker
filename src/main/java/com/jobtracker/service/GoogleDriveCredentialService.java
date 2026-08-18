package com.jobtracker.service;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.GoogleAccessTokenRefreshFailedException;
import com.jobtracker.exception.GoogleAuthenticationException;
import com.jobtracker.exception.GoogleReauthorizationRequiredException;
import com.jobtracker.exception.ServiceUnavailableException;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Single owner of the Google OAuth access-token lifecycle for a user's Drive connection.
 *
 * <p>Every Google Drive/Docs integration (resume generation, Drive lookup, base information,
 * exports, ...) obtains credentials through this service instead of duplicating refresh logic,
 * so all of them get the same behavior: proactive refresh ahead of expiry, a guarded
 * refresh-and-retry-once when Google unexpectedly returns 401, and a clear distinction between a
 * transient refresh failure and a grant that genuinely requires the user to reconnect.
 *
 * <p>Concurrent refreshes for the same user are serialized with an in-process lock so a burst of
 * simultaneous requests triggers at most one call to Google's token endpoint; later callers reuse
 * whichever access token the winner obtained.
 */
@Service
public class GoogleDriveCredentialService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveCredentialService.class);

    private static final String REAUTHORIZATION_MESSAGE =
            "Google Drive authorization has expired or been revoked. Reconnect Google Drive in ApplyWell and retry the operation.";
    private static final String REFRESH_FAILED_MESSAGE =
            "Google Drive is temporarily unavailable while refreshing access. Please retry shortly.";
    private static final int REASON_MAX_LENGTH = 255;

    private final GoogleDriveApiClient googleDriveApiClient;
    private final GoogleDriveProperties googleDriveProperties;
    private final GoogleDriveConnectionRepository connectionRepository;
    private final GoogleDriveConnectionStateWriter connectionStateWriter;
    private final Duration refreshSkew;

    /** One lock per user, so concurrent requests for the same connection refresh at most once. */
    private final ConcurrentHashMap<UUID, Object> refreshLocks = new ConcurrentHashMap<>();

    public GoogleDriveCredentialService(GoogleDriveApiClient googleDriveApiClient,
                                        GoogleDriveProperties googleDriveProperties,
                                        GoogleDriveConnectionRepository connectionRepository,
                                        GoogleDriveConnectionStateWriter connectionStateWriter) {
        this.googleDriveApiClient = googleDriveApiClient;
        this.googleDriveProperties = googleDriveProperties;
        this.connectionRepository = connectionRepository;
        this.connectionStateWriter = connectionStateWriter;
        this.refreshSkew = Duration.ofSeconds(googleDriveProperties.getTokenRefreshSkewSeconds());
    }

    /**
     * Returns the user's connection with a non-expiring-soon access token, refreshing proactively
     * using the stored refresh token when the current one is missing or within the skew window.
     */
    public GoogleDriveConnection getValidCredentials(UUID userId) {
        requireServerConfigured();
        GoogleDriveConnection connection = loadConnection(userId);
        if (isFresh(connection)) {
            return connection;
        }
        return refreshOnceFor(userId, connection.getAccessToken());
    }

    /**
     * Runs a single Google Drive/Docs API operation with a valid access token. If Google rejects
     * the token with 401 despite it looking fresh, refreshes once and retries the operation once;
     * a second failure propagates to the caller.
     */
    public <T> T call(UUID userId, Function<String, T> operation) {
        GoogleDriveConnection connection = getValidCredentials(userId);
        String accessToken = connection.getAccessToken();
        try {
            return operation.apply(accessToken);
        } catch (GoogleAuthenticationException ex) {
            log.warn("event=GOOGLE_ACCESS_TOKEN_REJECTED userId={} - refreshing and retrying once", userId);
            GoogleDriveConnection refreshed = refreshOnceFor(userId, accessToken);
            try {
                return operation.apply(refreshed.getAccessToken());
            } catch (GoogleAuthenticationException stillRejected) {
                log.error("event=GOOGLE_ACCESS_TOKEN_REJECTED_AFTER_REFRESH userId={}", userId);
                throw new GoogleAccessTokenRefreshFailedException(
                        "Google Drive rejected the request even after refreshing access. Please retry shortly.");
            }
        }
    }

    private GoogleDriveConnection refreshOnceFor(UUID userId, String knownStaleAccessToken) {
        Object lock = refreshLocks.computeIfAbsent(userId, id -> new Object());
        synchronized (lock) {
            GoogleDriveConnection connection = loadConnection(userId);
            if (!connection.getAccessToken().equals(knownStaleAccessToken)) {
                // A concurrent request already refreshed this connection; reuse its result
                // instead of hitting Google's token endpoint again.
                return connection;
            }
            return doRefresh(userId, connection);
        }
    }

    private GoogleDriveConnection doRefresh(UUID userId, GoogleDriveConnection connection) {
        GoogleDriveApiClient.OAuthTokens refreshed;
        try {
            refreshed = googleDriveApiClient.refreshAccessToken(connection.getRefreshToken());
        } catch (BadRequestException ex) {
            // The SDK client already classified this as an unrecoverable grant problem
            // (invalid_grant/invalid_client) rather than a transient provider failure.
            markReauthorizationRequired(connection, ex.getMessage());
            throw new GoogleReauthorizationRequiredException(REAUTHORIZATION_MESSAGE);
        } catch (ServiceUnavailableException ex) {
            log.warn("event=GOOGLE_ACCESS_TOKEN_REFRESH_TRANSIENT_FAILURE userId={}", userId);
            throw new GoogleAccessTokenRefreshFailedException(REFRESH_FAILED_MESSAGE);
        }

        connection.setAccessToken(refreshed.accessToken());
        connection.setAccessTokenExpiresAt(refreshed.accessTokenExpiresAt());
        // Google typically omits refresh_token on a refresh response; never overwrite the
        // stored refresh token with a blank value when that happens.
        if (StringUtils.hasText(refreshed.scope())) {
            connection.setGrantedScopes(refreshed.scope());
        }
        connection.setReauthorizationRequired(false);
        connection.setReauthorizationReason(null);
        return connectionStateWriter.save(connection);
    }

    private void markReauthorizationRequired(GoogleDriveConnection connection, String reason) {
        connection.setReauthorizationRequired(true);
        connection.setReauthorizationReason(truncate(reason));
        // Committed independently (REQUIRES_NEW) so the flag survives even though this method's
        // caller is about to throw, which would otherwise roll back this write too.
        connectionStateWriter.save(connection);
    }

    private GoogleDriveConnection loadConnection(UUID userId) {
        GoogleDriveConnection connection = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Google Drive is not connected for the current user"));
        if (connection.isReauthorizationRequired()) {
            throw new GoogleReauthorizationRequiredException(REAUTHORIZATION_MESSAGE);
        }
        return connection;
    }

    private boolean isFresh(GoogleDriveConnection connection) {
        return connection.getAccessTokenExpiresAt() != null
                && connection.getAccessTokenExpiresAt().isAfter(LocalDateTime.now().plus(refreshSkew));
    }

    private void requireServerConfigured() {
        if (!googleDriveProperties.isConfigured()) {
            throw new BadRequestException("Google Drive integration is not configured on the server");
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= REASON_MAX_LENGTH ? value : value.substring(0, REASON_MAX_LENGTH);
    }
}

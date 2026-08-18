package com.jobtracker.unit;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.GoogleAccessTokenRefreshFailedException;
import com.jobtracker.exception.GoogleAuthenticationException;
import com.jobtracker.exception.GoogleReauthorizationRequiredException;
import com.jobtracker.exception.ServiceUnavailableException;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.service.GoogleDriveConnectionStateWriter;
import com.jobtracker.service.GoogleDriveCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleDriveCredentialServiceTest {

    private static final String TEST_SCOPES = "https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/documents.readonly";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private GoogleDriveApiClient googleDriveApiClient;
    @Mock private GoogleDriveConnectionRepository connectionRepository;

    private GoogleDriveCredentialService credentialService;
    private GoogleDriveConnection connection;

    @BeforeEach
    void setUp() {
        GoogleDriveProperties properties = new GoogleDriveProperties(
                "client-id", "client-secret",
                "http://localhost:8080/api/v1/google-drive/oauth/callback",
                "http://localhost:5173/settings/google-drive/callback",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                TEST_SCOPES,
                60
        );
        credentialService = new GoogleDriveCredentialService(googleDriveApiClient, properties, connectionRepository,
                new GoogleDriveConnectionStateWriter(connectionRepository));

        User user = new User();
        user.setId(USER_ID);
        user.setEmail("user@example.com");

        connection = new GoogleDriveConnection();
        connection.setUser(user);
        connection.setAccessToken("initial-access-token");
        connection.setRefreshToken("stored-refresh-token");
        connection.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));
        connection.setGrantedScopes("https://www.googleapis.com/auth/drive");

        lenient().when(connectionRepository.save(any(GoogleDriveConnection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getValidCredentials_doesNotRefresh_whenTokenIsFresh() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));

        GoogleDriveConnection result = credentialService.getValidCredentials(USER_ID);

        assertThat(result.getAccessToken()).isEqualTo("initial-access-token");
        verify(googleDriveApiClient, never()).refreshAccessToken(anyString());
    }

    @Test
    void getValidCredentials_refreshes_whenExpiryIsInsideSkewWindow() {
        connection.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(30));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token")).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("new-access-token", "stored-refresh-token",
                        LocalDateTime.now().plusHours(1), "https://www.googleapis.com/auth/drive"));

        GoogleDriveConnection result = credentialService.getValidCredentials(USER_ID);

        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        verify(googleDriveApiClient).refreshAccessToken("stored-refresh-token");
    }

    @Test
    void getValidCredentials_refreshes_whenTokenIsAlreadyExpired() {
        connection.setAccessTokenExpiresAt(LocalDateTime.now().minusMinutes(5));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token")).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("new-access-token", "stored-refresh-token",
                        LocalDateTime.now().plusHours(1), "https://www.googleapis.com/auth/drive"));

        GoogleDriveConnection result = credentialService.getValidCredentials(USER_ID);

        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
    }

    @Test
    void getValidCredentials_persistsRefreshedAccessTokenAndExpiry() {
        LocalDateTime newExpiry = LocalDateTime.now().plusHours(2);
        connection.setAccessTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token")).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("new-access-token", "stored-refresh-token", newExpiry, "scope"));

        credentialService.getValidCredentials(USER_ID);

        verify(connectionRepository).save(connection);
        assertThat(connection.getAccessToken()).isEqualTo("new-access-token");
        assertThat(connection.getAccessTokenExpiresAt()).isEqualTo(newExpiry);
    }

    @Test
    void getValidCredentials_preservesStoredRefreshToken_whenGoogleOmitsANewOne() {
        connection.setAccessTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        // The SDK client always echoes back the input refresh token when Google omits one; the
        // credential service must never overwrite the stored refresh token with anything else.
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token")).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("new-access-token", "stored-refresh-token",
                        LocalDateTime.now().plusHours(1), null));

        credentialService.getValidCredentials(USER_ID);

        assertThat(connection.getRefreshToken()).isEqualTo("stored-refresh-token");
    }

    @Test
    void getValidCredentials_marksReauthorizationRequired_whenRefreshFailsWithInvalidGrant() {
        connection.setAccessTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token"))
                .thenThrow(new BadRequestException("Google OAuth error during refresh access token: invalid_grant"));

        assertThatThrownBy(() -> credentialService.getValidCredentials(USER_ID))
                .isInstanceOf(GoogleReauthorizationRequiredException.class)
                .hasMessageContaining("Reconnect Google Drive")
                .hasMessageNotContaining("invalid_grant")
                .hasMessageNotContaining("stored-refresh-token");

        assertThat(connection.isReauthorizationRequired()).isTrue();
        assertThat(connection.getReauthorizationReason()).contains("invalid_grant");
    }

    @Test
    void getValidCredentials_doesNotMarkRevoked_whenRefreshFailsTransiently() {
        connection.setAccessTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token"))
                .thenThrow(new ServiceUnavailableException("Google OAuth service unavailable during refresh access token"));

        assertThatThrownBy(() -> credentialService.getValidCredentials(USER_ID))
                .isInstanceOf(GoogleAccessTokenRefreshFailedException.class);

        assertThat(connection.isReauthorizationRequired()).isFalse();
        assertThat(connection.getReauthorizationReason()).isNull();
    }

    @Test
    void getValidCredentials_shortCircuits_whenConnectionAlreadyFlaggedForReauthorization() {
        connection.setReauthorizationRequired(true);
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> credentialService.getValidCredentials(USER_ID))
                .isInstanceOf(GoogleReauthorizationRequiredException.class);

        verify(googleDriveApiClient, never()).refreshAccessToken(anyString());
    }

    @Test
    void getValidCredentials_throws_whenNoConnectionExists() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> credentialService.getValidCredentials(USER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void call_retriesOnceAfterRefresh_whenGoogleRejectsAFreshLookingToken() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token")).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("new-access-token", "stored-refresh-token",
                        LocalDateTime.now().plusHours(1), "scope"));

        AtomicInteger attempts = new AtomicInteger();
        String result = credentialService.call(USER_ID, token -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new GoogleAuthenticationException("Simulated 401");
            }
            return "success:" + token;
        });

        assertThat(result).isEqualTo("success:new-access-token");
        assertThat(attempts.get()).isEqualTo(2);
        verify(googleDriveApiClient, times(1)).refreshAccessToken("stored-refresh-token");
    }

    @Test
    void call_stopsAfterOneRetry_whenGoogleKeepsRejectingTheToken() {
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token")).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("new-access-token", "stored-refresh-token",
                        LocalDateTime.now().plusHours(1), "scope"));

        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> credentialService.call(USER_ID, token -> {
            attempts.incrementAndGet();
            throw new GoogleAuthenticationException("Simulated 401");
        })).isInstanceOf(GoogleAccessTokenRefreshFailedException.class);

        assertThat(attempts.get()).isEqualTo(2);
        verify(googleDriveApiClient, times(1)).refreshAccessToken("stored-refresh-token");
    }

    @Test
    void concurrentRefreshRequests_resultInAtMostOneEffectiveRefresh() throws InterruptedException {
        connection.setAccessTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));

        AtomicInteger refreshCount = new AtomicInteger();
        when(googleDriveApiClient.refreshAccessToken("stored-refresh-token")).thenAnswer(invocation -> {
            refreshCount.incrementAndGet();
            // Widen the race window so concurrent callers overlap while one holds the lock.
            Thread.sleep(50);
            return new GoogleDriveApiClient.OAuthTokens("new-access-token", "stored-refresh-token",
                    LocalDateTime.now().plusHours(1), "scope");
        });

        int threadCount = 8;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new CopyOnWriteArrayList<>();
        List<String> results = new CopyOnWriteArrayList<>();

        IntStream.range(0, threadCount).forEach(i -> {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                results.add(credentialService.getValidCredentials(USER_ID).getAccessToken());
            });
            threads.add(thread);
            thread.start();
        });

        ready.await();
        go.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(refreshCount.get()).isEqualTo(1);
        assertThat(results).hasSize(threadCount).allMatch("new-access-token"::equals);
    }
}

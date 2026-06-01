package com.jobtracker.integration;

import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.GoogleDriveOAuthStateRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.repository.WebAuthnChallengeRepository;
import com.jobtracker.repository.WebAuthnCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.gpt-fallback-auth.enabled=true",
        "app.gpt-fallback-auth.token=test-gpt-fallback-token"
})
class GptFallbackAuthIT extends AbstractIntegrationTest {

    private static final String FALLBACK_EMAIL = "gpt-fallback@jobtracker.local";
    private static final String FALLBACK_TOKEN = "test-gpt-fallback-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private GoogleDriveOAuthStateRepository googleDriveOAuthStateRepository;
    @Autowired private GoogleDriveConnectionRepository googleDriveConnectionRepository;
    @Autowired private UserAchievementRepository userAchievementRepository;
    @Autowired private UserGamificationRepository userGamificationRepository;
    @Autowired private InterviewEventRepository interviewEventRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private WebAuthnChallengeRepository webAuthnChallengeRepository;
    @Autowired private WebAuthnCredentialRepository webAuthnCredentialRepository;
    @Autowired private UserInterviewMetricsRepository userInterviewMetricsRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDb() {
        googleDriveOAuthStateRepository.deleteAll();
        googleDriveConnectionRepository.deleteAll();
        userAchievementRepository.deleteAll();
        userGamificationRepository.deleteAll();
        interviewEventRepository.deleteAll();
        applicationRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        webAuthnChallengeRepository.deleteAll();
        webAuthnCredentialRepository.deleteAll();
        userInterviewMetricsRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void fallbackBearerTokenShouldAuthenticateRequests() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + FALLBACK_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(FALLBACK_EMAIL));

        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + FALLBACK_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}

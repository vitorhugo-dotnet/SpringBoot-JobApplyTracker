package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Points the integration at an unreachable API so the endpoint is exercised end to end without
 * touching the real github.com: the configured login is then served as a stale fallback.
 */
@TestPropertySource(properties = {
        "app.github.user-id=65777252",
        "app.github.login=vitorhugo-dotnet",
        "app.github.api-base-url=http://localhost:1",
        "app.github.timeout-ms=500"
})
class GitHubControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDb() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getProfile_shouldReturn403_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/github/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProfile_shouldReturnStableUserIdAndFallbackProfile_whenGitHubIsUnreachable() throws Exception {
        String accessToken = registerAndGetAccessToken("github-profile@example.com");

        mockMvc.perform(get("/api/v1/github/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(65777252L))
                .andExpect(jsonPath("$.login").value("vitorhugo-dotnet"))
                .andExpect(jsonPath("$.htmlUrl").value("https://github.com/vitorhugo-dotnet"))
                .andExpect(jsonPath("$.stale").value(true));
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        RegisterRequest request = new RegisterRequest("GitHub User", email, "pass1234", "pass1234", true);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return response.accessToken();
    }
}

package com.jobtracker.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasskeyDiscoverableLoginIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginOptions_shouldStartDiscoverableAuthenticationWithoutEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/passkey/login/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passkeyAvailable").value(true))
                .andExpect(jsonPath("$.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.publicKey.challenge").isNotEmpty())
                .andExpect(jsonPath("$.publicKey.rpId").value("localhost"));
    }
}

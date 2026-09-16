package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasskeyRegistrationOptionsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void passkeyRegisterOptions_shouldRequireDiscoverableCredential() throws Exception {
        String email = "passkey-" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(
                "Passkey User",
                email,
                "pass1234",
                "pass1234",
                true
        );

        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(registration.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(post("/api/v1/auth/passkey/register/options")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey.authenticatorSelection.residentKey").value("required"))
                .andExpect(jsonPath("$.publicKey.authenticatorSelection.requireResidentKey").value(true));
    }
}

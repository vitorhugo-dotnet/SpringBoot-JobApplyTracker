package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "resilience4j.ratelimiter.instances.exportManual.limit-for-period=1",
        "resilience4j.ratelimiter.instances.exportManual.limit-refresh-period=10m",
        "resilience4j.ratelimiter.instances.exportManual.timeout-duration=0"
})
class ExportRateLimitIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void manualExport_shouldReturn429_whenRateLimitIsExceeded() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
                "Export Rate Limit", "export-ratelimit@example.com", "pass1234", "pass1234", true);
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class).accessToken();

        mockMvc.perform(post("/api/v1/exports/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format":"CSV"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/exports/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format":"CSV"}"""))
                .andExpect(status().isTooManyRequests());
    }
}

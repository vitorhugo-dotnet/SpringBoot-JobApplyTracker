package com.jobtracker.integration.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.integration.AbstractIntegrationTest;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the MCP endpoint requires authentication via the existing OAuth2/JWT machinery.
 *
 * Spring AI 1.0.0 uses SSE transport (WebMvcSseServerTransport). The message endpoint for
 * JSON-RPC requests is POST /mcp/messages. The response is returned directly in the HTTP
 * response body (as required by the MCP HTTP+SSE spec).
 */
class McpAuthIT extends AbstractIntegrationTest {

    private static final String MCP_ENDPOINT = "/mcp/messages";

    private static final String MCP_INITIALIZE_BODY = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "initialize",
              "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": { "name": "test-client", "version": "1.0" }
              }
            }
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest reg = new RegisterRequest("MCP Test User", "mcp-test@example.com", "pass1234", "pass1234");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();
    }

    @Test
    void mcpEndpoint_withoutToken_returns403() throws Exception {
        mockMvc.perform(post(MCP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MCP_INITIALIZE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpEndpoint_withValidToken_doesNotReturn403() throws Exception {
        MvcResult result = mockMvc.perform(post(MCP_ENDPOINT)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MCP_INITIALIZE_BODY))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }

    @Test
    void mcpEndpoint_withMalformedToken_returns403() throws Exception {
        mockMvc.perform(post(MCP_ENDPOINT)
                        .header("Authorization", "Bearer not-a-real-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MCP_INITIALIZE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void mcpToken_worksForRestEndpoints() throws Exception {
        // Same JWT works for both REST and MCP — confirms auth symmetry.
        // 400 = passed auth but body was invalid; anything except 403 means auth succeeded.
        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }
}

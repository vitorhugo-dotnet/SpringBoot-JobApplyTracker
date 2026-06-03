package com.jobtracker.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.integration.AbstractIntegrationTest;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for MCP tool calls.
 *
 * The MCP Streamable HTTP transport requires an initialize handshake before any
 * other method (tools/list, tools/call) will be accepted. setUp() sends initialize
 * and captures the Mcp-Session-Id, which is then included in every subsequent request.
 */
class McpToolsIT extends AbstractIntegrationTest {

    private static final String MCP_INITIALIZE_BODY = """
            {
              "jsonrpc": "2.0",
              "id": 0,
              "method": "initialize",
              "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": { "name": "test-client", "version": "1.0" }
              }
            }
            """;

    private static final String TOOLS_LIST_BODY = """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "method": "tools/list",
              "params": {}
            }
            """;

    private static final String LIST_APPLICATIONS_CALL = """
            {
              "jsonrpc": "2.0",
              "id": 3,
              "method": "tools/call",
              "params": {
                "name": "listApplications",
                "arguments": {}
              }
            }
            """;

    private static final String GET_PIPELINE_SUMMARY_CALL = """
            {
              "jsonrpc": "2.0",
              "id": 4,
              "method": "tools/call",
              "params": {
                "name": "getPipelineSummary",
                "arguments": {}
              }
            }
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private ApplicationRepository applicationRepository;

    private String accessToken;
    private String mcpSessionId;

    @BeforeEach
    void setUp() throws Exception {
        applicationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest reg = new RegisterRequest("Tools Test User", "tools-test@example.com", "pass1234", "pass1234");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();

        // Establish MCP session — initialize is required before tools/list or tools/call
        MvcResult initResult = mockMvc.perform(post("/mcp")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MCP_INITIALIZE_BODY))
                .andReturn();
        mcpSessionId = initResult.getResponse().getHeader("Mcp-Session-Id");
    }

    /** Builds a /mcp request with auth + session headers already set. */
    private MockHttpServletRequestBuilder mcpPost(String body) {
        MockHttpServletRequestBuilder req = post("/mcp")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (mcpSessionId != null) {
            req.header("Mcp-Session-Id", mcpSessionId);
        }
        return req;
    }

    @Test
    void toolsList_authenticated_returnsApplicationTools() throws Exception {
        MvcResult result = mockMvc.perform(mcpPost(TOOLS_LIST_BODY))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isGreaterThan(0);

        boolean hasListApplications = false;
        boolean hasCreateApplication = false;
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            if ("listApplications".equals(name)) hasListApplications = true;
            if ("createApplication".equals(name)) hasCreateApplication = true;
        }
        assertThat(hasListApplications)
                .as("Expected 'listApplications' tool in tools/list response")
                .isTrue();
        assertThat(hasCreateApplication)
                .as("Expected 'createApplication' tool in tools/list response")
                .isTrue();
    }

    @Test
    void listApplicationsTool_authenticated_returnsEmptyPageForNewUser() throws Exception {
        MvcResult result = mockMvc.perform(mcpPost(LIST_APPLICATIONS_CALL))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"error\"");
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("result").isMissingNode()).isFalse();
    }

    @Test
    void getPipelineSummaryTool_authenticated_returnsValidResponse() throws Exception {
        MvcResult result = mockMvc.perform(mcpPost(GET_PIPELINE_SUMMARY_CALL))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"error\"");
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.path("result").isMissingNode()).isFalse();
    }
}

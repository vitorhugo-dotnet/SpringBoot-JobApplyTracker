package com.jobtracker.e2e;

import com.jobtracker.entity.Role;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.RoleName;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.RoleRepository;
import com.jobtracker.repository.UserRepository;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real end-to-end coverage of the MCP tool schema, run against a live Tomcat instance
 * (RANDOM_PORT) so the Streamable HTTP RouterFunction transport is actually exercised —
 * MockMvc's DispatcherServlet never discovers it (see the previously @Disabled McpToolsIT).
 *
 * Guards issue #63: the connector must advertise List-Base-Information and
 * Get-Base-Information-Content, and a BETA user must be able to invoke them.
 */
class McpToolsE2ETest extends AbstractE2ETest {

    private static final String MCP_ENDPOINT = "/mcp";
    private static final String MCP_SESSION_ID_HEADER = "Mcp-Session-Id";

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

    private static final String LIST_BASE_INFORMATION_CALL = """
            {
              "jsonrpc": "2.0",
              "id": 3,
              "method": "tools/call",
              "params": {
                "name": "List-Base-Information",
                "arguments": {}
              }
            }
            """;

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private ApplicationRepository applicationRepository;

    private String betaAccessToken;
    private String mcpSessionId;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "MCP Tools E2E User",
                          "email": "mcp-tools-e2e@example.com",
                          "password": "pass1234",
                          "confirmPassword": "pass1234",
                          "acceptedPrivacyPolicy": true
                        }
                        """)
                .post("/api/v1/auth/register")
                .then().statusCode(201);

        User user = userRepository.findByEmail("mcp-tools-e2e@example.com").orElseThrow();
        Role betaRole = roleRepository.findByName(RoleName.BETA)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(RoleName.BETA);
                    return roleRepository.save(role);
                });
        user.setRoles(Set.of(user.getRoles().stream().findFirst().orElseThrow(), betaRole));
        userRepository.save(user);

        Response login = given()
                .contentType("application/json")
                .body("""
                        {
                          "email": "mcp-tools-e2e@example.com",
                          "password": "pass1234"
                        }
                        """)
                .post("/api/v1/auth/login")
                .then().statusCode(200).extract().response();
        betaAccessToken = login.jsonPath().getString("accessToken");

        // MCP protocol requires initialize before other methods. The Streamable HTTP transport
        // hands back a Mcp-Session-Id that every subsequent request on this session must echo,
        // or the server rejects it with 400 "Session ID missing".
        Response initialize = given()
                .header("Authorization", "Bearer " + betaAccessToken)
                .accept("application/json, text/event-stream")
                .contentType("application/json")
                .body(MCP_INITIALIZE_BODY)
                .post(MCP_ENDPOINT)
                .then().statusCode(200).extract().response();
        mcpSessionId = initialize.header(MCP_SESSION_ID_HEADER);
        assertThat(mcpSessionId)
                .as("initialize must return a Mcp-Session-Id header for the Streamable HTTP transport")
                .isNotBlank();
    }

    @Test
    void toolsList_authenticated_advertisesBaseInformationTools() {
        Response response = given()
                .header("Authorization", "Bearer " + betaAccessToken)
                .header(MCP_SESSION_ID_HEADER, mcpSessionId)
                .accept("application/json, text/event-stream")
                .contentType("application/json")
                .body(TOOLS_LIST_BODY)
                .post(MCP_ENDPOINT)
                .then().statusCode(200).extract().response();

        JsonPath body = mcpJsonRpcBody(response);
        List<String> toolNames = body.getList("result.tools.name", String.class);
        assertThat(toolNames).isNotEmpty();
        assertThat(toolNames)
                .as("Expected 'List-Base-Information' in the tools/list response — issue #63")
                .contains("List-Base-Information");
        assertThat(toolNames)
                .as("Expected 'Get-Base-Information-Content' in the tools/list response — issue #63")
                .contains("Get-Base-Information-Content");
        assertThat(toolNames)
                .as("Sanity check: unrelated pre-existing tools must still be present")
                .contains("List-Applications", "Create-Application");
    }

    @Test
    void listBaseInformationTool_authenticatedBetaUser_returnsSuccessfulResult() {
        Response response = given()
                .header("Authorization", "Bearer " + betaAccessToken)
                .header(MCP_SESSION_ID_HEADER, mcpSessionId)
                .accept("application/json, text/event-stream")
                .contentType("application/json")
                .body(LIST_BASE_INFORMATION_CALL)
                .post(MCP_ENDPOINT)
                .then().statusCode(200).extract().response();

        JsonPath body = mcpJsonRpcBody(response);
        Boolean isError = body.getObject("result.isError", Boolean.class);
        assertThat(isError)
                .as("List-Base-Information must be callable end-to-end for a BETA user without error: %s",
                        response.asString())
                .isNotEqualTo(Boolean.TRUE);
    }

    /**
     * The Streamable HTTP transport frames JSON-RPC responses as a single SSE event
     * ({@code event: message\ndata: {...}}) rather than a bare JSON body, so the payload has
     * to be unwrapped before it can be parsed as JSON.
     */
    private static JsonPath mcpJsonRpcBody(Response response) {
        String raw = response.asString();
        String jsonLine = raw.lines()
                .filter(line -> line.startsWith("data:"))
                .findFirst()
                .map(line -> line.substring("data:".length()).trim())
                .orElse(raw);
        return new JsonPath(jsonLine);
    }
}

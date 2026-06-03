package com.jobtracker.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RFC 9728 OAuth 2.0 Protected Resource Metadata.
 *
 * Claude's MCP client fetches this before starting OAuth to discover which
 * authorization server protects the resource and which scopes are required.
 * Spring AI MCP Server WebMVC 1.0.0 does not auto-register this endpoint.
 *
 * Path-specific variant (RFC 9728 §4): when the client requests
 *   GET /.well-known/oauth-protected-resource/mcp/messages
 * the suffix (/mcp/messages) identifies the specific resource being accessed,
 * so we reflect it back in the "resource" field.
 */
@RestController
public class OAuthProtectedResourceMetadataController {

    private static final String WELL_KNOWN_PREFIX = "/.well-known/oauth-protected-resource";

    private final AuthorizationServerSettings authorizationServerSettings;
    private final McpOAuthProperties mcpOAuthProperties;

    public OAuthProtectedResourceMetadataController(
            AuthorizationServerSettings authorizationServerSettings,
            McpOAuthProperties mcpOAuthProperties) {
        this.authorizationServerSettings = authorizationServerSettings;
        this.mcpOAuthProperties = mcpOAuthProperties;
    }

    @GetMapping(
            value = {WELL_KNOWN_PREFIX, WELL_KNOWN_PREFIX + "/**"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> protectedResourceMetadata(HttpServletRequest request) {
        String issuer = authorizationServerSettings.getIssuer();
        String requestPath = request.getRequestURI();

        // Derive the resource path suffix after the well-known prefix.
        // "/.well-known/oauth-protected-resource"           → suffix = ""   → resource = issuer
        // "/.well-known/oauth-protected-resource/mcp/messages" → suffix = "/mcp/messages"
        String suffix = requestPath.startsWith(WELL_KNOWN_PREFIX)
                ? requestPath.substring(WELL_KNOWN_PREFIX.length())
                : "";
        String resource = issuer + suffix;

        return Map.of(
                "resource", resource,
                "authorization_servers", List.of(issuer),
                "bearer_methods_supported", List.of("header"),
                "scopes_supported", mcpOAuthProperties.getScopes()
        );
    }
}

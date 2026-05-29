package com.jobtracker.controller;

import com.jobtracker.config.GptOAuthProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class GptOAuthDiscoveryController {

    private final GptOAuthProperties properties;

    public GptOAuthDiscoveryController(GptOAuthProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> openidConfiguration() {
        String issuer = normalizedIssuer();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(Map.of(
                        "issuer", issuer,
                        "authorization_endpoint", issuer + "/oauth2/authorize",
                        "token_endpoint", issuer + "/oauth2/token",
                        "jwks_uri", issuer + "/oauth2/jwks",
                        "response_types_supported", List.of("code"),
                        "grant_types_supported", List.of("authorization_code"),
                        "subject_types_supported", List.of("public"),
                        "token_endpoint_auth_methods_supported", List.of("client_secret_post", "client_secret_basic"),
                        "code_challenge_methods_supported", List.of("S256"),
                        "scopes_supported", properties.getScopes()
                ));
    }

    @GetMapping("/oauth2/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(Map.of("keys", List.of()));
    }

    private String normalizedIssuer() {
        String issuer = properties.getIssuer();
        if (issuer.endsWith("/")) {
            return issuer.substring(0, issuer.length() - 1);
        }
        return issuer;
    }
}

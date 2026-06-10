package com.jobtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("#{'${cors.allowed-origins}'.split('\\s*,\\s*')}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        // OAuth endpoints used by browser-based public clients. ChatGPT runs the PKCE
        // code exchange from chatgpt.com in the user's browser, so the token endpoint
        // must answer CORS preflights from arbitrary origins (the MCP inspector on
        // localhost needs the same). These endpoints never authenticate via cookies —
        // security comes from the authorization code + PKCE — so a wildcard origin
        // without credentials is the standard posture for them (Auth0/Okta do the same).
        CorsConfiguration oauthConfiguration = new CorsConfiguration();
        oauthConfiguration.setAllowedOriginPatterns(List.of("*"));
        oauthConfiguration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        oauthConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        oauthConfiguration.setAllowCredentials(false);
        oauthConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Specific patterns must be registered before /** — first match wins.
        source.registerCorsConfiguration("/oauth2/token", oauthConfiguration);
        source.registerCorsConfiguration("/oauth2/revoke", oauthConfiguration);
        source.registerCorsConfiguration("/oauth2/jwks", oauthConfiguration);
        source.registerCorsConfiguration("/userinfo", oauthConfiguration);
        source.registerCorsConfiguration("/connect/register", oauthConfiguration);
        source.registerCorsConfiguration("/.well-known/**", oauthConfiguration);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

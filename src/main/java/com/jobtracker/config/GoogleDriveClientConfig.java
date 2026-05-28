package com.jobtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Configures the Spring OAuth2 client infrastructure used by the Google Drive integration.
 * <p>
 * The {@link ClientRegistration} bean captures all Google OAuth2 parameters in one place so that
 * Spring's standard {@link RestClientAuthorizationCodeTokenResponseClient} and
 * {@link RestClientRefreshTokenTokenResponseClient} can handle token exchange and refresh without any
 * manual HTTP calls.
 * <p>
 * <b>How the Spring Security context provides the authorized client:</b><br>
 * When a user completes the OAuth2 consent flow, {@code GoogleDriveOAuthService.handleCallback()}
 * calls {@code authorizationCodeTokenResponseClient.getTokenResponse(grantRequest)}.  The resulting
 * access and refresh tokens are stored in our {@code google_drive_connections} table via
 * {@code GoogleDriveConnectionRepository}.  For every subsequent Drive API call,
 * {@code GoogleDriveService} loads the connection, refreshes the token if needed using
 * {@code refreshTokenResponseClient}, and passes the fresh access token to {@link
 * com.jobtracker.service.DriveClientFactory#create} – which wraps it in a Google
 * {@link com.google.auth.oauth2.OAuth2Credentials} so the SDK can authenticate automatically.
 */
@Configuration
public class GoogleDriveClientConfig {
    private static final String DISABLED_CLIENT_ID = "google-drive-disabled-client";
    private static final String DISABLED_CLIENT_SECRET = "google-drive-disabled-secret";
    private static final String DISABLED_REDIRECT_URI = "http://localhost/google-drive-disabled";

    /**
     * The {@link ClientRegistration} for Google Drive. Built programmatically from
     * {@link GoogleDriveProperties} so we have a single source of truth for OAuth2 parameters.
     * Note: a {@code ClientRegistrationRepository} bean is intentionally <em>not</em> created;
     * that would trigger Spring Security's OAuth2 login autoconfiguration which is incompatible
     * with this application's stateless JWT architecture.
     */
    @Bean
    public ClientRegistration googleDriveClientRegistration(GoogleDriveProperties properties) {
        // Keep the application bootable even when Google credentials are absent.
        // Runtime operations are still blocked by GoogleDriveProperties#validateConfigured().
        String clientId = properties.isConfigured() ? properties.getClientId() : DISABLED_CLIENT_ID;
        String clientSecret = properties.isConfigured() ? properties.getClientSecret() : DISABLED_CLIENT_SECRET;
        String redirectUri = properties.isConfigured() ? properties.getRedirectUri() : DISABLED_REDIRECT_URI;

        return ClientRegistration.withRegistrationId("google-drive")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope(properties.getScopes().toArray(new String[0]))
                .authorizationUri(properties.getAuthorizationUri())
                .tokenUri(properties.getTokenUri())
                .build();
    }

    /**
     * Token response client for the Authorization Code grant. Replaces manual RestClient HTTP
     * calls with Spring Security's type-safe implementation. Configured with the same timeouts
     * used elsewhere in the application.
     */
    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeTokenResponseClient() {
        return new RestClientAuthorizationCodeTokenResponseClient();
    }

    /**
     * Token response client for the Refresh Token grant. Used by {@code GoogleDriveService} to
     * silently renew expired access tokens before each Drive API call.
     */
    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenResponseClient() {
        return new RestClientRefreshTokenTokenResponseClient();
    }
}

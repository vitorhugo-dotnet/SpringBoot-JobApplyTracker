package com.jobtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration for the GitHub account referenced by the Developer Tools &rarr; Source Code card.
 * <p>
 * The <b>numeric user ID</b> ({@code app.github.user-id}) is the canonical identity of the
 * integration: GitHub lets an account rename its {@code login} at any time, but the numeric ID never
 * changes. The optional {@code app.github.login} is kept only as a bootstrap value for legacy
 * setups that were configured before the ID existed (see
 * {@link com.jobtracker.service.GitHubProfileService}) and as a last-resort presentation fallback
 * when the GitHub API cannot be reached.
 */
@Component
public class GitHubProperties {

    private final Long userId;
    private final String login;
    private final String apiBaseUrl;
    private final String token;
    private final Duration cacheTtl;
    private final Duration timeout;

    public GitHubProperties(
            @Value("${app.github.user-id:}") Long userId,
            @Value("${app.github.login:}") String login,
            @Value("${app.github.api-base-url:https://api.github.com}") String apiBaseUrl,
            @Value("${app.github.token:}") String token,
            @Value("${app.github.cache-ttl-seconds:3600}") long cacheTtlSeconds,
            @Value("${app.github.timeout-ms:5000}") long timeoutMs
    ) {
        this.userId = userId;
        this.login = normalizeLogin(login);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
        this.token = token == null ? "" : token.trim();
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /** Stable GitHub numeric user ID, or {@code null} when only a legacy username is configured. */
    public Long getUserId() {
        return userId;
    }

    /** Legacy/bootstrap username. Presentation data only - never the identity of the integration. */
    public String getLogin() {
        return login;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getToken() {
        return token;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean hasToken() {
        return !token.isBlank();
    }

    /** The integration needs at least one of the two ways to reach the account. */
    public boolean isConfigured() {
        return userId != null || !login.isBlank();
    }

    /**
     * Accepts a bare login ({@code vitorhugo-dotnet}) as well as a full profile URL
     * ({@code https://github.com/vitorhugo-dotnet}), which is how the frontend used to be configured.
     */
    private String normalizeLogin(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash >= 0) {
            trimmed = trimmed.substring(lastSlash + 1);
        }
        return trimmed;
    }

    private String stripTrailingSlash(String value) {
        String base = value == null || value.isBlank() ? "https://api.github.com" : value.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}

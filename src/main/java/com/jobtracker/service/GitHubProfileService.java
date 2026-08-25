package com.jobtracker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobtracker.config.GitHubProperties;
import com.jobtracker.dto.github.GitHubProfileResponse;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the GitHub account referenced by the Developer Tools &rarr; Source Code card.
 * <p>
 * The account is addressed by its <b>stable numeric user ID</b>, never by its username: a GitHub
 * user can rename their {@code login} at any time, which would silently break any persisted
 * username-based reference. The current {@code login} and {@code html_url} are always taken from
 * GitHub's answer, so renaming the account keeps the <i>View on GitHub</i> link working without any
 * reconfiguration.
 * <p>
 * <b>Legacy migration:</b> installations configured before the ID existed only carry
 * {@code app.github.login}. The first lookup then resolves that username once through
 * {@code GET /users/{login}}, remembers the numeric ID it returns, and every later lookup goes
 * through {@code GET /user/{id}} like a natively configured installation.
 */
@Service
public class GitHubProfileService {

    private static final Logger log = LoggerFactory.getLogger(GitHubProfileService.class);
    private static final String ACCEPT_HEADER = "application/vnd.github+json";
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2022-11-28";

    private final GitHubProperties properties;
    private final RestClient restClient;
    private final Clock clock;

    /** Numeric ID discovered from a legacy username-only configuration. */
    private final AtomicReference<Long> migratedUserId = new AtomicReference<>();
    private final AtomicReference<CachedProfile> cache = new AtomicReference<>();

    @Autowired
    public GitHubProfileService(GitHubProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, restClientBuilder.requestFactory(requestFactory(properties)).build(), Clock.systemUTC());
    }

    /** Explicit wiring, used by tests to supply a stubbed {@link RestClient} and a controllable {@link Clock}. */
    public GitHubProfileService(GitHubProperties properties, RestClient restClient, Clock clock) {
        this.properties = properties;
        this.restClient = restClient;
        this.clock = clock;
    }

    /**
     * @return the current profile of the configured account.
     * @throws ResourceNotFoundException    when no GitHub account is configured at all.
     * @throws ServiceUnavailableException  when GitHub is unreachable and nothing can be served from cache.
     */
    public GitHubProfileResponse getProfile() {
        if (!properties.isConfigured()) {
            throw new ResourceNotFoundException("No GitHub account is configured on the server");
        }

        CachedProfile cached = cache.get();
        Instant now = clock.instant();
        if (cached != null && cached.isFresh(now, properties.getCacheTtl())) {
            return cached.profile();
        }

        Long userId = canonicalUserId();
        try {
            GitHubUser user = userId != null ? fetchById(userId) : fetchByLogin(properties.getLogin());
            return cacheAndReturn(user, now);
        } catch (RestClientException e) {
            log.warn("Failed to resolve GitHub profile (userId={}, login={}): {}",
                    userId, properties.getLogin(), e.getMessage());
            return fallback(cached, userId);
        }
    }

    /** The configured ID, or the one recovered once from a legacy username-only configuration. */
    private Long canonicalUserId() {
        Long configured = properties.getUserId();
        return configured != null ? configured : migratedUserId.get();
    }

    private GitHubUser fetchById(long userId) {
        return get("/user/" + userId);
    }

    private GitHubUser fetchByLogin(String login) {
        GitHubUser user = get("/users/" + login);
        if (user != null && user.id() != null) {
            // Migration step: from now on this installation is addressed by its stable ID.
            migratedUserId.set(user.id());
            log.info("Resolved legacy GitHub username '{}' to stable user ID {}; subsequent lookups use the ID",
                    login, user.id());
        }
        return user;
    }

    private GitHubUser get(String path) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri(properties.getApiBaseUrl() + path)
                .header("Accept", ACCEPT_HEADER)
                .header(API_VERSION_HEADER, API_VERSION);
        if (properties.hasToken()) {
            request = request.header("Authorization", "Bearer " + properties.getToken());
        }
        return request.retrieve().body(GitHubUser.class);
    }

    private GitHubProfileResponse cacheAndReturn(GitHubUser user, Instant now) {
        if (user == null || user.id() == null || user.login() == null || user.login().isBlank()) {
            throw new RestClientException("GitHub returned an incomplete user payload");
        }
        String htmlUrl = user.htmlUrl() != null && !user.htmlUrl().isBlank()
                ? user.htmlUrl()
                : "https://github.com/" + user.login();
        GitHubProfileResponse profile = new GitHubProfileResponse(
                user.id(), user.login(), htmlUrl, user.avatarUrl(), false);
        cache.set(new CachedProfile(profile, now));
        return profile;
    }

    /**
     * Never fail just because GitHub is momentarily unreachable: serve the last known profile, or the
     * configured username as a presentation-only fallback. Both are flagged {@code stale}. A numeric
     * ID is never turned into a guessed web URL - only GitHub can map it to a profile.
     */
    private GitHubProfileResponse fallback(CachedProfile cached, Long userId) {
        if (cached != null) {
            GitHubProfileResponse profile = cached.profile();
            return new GitHubProfileResponse(
                    profile.userId(), profile.login(), profile.htmlUrl(), profile.avatarUrl(), true);
        }
        String login = properties.getLogin();
        if (!login.isBlank()) {
            return new GitHubProfileResponse(userId, login, "https://github.com/" + login, null, true);
        }
        throw new ServiceUnavailableException("GitHub profile could not be resolved");
    }

    private static SimpleClientHttpRequestFactory requestFactory(GitHubProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeout());
        factory.setReadTimeout(properties.getTimeout());
        return factory;
    }

    private record CachedProfile(GitHubProfileResponse profile, Instant fetchedAt) {
        boolean isFresh(Instant now, java.time.Duration ttl) {
            return fetchedAt.plus(ttl).isAfter(now);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubUser(
            Long id,
            String login,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("avatar_url") String avatarUrl
    ) {}
}

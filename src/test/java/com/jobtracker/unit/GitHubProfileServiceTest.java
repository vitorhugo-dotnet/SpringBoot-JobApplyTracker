package com.jobtracker.unit;

import com.jobtracker.config.GitHubProperties;
import com.jobtracker.dto.github.GitHubProfileResponse;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.exception.ServiceUnavailableException;
import com.jobtracker.service.GitHubProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubProfileServiceTest {

    private static final String API = "https://api.github.test";

    private static final String USER_JSON = """
            {
              "login": "vitorhugo-dotnet",
              "id": 65777252,
              "html_url": "https://github.com/vitorhugo-dotnet",
              "avatar_url": "https://avatars.githubusercontent.com/u/65777252?v=4"
            }
            """;

    private static final String RENAMED_USER_JSON = """
            {
              "login": "vitorhugo-renamed",
              "id": 65777252,
              "html_url": "https://github.com/vitorhugo-renamed",
              "avatar_url": "https://avatars.githubusercontent.com/u/65777252?v=4"
            }
            """;

    @Test
    void shouldResolveProfileFromStableUserId() {
        Fixture fixture = fixture(properties(65777252L, ""), Clock.systemUTC());
        fixture.server.expect(requestTo(API + "/user/65777252"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

        GitHubProfileResponse profile = fixture.service.getProfile();

        assertThat(profile.userId()).isEqualTo(65777252L);
        assertThat(profile.login()).isEqualTo("vitorhugo-dotnet");
        assertThat(profile.htmlUrl()).isEqualTo("https://github.com/vitorhugo-dotnet");
        assertThat(profile.avatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/65777252?v=4");
        assertThat(profile.stale()).isFalse();
        fixture.server.verify();
    }

    @Test
    void shouldFollowUsernameChangeBecauseLookupGoesThroughTheId() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T10:00:00Z"));
        Fixture fixture = fixture(properties(65777252L, "vitorhugo-dotnet"), clock);
        fixture.server.expect(requestTo(API + "/user/65777252"))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(API + "/user/65777252"))
                .andRespond(withSuccess(RENAMED_USER_JSON, MediaType.APPLICATION_JSON));

        assertThat(fixture.service.getProfile().login()).isEqualTo("vitorhugo-dotnet");

        clock.advance(Duration.ofHours(2));
        GitHubProfileResponse afterRename = fixture.service.getProfile();

        assertThat(afterRename.userId()).isEqualTo(65777252L);
        assertThat(afterRename.login()).isEqualTo("vitorhugo-renamed");
        assertThat(afterRename.htmlUrl()).isEqualTo("https://github.com/vitorhugo-renamed");
        fixture.server.verify();
    }

    @Test
    void shouldMigrateLegacyUsernameOnlyConfigurationToTheNumericId() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T10:00:00Z"));
        Fixture fixture = fixture(properties(null, "vitorhugo-dotnet"), clock);
        fixture.server.expect(requestTo(API + "/users/vitorhugo-dotnet"))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));
        // The second lookup must already use the ID recovered from the first one.
        fixture.server.expect(requestTo(API + "/user/65777252"))
                .andRespond(withSuccess(RENAMED_USER_JSON, MediaType.APPLICATION_JSON));

        assertThat(fixture.service.getProfile().userId()).isEqualTo(65777252L);

        clock.advance(Duration.ofHours(2));
        assertThat(fixture.service.getProfile().login()).isEqualTo("vitorhugo-renamed");
        fixture.server.verify();
    }

    @Test
    void shouldServeCachedProfileWithinTtlWithoutCallingGitHubAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T10:00:00Z"));
        Fixture fixture = fixture(properties(65777252L, ""), clock);
        fixture.server.expect(requestTo(API + "/user/65777252"))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

        fixture.service.getProfile();
        clock.advance(Duration.ofMinutes(30));
        GitHubProfileResponse cached = fixture.service.getProfile();

        assertThat(cached.login()).isEqualTo("vitorhugo-dotnet");
        assertThat(cached.stale()).isFalse();
        fixture.server.verify();
    }

    @Test
    void shouldServeLastKnownProfileAsStaleWhenGitHubFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T10:00:00Z"));
        Fixture fixture = fixture(properties(65777252L, ""), clock);
        fixture.server.expect(requestTo(API + "/user/65777252"))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(API + "/user/65777252")).andRespond(withServerError());

        fixture.service.getProfile();
        clock.advance(Duration.ofHours(2));
        GitHubProfileResponse stale = fixture.service.getProfile();

        assertThat(stale.login()).isEqualTo("vitorhugo-dotnet");
        assertThat(stale.htmlUrl()).isEqualTo("https://github.com/vitorhugo-dotnet");
        assertThat(stale.stale()).isTrue();
        fixture.server.verify();
    }

    @Test
    void shouldFallBackToConfiguredLoginWhenGitHubFailsAndNothingIsCached() {
        Fixture fixture = fixture(properties(65777252L, "vitorhugo-dotnet"), Clock.systemUTC());
        fixture.server.expect(requestTo(API + "/user/65777252")).andRespond(withServerError());

        GitHubProfileResponse fallback = fixture.service.getProfile();

        assertThat(fallback.userId()).isEqualTo(65777252L);
        assertThat(fallback.login()).isEqualTo("vitorhugo-dotnet");
        assertThat(fallback.htmlUrl()).isEqualTo("https://github.com/vitorhugo-dotnet");
        assertThat(fallback.stale()).isTrue();
        fixture.server.verify();
    }

    @Test
    void shouldFailWithServiceUnavailableWhenGitHubFailsAndNoFallbackExists() {
        Fixture fixture = fixture(properties(65777252L, ""), Clock.systemUTC());
        fixture.server.expect(requestTo(API + "/user/65777252")).andRespond(withServerError());

        assertThatThrownBy(fixture.service::getProfile)
                .isInstanceOf(ServiceUnavailableException.class);
        fixture.server.verify();
    }

    @Test
    void shouldFailWithNotFoundWhenNoAccountIsConfigured() {
        Fixture fixture = fixture(properties(null, ""), Clock.systemUTC());

        assertThatThrownBy(fixture.service::getProfile)
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldAcceptALegacyProfileUrlAsLoginConfiguration() {
        GitHubProperties properties = properties(null, "https://github.com/vitorhugo-dotnet");

        assertThat(properties.getLogin()).isEqualTo("vitorhugo-dotnet");
        assertThat(properties.isConfigured()).isTrue();
    }

    private GitHubProperties properties(Long userId, String login) {
        return new GitHubProperties(userId, login, API + "/", "", 3600, 5000);
    }

    private Fixture fixture(GitHubProperties properties, Clock clock) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new GitHubProfileService(properties, builder.build(), clock), server);
    }

    private record Fixture(GitHubProfileService service, MockRestServiceServer server) {}

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

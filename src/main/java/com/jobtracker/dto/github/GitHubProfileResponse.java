package com.jobtracker.dto.github;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "GitHub account backing the Developer Tools source-code card, resolved from its stable numeric user ID")
public record GitHubProfileResponse(

        @Schema(description = "Stable GitHub numeric user ID - the canonical identity of the integration", example = "65777252")
        Long userId,

        @Schema(description = "Current GitHub login as returned by GitHub. Presentation data only, may change at any time", example = "vitorhugo-dotnet")
        String login,

        @Schema(description = "Current profile URL as returned by GitHub", example = "https://github.com/vitorhugo-dotnet")
        String htmlUrl,

        @Schema(description = "Current avatar URL as returned by GitHub", example = "https://avatars.githubusercontent.com/u/65777252?v=4")
        String avatarUrl,

        @Schema(description = "True when GitHub could not be reached and a cached or configured fallback was served instead")
        boolean stale
) {}

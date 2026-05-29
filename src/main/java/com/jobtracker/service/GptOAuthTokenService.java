package com.jobtracker.service;

import com.jobtracker.config.GptOAuthProperties;
import com.jobtracker.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class GptOAuthTokenService {

    private final JwtEncoder gptOAuthJwtEncoder;
    private final GptOAuthProperties properties;

    public GptOAuthTokenService(JwtEncoder gptOAuthJwtEncoder, GptOAuthProperties properties) {
        this.gptOAuthJwtEncoder = gptOAuthJwtEncoder;
        this.properties = properties;
    }

    public IssuedAccessToken issueAccessToken(User user, Set<String> scopes) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getAccessTokenExpirationSeconds());

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(user.getEmail())
                .audience(List.of(properties.getAudience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", String.join(" ", scopes))
                .claim("roles", user.getRoles().stream()
                        .map(role -> "ROLE_" + role.getName().name())
                        .toList())
                .claim("user_id", user.getId().toString())
                .claim("token_use", "gpt_action_access")
                .build();

        String tokenValue = gptOAuthJwtEncoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claimsSet
                )
        ).getTokenValue();

        return new IssuedAccessToken(tokenValue, properties.getAccessTokenExpirationSeconds(), String.join(" ", scopes));
    }

    public record IssuedAccessToken(String tokenValue, long expiresIn, String scopeValue) {
    }
}

package com.taskflow.service;

import com.taskflow.config.JwtProperties;
import com.taskflow.exception.InvalidTokenException;
import com.taskflow.model.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-only-jwt-signing-secret-key-at-least-32-bytes";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void generateAndValidateTokenRoundTripsClaims() {
        Instant fixedNow = Instant.parse("2026-11-25T15:00:00Z");
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        JwtService jwtService = new JwtService(new JwtProperties(TEST_SECRET, 3_600_000L), jsonMapper, clock);

        User user = new User("Alice", "alice@example.com", "hashed");
        user.setId("user-123");

        JwtService.GeneratedToken generated = jwtService.generateToken(user);
        JwtService.JwtClaims claims = jwtService.parseAndValidate(generated.token());

        assertThat(claims.userId()).isEqualTo("user-123");
        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.issuedAt()).isEqualTo(fixedNow);
        assertThat(claims.expiresAt()).isEqualTo(fixedNow.plusSeconds(3600));
    }

    @Test
    void parseAndValidateRejectsTamperedToken() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC);
        JwtService jwtService = new JwtService(new JwtProperties(TEST_SECRET, 3_600_000L), jsonMapper, clock);

        User user = new User("Alice", "alice@example.com", "hashed");
        user.setId("user-123");

        String token = jwtService.generateToken(user).token();
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parseAndValidateRejectsExpiredToken() {
        Instant issuedTime = Instant.parse("2026-11-25T15:00:00Z");
        JwtService issuer = new JwtService(
                new JwtProperties(TEST_SECRET, 60_000L),
                jsonMapper,
                Clock.fixed(issuedTime, ZoneOffset.UTC));

        User user = new User("Alice", "alice@example.com", "hashed");
        user.setId("user-123");
        String token = issuer.generateToken(user).token();

        JwtService verifierAfterExpiry = new JwtService(
                new JwtProperties(TEST_SECRET, 60_000L),
                jsonMapper,
                Clock.fixed(issuedTime.plusSeconds(120), ZoneOffset.UTC));

        assertThatThrownBy(() -> verifierAfterExpiry.parseAndValidate(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void constructorRejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short-secret", 3_600_000L), jsonMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}

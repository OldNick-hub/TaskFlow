package com.taskflow.service;

import com.taskflow.config.JwtProperties;
import com.taskflow.exception.InvalidTokenException;
import com.taskflow.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates and validates HMAC-SHA256 JWT access tokens using an externalized signing secret.
 */
@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SecretKeySpec signingKey;
    private final long expirationMs;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        this(jwtProperties, objectMapper, Clock.systemUTC());
    }

    JwtService(JwtProperties jwtProperties, ObjectMapper objectMapper, Clock clock) {
        if (jwtProperties == null || jwtProperties.secret() == null || jwtProperties.secret().isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable must be configured");
        }
        byte[] secretBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HMAC-SHA256");
        }
        if (jwtProperties.expirationMs() <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than 0 ms");
        }
        this.signingKey = new SecretKeySpec(secretBytes, HMAC_ALGORITHM);
        this.expirationMs = jwtProperties.expirationMs();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public GeneratedToken generateToken(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusMillis(expirationMs);
        String tokenId = UUID.randomUUID().toString();

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getId());
        payload.put("email", user.getEmail());
        payload.put("jti", tokenId);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        try {
            String encodedHeader = encodeJson(header);
            String encodedPayload = encodeJson(payload);
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = BASE64_URL_ENCODER.encodeToString(sign(signingInput));
            long expiresInSeconds = Math.max(1L, expirationMs / 1000L);
            return new GeneratedToken(signingInput + "." + signature, tokenId, expiresAt, expiresInSeconds);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate JWT access token", ex);
        }
    }

    public JwtClaims parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Missing access token");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new InvalidTokenException("Invalid access token");
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSignature = sign(signingInput);
        byte[] actualSignature;
        try {
            actualSignature = BASE64_URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid access token");
        }

        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new InvalidTokenException("Invalid access token");
        }

        Map<String, Object> header = decodeJson(parts[0]);
        if (!"HS256".equals(header.get("alg")) || !"JWT".equals(header.get("typ"))) {
            throw new InvalidTokenException("Invalid access token");
        }

        Map<String, Object> claims = decodeJson(parts[1]);
        String userId = requireNonBlankString(claims.get("sub"));
        String email = requireNonBlankString(claims.get("email"));
        String tokenId = requireNonBlankString(claims.get("jti"));
        Instant issuedAt = Instant.ofEpochSecond(requireEpochSeconds(claims.get("iat")));
        Instant expiresAt = Instant.ofEpochSecond(requireEpochSeconds(claims.get("exp")));

        Instant now = clock.instant();
        if (!expiresAt.isAfter(now)) {
            throw new InvalidTokenException("Access token has expired");
        }

        return new JwtClaims(userId, email, tokenId, issuedAt, expiresAt);
    }

    private String encodeJson(Map<String, Object> value) {
        String json = objectMapper.writeValueAsString(value);
        return BASE64_URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> decodeJson(String encodedPart) {
        try {
            byte[] decoded = BASE64_URL_DECODER.decode(encodedPart);
            return objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), MAP_TYPE);
        } catch (Exception ex) {
            throw new InvalidTokenException("Invalid access token");
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256 signature", ex);
        }
    }

    private String requireNonBlankString(Object value) {
        if (!(value instanceof String str) || str.isBlank()) {
            throw new InvalidTokenException("Invalid access token");
        }
        return str;
    }

    private long requireEpochSeconds(Object value) {
        if (!(value instanceof Number number)) {
            throw new InvalidTokenException("Invalid access token");
        }
        return number.longValue();
    }

    public record GeneratedToken(String token, String tokenId, Instant expiresAt, long expiresInSeconds) {
    }

    public record JwtClaims(String userId, String email, String tokenId, Instant issuedAt, Instant expiresAt) {
    }
}

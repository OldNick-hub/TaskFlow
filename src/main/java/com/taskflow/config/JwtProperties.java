package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized JWT configuration properties bound from environment variables.
 */
@ConfigurationProperties(prefix = "taskflow.security.jwt")
public record JwtProperties(
        String secret,
        long expirationMs
) {
}

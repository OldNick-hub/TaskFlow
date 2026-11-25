package com.taskflow.dto;

/**
 * Response payload returned upon successful authentication.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}

package com.taskflow.dto;

import com.taskflow.model.User;

import java.time.Instant;

/**
 * Public user representation excluding sensitive fields such as password hashes.
 */
public record UserResponse(
        String id,
        String name,
        String email,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}

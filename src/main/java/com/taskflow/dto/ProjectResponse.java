package com.taskflow.dto;

import com.taskflow.model.Project;

import java.time.Instant;

/**
 * API representation of a TaskFlow project.
 */
public record ProjectResponse(
        String id,
        String name,
        String description,
        String ownerId,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwnerId(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}

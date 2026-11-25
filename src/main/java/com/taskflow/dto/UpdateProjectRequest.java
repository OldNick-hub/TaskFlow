package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating an existing project.
 */
public record UpdateProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(min = 1, max = 150, message = "Project name must not exceed 150 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description
) {
}

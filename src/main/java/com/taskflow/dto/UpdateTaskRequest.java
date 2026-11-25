package com.taskflow.dto;

import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for updating an existing task's details.
 */
public record UpdateTaskRequest(
        @NotBlank(message = "Task title is required")
        @Size(min = 1, max = 200, message = "Task title must not exceed 200 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        Instant dueDate
) {
}

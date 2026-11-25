package com.taskflow.dto;

import com.taskflow.model.TaskPriority;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for changing a task's priority.
 */
public record UpdateTaskPriorityRequest(
        @NotNull(message = "Task priority is required")
        TaskPriority priority
) {
}

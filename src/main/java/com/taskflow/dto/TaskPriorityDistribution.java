package com.taskflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Task count breakdown across all priority levels.
 */
public record TaskPriorityDistribution(
        @JsonProperty("LOW") long low,
        @JsonProperty("MEDIUM") long medium,
        @JsonProperty("HIGH") long high,
        @JsonProperty("URGENT") long urgent
) {
    public static TaskPriorityDistribution empty() {
        return new TaskPriorityDistribution(0L, 0L, 0L, 0L);
    }
}

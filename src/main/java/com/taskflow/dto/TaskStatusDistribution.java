package com.taskflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Task count breakdown across all workflow statuses.
 */
public record TaskStatusDistribution(
        @JsonProperty("TODO") long todo,
        @JsonProperty("IN_PROGRESS") long inProgress,
        @JsonProperty("IN_REVIEW") long inReview,
        @JsonProperty("DONE") long done
) {
    public static TaskStatusDistribution empty() {
        return new TaskStatusDistribution(0L, 0L, 0L, 0L);
    }
}

package com.taskflow.dto;

/**
 * Sanitized database connectivity status returned by health verification endpoints.
 * Contains no connection strings, hostnames, or credentials.
 */
public record DatabaseHealthResponse(
        String status,
        String database,
        boolean reachable
) {
}

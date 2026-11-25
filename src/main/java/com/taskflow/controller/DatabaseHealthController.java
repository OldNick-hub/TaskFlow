package com.taskflow.controller;

import com.taskflow.dto.DatabaseHealthResponse;
import com.taskflow.service.DatabaseHealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes a safe, credential-free database connectivity verification endpoint.
 */
@RestController
@RequestMapping("/api/health")
public class DatabaseHealthController {

    private final DatabaseHealthService databaseHealthService;

    public DatabaseHealthController(DatabaseHealthService databaseHealthService) {
        this.databaseHealthService = databaseHealthService;
    }

    @GetMapping("/db")
    public ResponseEntity<DatabaseHealthResponse> checkDatabaseHealth() {
        DatabaseHealthResponse response = databaseHealthService.checkConnectivity();
        HttpStatus status = response.reachable() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }
}

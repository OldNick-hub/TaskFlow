package com.taskflow.controller;

import com.taskflow.config.AuthenticatedUser;
import com.taskflow.dto.DashboardResponse;
import com.taskflow.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint providing aggregated analytics for the authenticated user's dashboard.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(dashboardService.getDashboardAnalytics(currentUser));
    }
}

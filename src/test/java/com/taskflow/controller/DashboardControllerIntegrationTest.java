package com.taskflow.controller;

import com.taskflow.config.JwtAuthenticationFilter;
import com.taskflow.config.SecurityConfig;
import com.taskflow.exception.GlobalExceptionHandler;
import com.taskflow.model.Project;
import com.taskflow.model.Task;
import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import com.taskflow.model.User;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.DashboardService;
import com.taskflow.service.JwtService;
import com.taskflow.service.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        TokenRevocationService.class,
        DashboardService.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "taskflow.security.jwt.secret=test-only-jwt-signing-secret-key-at-least-32-bytes",
        "taskflow.security.jwt.expiration-ms=3600000"
})
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ProjectRepository projectRepository;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private RevokedTokenRepository revokedTokenRepository;

    private String userOneToken;
    private String userTwoToken;

    @BeforeEach
    void setUp() {
        when(revokedTokenRepository.existsByTokenId(anyString())).thenReturn(false);

        User userOne = new User("Alice", "alice@example.com", "hashed");
        userOne.setId("user-1");
        userOneToken = jwtService.generateToken(userOne).token();

        User userTwo = new User("Bob", "bob@example.com", "hashed");
        userTwo.setId("user-2");
        userTwoToken = jwtService.generateToken(userTwo).token();
    }

    @Test
    void dashboardRequiresValidNonRevokedJwt() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));

        when(revokedTokenRepository.existsByTokenId(anyString())).thenReturn(true);
        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has been revoked"));
    }

    @Test
    void dashboardReturnsScopedAnalyticsAndIsolatesUsers() throws Exception {
        Project p1 = new Project("Alpha", "First", "user-1");
        p1.setId("proj-1");

        Task t1 = new Task("T1", null, TaskStatus.DONE, TaskPriority.HIGH,
                Instant.now().minusSeconds(3600), "proj-1");
        Task t2 = new Task("T2", null, TaskStatus.IN_PROGRESS, TaskPriority.URGENT,
                Instant.now().minusSeconds(3600), "proj-1");
        Task t3 = new Task("T3", null, TaskStatus.IN_REVIEW, TaskPriority.MEDIUM,
                Instant.now().plusSeconds(86400), "proj-1");
        Task t4 = new Task("T4", null, TaskStatus.TODO, TaskPriority.LOW,
                null, "proj-1");

        when(projectRepository.findByOwnerId("user-1")).thenReturn(List.of(p1));
        when(taskRepository.findByProjectIdIn(List.of("proj-1"))).thenReturn(List.of(t1, t2, t3, t4));
        when(projectRepository.findByOwnerId("user-2")).thenReturn(List.of());

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").value(1))
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.pendingTasks").value(3))
                .andExpect(jsonPath("$.inProgressTasks").value(1))
                .andExpect(jsonPath("$.overdueTasks").value(1))
                .andExpect(jsonPath("$.completionPercentage").value(25.0))
                .andExpect(jsonPath("$.statusDistribution.TODO").value(1))
                .andExpect(jsonPath("$.statusDistribution.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.statusDistribution.IN_REVIEW").value(1))
                .andExpect(jsonPath("$.statusDistribution.DONE").value(1))
                .andExpect(jsonPath("$.priorityDistribution.LOW").value(1))
                .andExpect(jsonPath("$.priorityDistribution.MEDIUM").value(1))
                .andExpect(jsonPath("$.priorityDistribution.HIGH").value(1))
                .andExpect(jsonPath("$.priorityDistribution.URGENT").value(1));

        verify(projectRepository).findByOwnerId("user-1");

        // Second user only sees their own empty metrics, never user-1's data
        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").value(0))
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.completedTasks").value(0))
                .andExpect(jsonPath("$.pendingTasks").value(0))
                .andExpect(jsonPath("$.inProgressTasks").value(0))
                .andExpect(jsonPath("$.overdueTasks").value(0))
                .andExpect(jsonPath("$.completionPercentage").value(0.0))
                .andExpect(jsonPath("$.statusDistribution.TODO").value(0))
                .andExpect(jsonPath("$.statusDistribution.IN_PROGRESS").value(0))
                .andExpect(jsonPath("$.statusDistribution.IN_REVIEW").value(0))
                .andExpect(jsonPath("$.statusDistribution.DONE").value(0))
                .andExpect(jsonPath("$.priorityDistribution.LOW").value(0))
                .andExpect(jsonPath("$.priorityDistribution.MEDIUM").value(0))
                .andExpect(jsonPath("$.priorityDistribution.HIGH").value(0))
                .andExpect(jsonPath("$.priorityDistribution.URGENT").value(0));

        verify(projectRepository).findByOwnerId("user-2");
    }

    @Test
    void dashboardHandlesProjectsWithZeroTasks() throws Exception {
        Project p1 = new Project("Empty", null, "user-1");
        p1.setId("proj-1");

        when(projectRepository.findByOwnerId("user-1")).thenReturn(List.of(p1));
        when(taskRepository.findByProjectIdIn(List.of("proj-1"))).thenReturn(List.of());

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").value(1))
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.completionPercentage").value(0.0))
                .andExpect(jsonPath("$.statusDistribution.DONE").value(0))
                .andExpect(jsonPath("$.priorityDistribution.MEDIUM").value(0));
    }
}

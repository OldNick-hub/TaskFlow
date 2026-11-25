package com.taskflow.controller;

import com.taskflow.config.JwtAuthenticationFilter;
import com.taskflow.config.SecurityConfig;
import com.taskflow.exception.GlobalExceptionHandler;
import com.taskflow.model.Project;
import com.taskflow.model.User;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.service.JwtService;
import com.taskflow.service.ProjectService;
import com.taskflow.service.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        TokenRevocationService.class,
        ProjectService.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "taskflow.security.jwt.secret=test-only-jwt-signing-secret-key-at-least-32-bytes",
        "taskflow.security.jwt.expiration-ms=3600000"
})
class ProjectControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ProjectRepository projectRepository;

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
    void projectEndpointsRequireValidNonRevokedJwt() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects/proj-1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/projects/proj-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/projects/proj-1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));

        when(revokedTokenRepository.existsByTokenId(anyString())).thenReturn(true);
        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has been revoked"));
    }

    @Test
    void createProjectUsesAuthenticatedPrincipalAndIgnoresClientOwnerId() throws Exception {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            assertThat(project.getOwnerId()).isEqualTo("user-1");
            project.setId("proj-1");
            project.setCreatedAt(Instant.parse("2026-11-25T16:00:00Z"));
            project.setUpdatedAt(Instant.parse("2026-11-25T16:00:00Z"));
            return project;
        });

        String payload = """
                {
                  "name": "Roadmap Q1",
                  "description": "Deliver project API",
                  "ownerId": "user-2"
                }
                """;

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("proj-1"))
                .andExpect(jsonPath("$.name").value("Roadmap Q1"))
                .andExpect(jsonPath("$.description").value("Deliver project API"))
                .andExpect(jsonPath("$.ownerId").value("user-1"));
    }

    @Test
    void createAndUpdateRejectInvalidPayloads() throws Exception {
        String invalidPayload = """
                {
                  "name": "   ",
                  "description": "Valid description"
                }
                """;

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());

        mockMvc.perform(put("/api/projects/proj-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    void getProjectsReturnsOnlyLoggedInUserProjects() throws Exception {
        Project p1 = new Project("Alpha", "First", "user-1");
        p1.setId("proj-1");
        p1.setCreatedAt(Instant.parse("2026-11-25T15:00:00Z"));

        Project p2 = new Project("Beta", "Second", "user-1");
        p2.setId("proj-2");
        p2.setCreatedAt(Instant.parse("2026-11-25T16:00:00Z"));

        when(projectRepository.findByOwnerIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(p2, p1));

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("proj-2"))
                .andExpect(jsonPath("$[0].ownerId").value("user-1"))
                .andExpect(jsonPath("$[1].id").value("proj-1"))
                .andExpect(jsonPath("$[1].ownerId").value("user-1"));

        verify(projectRepository).findByOwnerIdOrderByCreatedAtDesc("user-1");
    }

    @Test
    void getProjectByIdAllowsOwnerAndDeniesOtherUsers() throws Exception {
        Project project = new Project("Alpha", "First", "user-1");
        project.setId("proj-1");

        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(project));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/proj-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("proj-1"))
                .andExpect(jsonPath("$.name").value("Alpha"))
                .andExpect(jsonPath("$.ownerId").value("user-1"));

        mockMvc.perform(get("/api/projects/proj-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));
    }

    @Test
    void updateProjectUpdatesOwnedProjectAndRejectsOtherUsers() throws Exception {
        Project existing = new Project("Alpha", "Old", "user-1");
        existing.setId("proj-1");

        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(existing));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = """
                {
                  "name": "Alpha Updated",
                  "description": "New description",
                  "ownerId": "user-2"
                }
                """;

        mockMvc.perform(put("/api/projects/proj-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("proj-1"))
                .andExpect(jsonPath("$.name").value("Alpha Updated"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.ownerId").value("user-1"));

        mockMvc.perform(put("/api/projects/proj-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));
    }

    @Test
    void deleteProjectDeletesOwnedProjectAndRejectsOtherUsers() throws Exception {
        Project existing = new Project("Alpha", "First", "user-1");
        existing.setId("proj-1");

        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(existing));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/projects/proj-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));

        verify(projectRepository, never()).delete(any());

        mockMvc.perform(delete("/api/projects/proj-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isNoContent());

        verify(projectRepository).delete(existing);
    }
}

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
import com.taskflow.service.JwtService;
import com.taskflow.service.TaskService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        TokenRevocationService.class,
        TaskService.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "taskflow.security.jwt.secret=test-only-jwt-signing-secret-key-at-least-32-bytes",
        "taskflow.security.jwt.expiration-ms=3600000"
})
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TaskRepository taskRepository;

    @MockitoBean
    private ProjectRepository projectRepository;

    @MockitoBean
    private RevokedTokenRepository revokedTokenRepository;

    private String userOneToken;
    private String userTwoToken;
    private Project userOneProject;

    @BeforeEach
    void setUp() {
        when(revokedTokenRepository.existsByTokenId(anyString())).thenReturn(false);

        User userOne = new User("Alice", "alice@example.com", "hashed");
        userOne.setId("user-1");
        userOneToken = jwtService.generateToken(userOne).token();

        User userTwo = new User("Bob", "bob@example.com", "hashed");
        userTwo.setId("user-2");
        userTwoToken = jwtService.generateToken(userTwo).token();

        userOneProject = new Project("Roadmap", "Q4 deliverables", "user-1");
        userOneProject.setId("proj-1");
    }

    @Test
    void taskEndpointsRequireValidNonRevokedJwt() throws Exception {
        mockMvc.perform(post("/api/projects/proj-1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects/proj-1/tasks"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tasks/task-1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/tasks/task-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/tasks/task-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/tasks/task-1/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"HIGH\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/tasks/task-1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/tasks/task-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));

        when(revokedTokenRepository.existsByTokenId(anyString())).thenReturn(true);
        mockMvc.perform(get("/api/projects/proj-1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has been revoked"));
    }

    @Test
    void createTaskVerifiesProjectOwnershipAndIgnoresClientSuppliedProjectOrOwnerIds() throws Exception {
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(userOneProject));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            assertThat(saved.getProjectId()).isEqualTo("proj-1");
            saved.setId("task-1");
            saved.setCreatedAt(Instant.parse("2026-11-25T16:10:00Z"));
            return saved;
        });

        String payload = """
                {
                  "title": "Implement Task API",
                  "description": "Controllers and tests",
                  "status": "IN_PROGRESS",
                  "priority": "HIGH",
                  "dueDate": "2026-12-05T17:00:00Z",
                  "projectId": "other-project",
                  "ownerId": "user-2"
                }
                """;

        mockMvc.perform(post("/api/projects/proj-1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("task-1"))
                .andExpect(jsonPath("$.title").value("Implement Task API"))
                .andExpect(jsonPath("$.description").value("Controllers and tests"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-05T17:00:00Z"))
                .andExpect(jsonPath("$.projectId").value("proj-1"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-25T16:10:00Z"));

        mockMvc.perform(post("/api/projects/proj-1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));
    }

    @Test
    void getTasksByProjectSupportsFilteringAndRejectsInvalidEnumParamsAndCrossUserAccess() throws Exception {
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(userOneProject));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());

        Task t1 = new Task("T1", "D1", TaskStatus.TODO, TaskPriority.LOW, null, "proj-1");
        t1.setId("task-1");
        Task t2 = new Task("T2", "D2", TaskStatus.IN_REVIEW, TaskPriority.URGENT, null, "proj-1");
        t2.setId("task-2");

        when(taskRepository.findByProjectId("proj-1")).thenReturn(List.of(t1, t2));
        when(taskRepository.findByProjectIdAndStatus("proj-1", TaskStatus.IN_REVIEW)).thenReturn(List.of(t2));
        when(taskRepository.findByProjectIdAndPriority("proj-1", TaskPriority.LOW)).thenReturn(List.of(t1));
        when(taskRepository.findByProjectIdAndStatusAndPriority("proj-1", TaskStatus.IN_REVIEW, TaskPriority.URGENT))
                .thenReturn(List.of(t2));

        mockMvc.perform(get("/api/projects/proj-1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/projects/proj-1/tasks?status=IN_REVIEW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("task-2"));

        mockMvc.perform(get("/api/projects/proj-1/tasks?priority=LOW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("task-1"));

        mockMvc.perform(get("/api/projects/proj-1/tasks?status=IN_REVIEW&priority=URGENT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("task-2"));

        mockMvc.perform(get("/api/projects/proj-1/tasks?status=NOT_A_STATUS")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'status'"));

        mockMvc.perform(get("/api/projects/proj-1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));
    }

    @Test
    void getAndUpdateTaskVerifyOwnershipAndSetDeadline() throws Exception {
        Task existing = new Task("Old Title", "Old Desc", TaskStatus.TODO, TaskPriority.MEDIUM, null, "proj-1");
        existing.setId("task-1");

        when(taskRepository.findById("task-1")).thenReturn(Optional.of(existing));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(userOneProject));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/api/tasks/task-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("task-1"))
                .andExpect(jsonPath("$.title").value("Old Title"));

        mockMvc.perform(get("/api/tasks/task-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found"));

        String updatePayload = """
                {
                  "title": "Updated Title",
                  "description": "Updated Description",
                  "status": "IN_REVIEW",
                  "priority": "URGENT",
                  "dueDate": "2026-12-20T09:30:00Z",
                  "projectId": "hijacked-project"
                }
                """;

        mockMvc.perform(put("/api/tasks/task-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.description").value("Updated Description"))
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-20T09:30:00Z"))
                .andExpect(jsonPath("$.projectId").value("proj-1"));

        mockMvc.perform(put("/api/tasks/task-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found"));
    }

    @Test
    void patchStatusAndPriorityValidateInputAndEnforceOwnership() throws Exception {
        Task existing = new Task("Task", null, TaskStatus.TODO, TaskPriority.LOW, null, "proj-1");
        existing.setId("task-1");

        when(taskRepository.findById("task-1")).thenReturn(Optional.of(existing));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(userOneProject));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/tasks/task-1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        mockMvc.perform(patch("/api/tasks/task-1/priority")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"URGENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"));

        mockMvc.perform(patch("/api/tasks/task-1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/tasks/task-1/priority")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"LOW\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTaskDeletesOwnedTaskAndBlocksCrossUserAccess() throws Exception {
        Task existing = new Task("Task", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, "proj-1");
        existing.setId("task-1");

        when(taskRepository.findById("task-1")).thenReturn(Optional.of(existing));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(userOneProject));

        mockMvc.perform(delete("/api/tasks/task-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userTwoToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found"));

        verify(taskRepository, never()).delete(any());

        mockMvc.perform(delete("/api/tasks/task-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken))
                .andExpect(status().isNoContent());

        verify(taskRepository).delete(existing);
    }

    @Test
    void validationErrorsReturnBadRequestWithFieldDetails() throws Exception {
        mockMvc.perform(post("/api/projects/proj-1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists());

        mockMvc.perform(put("/api/tasks/task-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists());

        mockMvc.perform(patch("/api/tasks/task-1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.status").exists());

        mockMvc.perform(patch("/api/tasks/task-1/priority")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.priority").exists());
    }
}

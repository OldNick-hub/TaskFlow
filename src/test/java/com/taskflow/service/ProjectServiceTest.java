package com.taskflow.service;

import com.taskflow.config.AuthenticatedUser;
import com.taskflow.dto.CreateProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.dto.UpdateProjectRequest;
import com.taskflow.exception.InvalidTokenException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.Project;
import com.taskflow.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    private static final AuthenticatedUser USER_ONE = AuthenticatedUser.stateless("user-1", "alice@example.com");

    @Test
    void createProjectSetsOwnerFromPrincipalAndTrimsFields() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId("proj-1");
            project.setCreatedAt(Instant.parse("2026-11-25T16:00:00Z"));
            project.setUpdatedAt(Instant.parse("2026-11-25T16:00:00Z"));
            return project;
        });

        CreateProjectRequest request = new CreateProjectRequest("  Platform Rewrite  ", "  Core API migration  ");
        ProjectResponse response = projectService.createProject(request, USER_ONE);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project savedEntity = captor.getValue();

        assertThat(savedEntity.getName()).isEqualTo("Platform Rewrite");
        assertThat(savedEntity.getDescription()).isEqualTo("Core API migration");
        assertThat(savedEntity.getOwnerId()).isEqualTo("user-1");

        assertThat(response.id()).isEqualTo("proj-1");
        assertThat(response.name()).isEqualTo("Platform Rewrite");
        assertThat(response.description()).isEqualTo("Core API migration");
        assertThat(response.ownerId()).isEqualTo("user-1");
    }

    @Test
    void createProjectAllowsNullDescriptionAndRejectsMissingPrincipal() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId("proj-2");
            return project;
        });

        ProjectResponse response = projectService.createProject(
                new CreateProjectRequest("Minimal", null), USER_ONE);
        assertThat(response.description()).isNull();

        assertThatThrownBy(() -> projectService.createProject(
                new CreateProjectRequest("Minimal", null), null))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> projectService.createProject(
                new CreateProjectRequest("Minimal", null), AuthenticatedUser.stateless(" ", "a@b.com")))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getProjectsQueriesOnlyPrincipalProjects() {
        Project p1 = new Project("Alpha", "First", "user-1");
        p1.setId("proj-1");
        Project p2 = new Project("Beta", "Second", "user-1");
        p2.setId("proj-2");

        when(projectRepository.findByOwnerIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(p2, p1));

        List<ProjectResponse> results = projectService.getProjects(USER_ONE);

        verify(projectRepository).findByOwnerIdOrderByCreatedAtDesc("user-1");
        assertThat(results).extracting(ProjectResponse::id).containsExactly("proj-2", "proj-1");
        assertThat(results).extracting(ProjectResponse::ownerId).containsOnly("user-1");
    }

    @Test
    void getProjectByIdEnforcesOwnership() {
        Project project = new Project("Alpha", "Desc", "user-1");
        project.setId("proj-1");

        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(project));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());

        ProjectResponse found = projectService.getProjectById("proj-1", USER_ONE);
        assertThat(found.id()).isEqualTo("proj-1");
        assertThat(found.ownerId()).isEqualTo("user-1");

        AuthenticatedUser userTwo = AuthenticatedUser.stateless("user-2", "bob@example.com");
        assertThatThrownBy(() -> projectService.getProjectById("proj-1", userTwo))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProjectModifiesOwnedProjectAndRejectsUnauthorizedUser() {
        Project existing = new Project("Old Name", "Old Desc", "user-1");
        existing.setId("proj-1");

        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());

        ProjectResponse updated = projectService.updateProject(
                "proj-1",
                new UpdateProjectRequest("  New Name  ", "  New Desc  "),
                USER_ONE);

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.description()).isEqualTo("New Desc");
        assertThat(updated.ownerId()).isEqualTo("user-1");

        AuthenticatedUser userTwo = AuthenticatedUser.stateless("user-2", "bob@example.com");
        assertThatThrownBy(() -> projectService.updateProject(
                "proj-1",
                new UpdateProjectRequest("Hack", "Hack"),
                userTwo))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteProjectDeletesOwnedProjectAndRejectsUnauthorizedUser() {
        Project existing = new Project("Alpha", "Desc", "user-1");
        existing.setId("proj-1");

        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(existing));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());

        projectService.deleteProject("proj-1", USER_ONE);
        verify(projectRepository).delete(existing);

        AuthenticatedUser userTwo = AuthenticatedUser.stateless("user-2", "bob@example.com");
        assertThatThrownBy(() -> projectService.deleteProject("proj-1", userTwo))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(projectRepository).delete(any());
    }
}

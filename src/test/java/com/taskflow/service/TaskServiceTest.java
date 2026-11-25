package com.taskflow.service;

import com.taskflow.config.AuthenticatedUser;
import com.taskflow.dto.CreateTaskRequest;
import com.taskflow.dto.TaskResponse;
import com.taskflow.dto.UpdateTaskPriorityRequest;
import com.taskflow.dto.UpdateTaskRequest;
import com.taskflow.dto.UpdateTaskStatusRequest;
import com.taskflow.exception.InvalidTokenException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.Project;
import com.taskflow.model.Task;
import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private TaskService taskService;

    private static final AuthenticatedUser USER_ONE = AuthenticatedUser.stateless("user-1", "alice@example.com");
    private static final AuthenticatedUser USER_TWO = AuthenticatedUser.stateless("user-2", "bob@example.com");

    private Project ownedProject() {
        Project project = new Project("Backend", "API", "user-1");
        project.setId("proj-1");
        return project;
    }

    @Test
    void createTaskVerifiesOwnershipAppliesDefaultsAndTrimsFields() {
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(ownedProject()));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId("task-1");
            task.setCreatedAt(Instant.parse("2026-11-25T16:00:00Z"));
            return task;
        });

        Instant due = Instant.parse("2026-12-01T18:00:00Z");
        CreateTaskRequest request = new CreateTaskRequest(
                "  Implement Auth  ", "  JWT filter  ", null, null, due);

        TaskResponse response = taskService.createTask("proj-1", request, USER_ONE);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        Task saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("Implement Auth");
        assertThat(saved.getDescription()).isEqualTo("JWT filter");
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(saved.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(saved.getDueDate()).isEqualTo(due);
        assertThat(saved.getProjectId()).isEqualTo("proj-1");

        assertThat(response.id()).isEqualTo("task-1");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(response.projectId()).isEqualTo("proj-1");
    }

    @Test
    void createTaskAcceptsExplicitStatusAndPriorityAndRejectsUnownedProject() {
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(ownedProject()));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.createTask(
                "proj-1",
                new CreateTaskRequest("Urgent Fix", null, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, null),
                USER_ONE);
        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.priority()).isEqualTo(TaskPriority.URGENT);

        assertThatThrownBy(() -> taskService.createTask(
                "proj-1",
                new CreateTaskRequest("Hack", null, null, null, null),
                USER_TWO))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> taskService.createTask(
                "proj-1",
                new CreateTaskRequest("No Auth", null, null, null, null),
                null))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getTasksByProjectAppliesStatusAndPriorityFilters() {
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(ownedProject()));
        Task t1 = new Task("T1", null, TaskStatus.TODO, TaskPriority.LOW, null, "proj-1");
        t1.setId("task-1");
        Task t2 = new Task("T2", null, TaskStatus.IN_REVIEW, TaskPriority.HIGH, null, "proj-1");
        t2.setId("task-2");

        when(taskRepository.findByProjectId("proj-1")).thenReturn(List.of(t1, t2));
        when(taskRepository.findByProjectIdAndStatus("proj-1", TaskStatus.IN_REVIEW)).thenReturn(List.of(t2));
        when(taskRepository.findByProjectIdAndPriority("proj-1", TaskPriority.LOW)).thenReturn(List.of(t1));
        when(taskRepository.findByProjectIdAndStatusAndPriority("proj-1", TaskStatus.IN_REVIEW, TaskPriority.HIGH))
                .thenReturn(List.of(t2));

        assertThat(taskService.getTasksByProject("proj-1", null, null, USER_ONE)).hasSize(2);
        assertThat(taskService.getTasksByProject("proj-1", TaskStatus.IN_REVIEW, null, USER_ONE))
                .extracting(TaskResponse::id).containsExactly("task-2");
        assertThat(taskService.getTasksByProject("proj-1", null, TaskPriority.LOW, USER_ONE))
                .extracting(TaskResponse::id).containsExactly("task-1");
        assertThat(taskService.getTasksByProject("proj-1", TaskStatus.IN_REVIEW, TaskPriority.HIGH, USER_ONE))
                .extracting(TaskResponse::id).containsExactly("task-2");

        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> taskService.getTasksByProject("proj-1", null, null, USER_TWO))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAndUpdateTaskVerifyOwnershipThroughParentProject() {
        Task existing = new Task("Initial", "Old", TaskStatus.TODO, TaskPriority.LOW, null, "proj-1");
        existing.setId("task-1");

        when(taskRepository.findById("task-1")).thenReturn(Optional.of(existing));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(ownedProject()));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse fetched = taskService.getTaskById("task-1", USER_ONE);
        assertThat(fetched.id()).isEqualTo("task-1");

        Instant newDeadline = Instant.parse("2026-12-15T12:00:00Z");
        TaskResponse updated = taskService.updateTask(
                "task-1",
                new UpdateTaskRequest("  Updated  ", "  New Desc  ", TaskStatus.IN_PROGRESS, TaskPriority.HIGH, newDeadline),
                USER_ONE);
        assertThat(updated.title()).isEqualTo("Updated");
        assertThat(updated.description()).isEqualTo("New Desc");
        assertThat(updated.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(updated.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(updated.dueDate()).isEqualTo(newDeadline);
        assertThat(updated.projectId()).isEqualTo("proj-1");

        TaskResponse statusPatched = taskService.updateTaskStatus(
                "task-1", new UpdateTaskStatusRequest(TaskStatus.DONE), USER_ONE);
        assertThat(statusPatched.status()).isEqualTo(TaskStatus.DONE);

        TaskResponse priorityPatched = taskService.updateTaskPriority(
                "task-1", new UpdateTaskPriorityRequest(TaskPriority.URGENT), USER_ONE);
        assertThat(priorityPatched.priority()).isEqualTo(TaskPriority.URGENT);

        assertThatThrownBy(() -> taskService.getTaskById("task-1", USER_TWO))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> taskService.updateTaskStatus(
                "task-1", new UpdateTaskStatusRequest(TaskStatus.TODO), USER_TWO))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteTaskDeletesOwnedTaskAndPreventsCrossUserDeletion() {
        Task existing = new Task("Delete me", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, "proj-1");
        existing.setId("task-1");

        when(taskRepository.findById("task-1")).thenReturn(Optional.of(existing));
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-2")).thenReturn(Optional.empty());
        when(projectRepository.findByIdAndOwnerId("proj-1", "user-1")).thenReturn(Optional.of(ownedProject()));

        assertThatThrownBy(() -> taskService.deleteTask("task-1", USER_TWO))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).delete(any());

        taskService.deleteTask("task-1", USER_ONE);
        verify(taskRepository).delete(existing);
    }
}

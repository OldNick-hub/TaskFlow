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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles project-scoped and owner-verified task creation, retrieval, filtering, updates, and deletion.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Creates a new task inside a project after verifying the authenticated user owns the project.
     */
    public TaskResponse createTask(String projectId, CreateTaskRequest request, AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        Project project = requireOwnedProject(projectId, ownerId);

        Task task = new Task(
                request.title().trim(),
                normalizeDescription(request.description()),
                request.status(),
                request.priority(),
                request.dueDate(),
                project.getId());

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    /**
     * Retrieves tasks for a project owned by the authenticated user, with optional status and priority filters.
     */
    public List<TaskResponse> getTasksByProject(
            String projectId,
            TaskStatus status,
            TaskPriority priority,
            AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        Project project = requireOwnedProject(projectId, ownerId);

        List<Task> tasks;
        if (status != null && priority != null) {
            tasks = taskRepository.findByProjectIdAndStatusAndPriority(project.getId(), status, priority);
        } else if (status != null) {
            tasks = taskRepository.findByProjectIdAndStatus(project.getId(), status);
        } else if (priority != null) {
            tasks = taskRepository.findByProjectIdAndPriority(project.getId(), priority);
        } else {
            tasks = taskRepository.findByProjectId(project.getId());
        }

        return tasks.stream().map(TaskResponse::from).toList();
    }

    /**
     * Retrieves a single task after verifying the authenticated user owns its parent project.
     */
    public TaskResponse getTaskById(String taskId, AuthenticatedUser principal) {
        Task task = findOwnedTask(taskId, principal);
        return TaskResponse.from(task);
    }

    /**
     * Updates a task's details and deadline after verifying ownership through its parent project.
     */
    public TaskResponse updateTask(String taskId, UpdateTaskRequest request, AuthenticatedUser principal) {
        Task task = findOwnedTask(taskId, principal);
        task.setTitle(request.title().trim());
        task.setDescription(normalizeDescription(request.description()));
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setDueDate(request.dueDate());

        Task updated = taskRepository.save(task);
        return TaskResponse.from(updated);
    }

    /**
     * Updates a task's workflow status after verifying ownership through its parent project.
     */
    public TaskResponse updateTaskStatus(
            String taskId, UpdateTaskStatusRequest request, AuthenticatedUser principal) {
        Task task = findOwnedTask(taskId, principal);
        task.setStatus(request.status());
        Task updated = taskRepository.save(task);
        return TaskResponse.from(updated);
    }

    /**
     * Updates a task's priority after verifying ownership through its parent project.
     */
    public TaskResponse updateTaskPriority(
            String taskId, UpdateTaskPriorityRequest request, AuthenticatedUser principal) {
        Task task = findOwnedTask(taskId, principal);
        task.setPriority(request.priority());
        Task updated = taskRepository.save(task);
        return TaskResponse.from(updated);
    }

    /**
     * Deletes a task after verifying ownership through its parent project.
     */
    public void deleteTask(String taskId, AuthenticatedUser principal) {
        Task task = findOwnedTask(taskId, principal);
        taskRepository.delete(task);
    }

    private Project requireOwnedProject(String projectId, String ownerId) {
        if (projectId == null || projectId.isBlank()) {
            throw new ResourceNotFoundException("Project not found");
        }
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    private Task findOwnedTask(String taskId, AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        if (taskId == null || taskId.isBlank()) {
            throw new ResourceNotFoundException("Task not found");
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (task.getProjectId() == null || task.getProjectId().isBlank()) {
            throw new ResourceNotFoundException("Task not found");
        }
        projectRepository.findByIdAndOwnerId(task.getProjectId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        return task;
    }

    private String requireOwnerId(AuthenticatedUser principal) {
        if (principal == null || principal.getId() == null || principal.getId().isBlank()) {
            throw new InvalidTokenException("Authentication is required");
        }
        return principal.getId();
    }

    private String normalizeDescription(String description) {
        return description != null ? description.trim() : null;
    }
}

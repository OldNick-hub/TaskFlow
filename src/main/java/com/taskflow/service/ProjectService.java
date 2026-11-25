package com.taskflow.service;

import com.taskflow.config.AuthenticatedUser;
import com.taskflow.dto.CreateProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.dto.UpdateProjectRequest;
import com.taskflow.exception.InvalidTokenException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.Project;
import com.taskflow.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles owner-scoped project creation, retrieval, updates, and deletion.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Creates a new project owned by the authenticated principal.
     */
    public ProjectResponse createProject(CreateProjectRequest request, AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        Project project = new Project(
                request.name().trim(),
                normalizeDescription(request.description()),
                ownerId);
        Project saved = projectRepository.save(project);
        return ProjectResponse.from(saved);
    }

    /**
     * Retrieves all projects belonging to the authenticated principal, newest first.
     */
    public List<ProjectResponse> getProjects(AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        return projectRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /**
     * Retrieves a single project by ID if owned by the authenticated principal.
     */
    public ProjectResponse getProjectById(String projectId, AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        Project project = findOwnedProject(projectId, ownerId);
        return ProjectResponse.from(project);
    }

    /**
     * Updates an existing project's name and description if owned by the authenticated principal.
     */
    public ProjectResponse updateProject(String projectId, UpdateProjectRequest request, AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        Project project = findOwnedProject(projectId, ownerId);
        project.setName(request.name().trim());
        project.setDescription(normalizeDescription(request.description()));
        Project updated = projectRepository.save(project);
        return ProjectResponse.from(updated);
    }

    /**
     * Deletes an existing project if owned by the authenticated principal.
     */
    public void deleteProject(String projectId, AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        Project project = findOwnedProject(projectId, ownerId);
        projectRepository.delete(project);
    }

    private Project findOwnedProject(String projectId, String ownerId) {
        if (projectId == null || projectId.isBlank()) {
            throw new ResourceNotFoundException("Project not found");
        }
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
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

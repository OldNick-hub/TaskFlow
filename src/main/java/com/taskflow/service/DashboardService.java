package com.taskflow.service;

import com.taskflow.config.AuthenticatedUser;
import com.taskflow.dto.DashboardResponse;
import com.taskflow.dto.TaskPriorityDistribution;
import com.taskflow.dto.TaskStatusDistribution;
import com.taskflow.exception.InvalidTokenException;
import com.taskflow.model.Project;
import com.taskflow.model.Task;
import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Computes real-time project and task analytics scoped strictly to the authenticated user.
 */
@Service
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final Clock clock;

    @Autowired
    public DashboardService(ProjectRepository projectRepository, TaskRepository taskRepository) {
        this(projectRepository, taskRepository, Clock.systemUTC());
    }

    DashboardService(ProjectRepository projectRepository, TaskRepository taskRepository, Clock clock) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    /**
     * Aggregates project counts, task completion metrics, overdue tasks, and status/priority distributions
     * for the authenticated user.
     */
    public DashboardResponse getDashboardAnalytics(AuthenticatedUser principal) {
        String ownerId = requireOwnerId(principal);
        List<Project> userProjects = projectRepository.findByOwnerId(ownerId);
        long totalProjects = userProjects.size();

        List<String> projectIds = userProjects.stream()
                .map(Project::getId)
                .filter(Objects::nonNull)
                .toList();

        if (projectIds.isEmpty()) {
            return DashboardResponse.empty(totalProjects);
        }

        List<Task> tasks = taskRepository.findByProjectIdIn(projectIds);
        if (tasks.isEmpty()) {
            return DashboardResponse.empty(totalProjects);
        }

        long todo = 0L;
        long inProgress = 0L;
        long inReview = 0L;
        long done = 0L;

        long low = 0L;
        long medium = 0L;
        long high = 0L;
        long urgent = 0L;

        long overdue = 0L;
        Instant now = clock.instant();

        for (Task task : tasks) {
            TaskStatus status = task.getStatus();
            if (status != null) {
                switch (status) {
                    case TODO -> todo++;
                    case IN_PROGRESS -> inProgress++;
                    case IN_REVIEW -> inReview++;
                    case DONE -> done++;
                }
            }

            TaskPriority priority = task.getPriority();
            if (priority != null) {
                switch (priority) {
                    case LOW -> low++;
                    case MEDIUM -> medium++;
                    case HIGH -> high++;
                    case URGENT -> urgent++;
                }
            }

            if (task.getDueDate() != null && task.getDueDate().isBefore(now) && status != TaskStatus.DONE) {
                overdue++;
            }
        }

        long totalTasks = tasks.size();
        long completedTasks = done;
        long pendingTasks = totalTasks - completedTasks;
        double completionPercentage = calculatePercentage(completedTasks, totalTasks);

        return new DashboardResponse(
                totalProjects,
                totalTasks,
                completedTasks,
                pendingTasks,
                inProgress,
                overdue,
                completionPercentage,
                new TaskStatusDistribution(todo, inProgress, inReview, done),
                new TaskPriorityDistribution(low, medium, high, urgent)
        );
    }

    private double calculatePercentage(long completed, long total) {
        if (total <= 0L) {
            return 0.0;
        }
        return BigDecimal.valueOf((completed * 100.0) / total)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String requireOwnerId(AuthenticatedUser principal) {
        if (principal == null || principal.getId() == null || principal.getId().isBlank()) {
            throw new InvalidTokenException("Authentication is required");
        }
        return principal.getId();
    }
}

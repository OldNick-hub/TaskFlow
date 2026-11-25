package com.taskflow.service;

import com.taskflow.config.AuthenticatedUser;
import com.taskflow.dto.DashboardResponse;
import com.taskflow.exception.InvalidTokenException;
import com.taskflow.model.Project;
import com.taskflow.model.Task;
import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    private static final Instant FIXED_NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final AuthenticatedUser USER_ONE = AuthenticatedUser.stateless("user-1", "alice@example.com");

    @Test
    void getDashboardAnalyticsCalculatesAllMetricsAndDistributions() {
        DashboardService service = new DashboardService(projectRepository, taskRepository, FIXED_CLOCK);

        Project p1 = new Project("Alpha", "A", "user-1");
        p1.setId("proj-1");
        Project p2 = new Project("Beta", "B", "user-1");
        p2.setId("proj-2");

        when(projectRepository.findByOwnerId("user-1")).thenReturn(List.of(p1, p2));

        Task t1 = new Task("T1", null, TaskStatus.TODO, TaskPriority.LOW,
                FIXED_NOW.minusSeconds(3600), "proj-1"); // overdue
        Task t2 = new Task("T2", null, TaskStatus.IN_PROGRESS, TaskPriority.HIGH,
                FIXED_NOW.minusSeconds(60), "proj-1"); // overdue & inProgress
        Task t3 = new Task("T3", null, TaskStatus.IN_REVIEW, TaskPriority.URGENT,
                FIXED_NOW.plusSeconds(3600), "proj-1"); // not overdue
        Task t4 = new Task("T4", null, TaskStatus.DONE, TaskPriority.MEDIUM,
                FIXED_NOW.minusSeconds(7200), "proj-2"); // completed, past due -> NOT overdue
        Task t5 = new Task("T5", null, TaskStatus.DONE, TaskPriority.HIGH,
                null, "proj-2"); // completed
        Task t6 = new Task("T6", null, TaskStatus.TODO, TaskPriority.MEDIUM,
                null, "proj-2"); // pending

        when(taskRepository.findByProjectIdIn(List.of("proj-1", "proj-2")))
                .thenReturn(List.of(t1, t2, t3, t4, t5, t6));

        DashboardResponse response = service.getDashboardAnalytics(USER_ONE);

        assertThat(response.totalProjects()).isEqualTo(2L);
        assertThat(response.totalTasks()).isEqualTo(6L);
        assertThat(response.completedTasks()).isEqualTo(2L);
        assertThat(response.pendingTasks()).isEqualTo(4L);
        assertThat(response.inProgressTasks()).isEqualTo(1L);
        assertThat(response.overdueTasks()).isEqualTo(2L);
        assertThat(response.completionPercentage()).isEqualTo(33.33);

        assertThat(response.statusDistribution().todo()).isEqualTo(2L);
        assertThat(response.statusDistribution().inProgress()).isEqualTo(1L);
        assertThat(response.statusDistribution().inReview()).isEqualTo(1L);
        assertThat(response.statusDistribution().done()).isEqualTo(2L);

        assertThat(response.priorityDistribution().low()).isEqualTo(1L);
        assertThat(response.priorityDistribution().medium()).isEqualTo(2L);
        assertThat(response.priorityDistribution().high()).isEqualTo(2L);
        assertThat(response.priorityDistribution().urgent()).isEqualTo(1L);
    }

    @Test
    void getDashboardAnalyticsHandlesEmptyProjectsAndZeroTasksSafely() {
        DashboardService service = new DashboardService(projectRepository, taskRepository, FIXED_CLOCK);

        when(projectRepository.findByOwnerId("user-1")).thenReturn(List.of());
        DashboardResponse noProjects = service.getDashboardAnalytics(USER_ONE);

        verify(taskRepository, never()).findByProjectIdIn(anyCollection());
        assertThat(noProjects.totalProjects()).isZero();
        assertThat(noProjects.totalTasks()).isZero();
        assertThat(noProjects.completedTasks()).isZero();
        assertThat(noProjects.pendingTasks()).isZero();
        assertThat(noProjects.inProgressTasks()).isZero();
        assertThat(noProjects.overdueTasks()).isZero();
        assertThat(noProjects.completionPercentage()).isEqualTo(0.0);
        assertThat(noProjects.statusDistribution().todo()).isZero();
        assertThat(noProjects.statusDistribution().done()).isZero();
        assertThat(noProjects.priorityDistribution().low()).isZero();
        assertThat(noProjects.priorityDistribution().urgent()).isZero();

        Project p1 = new Project("Empty Project", null, "user-1");
        p1.setId("proj-1");
        when(projectRepository.findByOwnerId("user-1")).thenReturn(List.of(p1));
        when(taskRepository.findByProjectIdIn(List.of("proj-1"))).thenReturn(List.of());

        DashboardResponse noTasks = service.getDashboardAnalytics(USER_ONE);
        assertThat(noTasks.totalProjects()).isEqualTo(1L);
        assertThat(noTasks.totalTasks()).isZero();
        assertThat(noTasks.completionPercentage()).isEqualTo(0.0);
    }

    @Test
    void getDashboardAnalyticsRejectsMissingOrBlankPrincipal() {
        DashboardService service = new DashboardService(projectRepository, taskRepository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.getDashboardAnalytics(null))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.getDashboardAnalytics(AuthenticatedUser.stateless(" ", "a@b.com")))
                .isInstanceOf(InvalidTokenException.class);
    }
}

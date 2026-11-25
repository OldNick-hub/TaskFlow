package com.taskflow.dto;

/**
 * Aggregated analytics payload for the authenticated user's dashboard.
 */
public record DashboardResponse(
        long totalProjects,
        long totalTasks,
        long completedTasks,
        long pendingTasks,
        long inProgressTasks,
        long overdueTasks,
        double completionPercentage,
        TaskStatusDistribution statusDistribution,
        TaskPriorityDistribution priorityDistribution
) {
    public static DashboardResponse empty(long totalProjects) {
        return new DashboardResponse(
                totalProjects,
                0L,
                0L,
                0L,
                0L,
                0L,
                0.0,
                TaskStatusDistribution.empty(),
                TaskPriorityDistribution.empty()
        );
    }
}

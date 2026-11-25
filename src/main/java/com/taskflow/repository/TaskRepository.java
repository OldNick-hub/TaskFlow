package com.taskflow.repository;

import com.taskflow.model.Task;
import com.taskflow.model.TaskPriority;
import com.taskflow.model.TaskStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data MongoDB repository for {@link Task} documents.
 */
public interface TaskRepository extends MongoRepository<Task, String> {

    List<Task> findByProjectId(String projectId);

    List<Task> findByProjectIdIn(Collection<String> projectIds);

    List<Task> findByProjectIdAndStatus(String projectId, TaskStatus status);

    List<Task> findByProjectIdAndPriority(String projectId, TaskPriority priority);

    List<Task> findByProjectIdAndStatusAndPriority(String projectId, TaskStatus status, TaskPriority priority);

    void deleteByProjectId(String projectId);
}

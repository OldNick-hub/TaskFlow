package com.taskflow.repository;

import com.taskflow.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link Project} documents.
 */
public interface ProjectRepository extends MongoRepository<Project, String> {

    List<Project> findByOwnerId(String ownerId);

    List<Project> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<Project> findByIdAndOwnerId(String id, String ownerId);
}

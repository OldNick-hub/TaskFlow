package com.taskflow.service;

import com.taskflow.dto.DatabaseHealthResponse;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Performs safe MongoDB connectivity checks without exposing credentials or connection URIs.
 */
@Service
public class DatabaseHealthService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthService.class);
    private static final Document PING_COMMAND = new Document("ping", 1);

    private final MongoTemplate mongoTemplate;
    private final String configuredDatabase;

    public DatabaseHealthService(
            MongoTemplate mongoTemplate,
            @Value("${spring.mongodb.database:taskflow}") String configuredDatabase) {
        this.mongoTemplate = mongoTemplate;
        this.configuredDatabase = configuredDatabase;
    }

    /**
     * Executes a lightweight {@code ping} command against the configured MongoDB database.
     *
     * @return sanitized connectivity status without any credentials or exception details
     */
    public DatabaseHealthResponse checkConnectivity() {
        String databaseName = configuredDatabase;
        try {
            databaseName = mongoTemplate.getDb().getName();
            Document result = mongoTemplate.executeCommand(PING_COMMAND);
            Object okValue = result.get("ok");
            boolean up = okValue instanceof Number number && number.intValue() == 1;
            return new DatabaseHealthResponse(up ? "UP" : "DOWN", databaseName, up);
        } catch (Exception ex) {
            log.warn(
                    "MongoDB connectivity check failed for database '{}' (exceptionType={})",
                    databaseName,
                    ex.getClass().getSimpleName());
            return new DatabaseHealthResponse("DOWN", databaseName, false);
        }
    }
}

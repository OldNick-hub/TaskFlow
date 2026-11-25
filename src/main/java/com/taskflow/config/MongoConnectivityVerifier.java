package com.taskflow.config;

import com.taskflow.dto.DatabaseHealthResponse;
import com.taskflow.service.DatabaseHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Verifies MongoDB connectivity on application startup without logging credentials or URIs.
 */
@Component
@ConditionalOnProperty(name = "taskflow.mongodb.verify-on-startup", havingValue = "true", matchIfMissing = true)
public class MongoConnectivityVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoConnectivityVerifier.class);

    private final DatabaseHealthService databaseHealthService;

    public MongoConnectivityVerifier(DatabaseHealthService databaseHealthService) {
        this.databaseHealthService = databaseHealthService;
    }

    @Override
    public void run(ApplicationArguments args) {
        DatabaseHealthResponse status = databaseHealthService.checkConnectivity();
        if (status.reachable()) {
            log.info("MongoDB connectivity verified for database '{}'", status.database());
        } else {
            log.error(
                    "MongoDB startup connectivity check failed for database '{}'. "
                            + "Verify MONGODB_URI, credentials, and MongoDB Atlas Network Access.",
                    status.database());
        }
    }
}

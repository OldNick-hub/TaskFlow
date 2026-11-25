package com.taskflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoDB configuration enabling automatic document auditing (e.g., {@code @CreatedDate}).
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}

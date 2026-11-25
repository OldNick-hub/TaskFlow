package com.taskflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/taskflow",
        "spring.data.mongodb.auto-index-creation=false",
        "taskflow.mongodb.verify-on-startup=false",
        "taskflow.security.jwt.secret=test-only-jwt-signing-secret-key-at-least-32-bytes"
})
class TaskFlowApplicationTests {

    @Test
    void contextLoads() {
    }
}

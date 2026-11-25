package com.taskflow.service;

import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;
import com.taskflow.dto.DatabaseHealthResponse;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseHealthServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoDatabase mongoDatabase;

    @Test
    void checkConnectivityReturnsUpWhenPingSucceeds() {
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.getName()).thenReturn("taskflow");
        when(mongoTemplate.executeCommand(new Document("ping", 1)))
                .thenReturn(new Document("ok", 1.0));

        DatabaseHealthService service = new DatabaseHealthService(mongoTemplate, "taskflow");
        DatabaseHealthResponse response = service.checkConnectivity();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.database()).isEqualTo("taskflow");
        assertThat(response.reachable()).isTrue();
    }

    @Test
    void checkConnectivityReturnsSanitizedDownWhenPingFails() {
        when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
        when(mongoDatabase.getName()).thenReturn("taskflow");
        when(mongoTemplate.executeCommand(new Document("ping", 1)))
                .thenThrow(new MongoTimeoutException(
                        "mongodb+srv://secretUser:secretPass@cluster0.example.mongodb.net failed"));

        DatabaseHealthService service = new DatabaseHealthService(mongoTemplate, "taskflow");
        DatabaseHealthResponse response = service.checkConnectivity();

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.database()).isEqualTo("taskflow");
        assertThat(response.reachable()).isFalse();
    }
}

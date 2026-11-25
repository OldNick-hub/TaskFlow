package com.taskflow.controller;

import com.taskflow.config.SecurityConfig;
import com.taskflow.dto.DatabaseHealthResponse;
import com.taskflow.service.DatabaseHealthService;
import com.taskflow.service.JwtService;
import com.taskflow.service.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DatabaseHealthController.class)
@Import(SecurityConfig.class)
class DatabaseHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseHealthService databaseHealthService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private TokenRevocationService tokenRevocationService;

    @Test
    void checkDatabaseHealthReturnsOkWhenReachable() throws Exception {
        when(databaseHealthService.checkConnectivity())
                .thenReturn(new DatabaseHealthResponse("UP", "taskflow", true));

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("taskflow"))
                .andExpect(jsonPath("$.reachable").value(true));
    }

    @Test
    void checkDatabaseHealthReturnsServiceUnavailableWithoutExposingSecrets() throws Exception {
        when(databaseHealthService.checkConnectivity())
                .thenReturn(new DatabaseHealthResponse("DOWN", "taskflow", false));

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database").value("taskflow"))
                .andExpect(jsonPath("$.reachable").value(false))
                .andExpect(content().string(not(containsString("mongodb+srv"))))
                .andExpect(content().string(not(containsString("password"))));
    }
}

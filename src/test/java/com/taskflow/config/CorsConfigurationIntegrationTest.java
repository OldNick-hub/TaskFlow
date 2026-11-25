package com.taskflow.config;

import com.taskflow.controller.DatabaseHealthController;
import com.taskflow.dto.DatabaseHealthResponse;
import com.taskflow.service.DatabaseHealthService;
import com.taskflow.service.JwtService;
import com.taskflow.service.TokenRevocationService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsConfigurationIntegrationTest {

    @Nested
    @WebMvcTest(DatabaseHealthController.class)
    @Import(SecurityConfig.class)
    class DefaultLocalhostCorsTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private DatabaseHealthService databaseHealthService;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private TokenRevocationService tokenRevocationService;

        @Test
        void defaultConfigurationPermitsLocalhostAndBlocksExternalOrigins() throws Exception {
            when(databaseHealthService.checkConnectivity())
                    .thenReturn(new DatabaseHealthResponse("UP", "taskflow", true));

            mockMvc.perform(options("/api/health/db")
                            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));

            mockMvc.perform(get("/api/health/db")
                            .header(HttpHeaders.ORIGIN, "https://untrusted.example.com"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Nested
    @WebMvcTest(DatabaseHealthController.class)
    @Import(SecurityConfig.class)
    @TestPropertySource(properties = {
            "taskflow.cors.allowed-origins=https://taskflow-ui.onrender.com, https://app.taskflow.dev"
    })
    class ProductionConfiguredCorsTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private DatabaseHealthService databaseHealthService;

        @MockitoBean
        private JwtService jwtService;

        @MockitoBean
        private TokenRevocationService tokenRevocationService;

        @Test
        void productionConfigurationPermitsOnlyConfiguredOrigins() throws Exception {
            when(databaseHealthService.checkConnectivity())
                    .thenReturn(new DatabaseHealthResponse("UP", "taskflow", true));

            mockMvc.perform(options("/api/health/db")
                            .header(HttpHeaders.ORIGIN, "https://taskflow-ui.onrender.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://taskflow-ui.onrender.com"));

            mockMvc.perform(get("/api/health/db")
                            .header(HttpHeaders.ORIGIN, "https://app.taskflow.dev"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.taskflow.dev"));

            mockMvc.perform(get("/api/health/db")
                            .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

            mockMvc.perform(get("/api/health/db")
                            .header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Test
    void wildcardOriginIsRejected() {
        SecurityConfig config = new SecurityConfig();
        assertThatThrownBy(() -> config.corsConfigurationSource(List.of("*")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wildcard '*'");
    }
}

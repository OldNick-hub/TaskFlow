package com.taskflow.controller;

import com.taskflow.config.CustomUserDetailsService;
import com.taskflow.config.JwtAuthenticationFilter;
import com.taskflow.config.SecurityConfig;
import com.taskflow.exception.GlobalExceptionHandler;
import com.taskflow.model.RevokedToken;
import com.taskflow.model.User;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.service.AuthService;
import com.taskflow.service.JwtService;
import com.taskflow.service.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        CustomUserDetailsService.class,
        JwtService.class,
        TokenRevocationService.class,
        AuthService.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "taskflow.security.jwt.secret=test-only-jwt-signing-secret-key-at-least-32-bytes",
        "taskflow.security.jwt.expiration-ms=3600000"
})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RevokedTokenRepository revokedTokenRepository;

    @Test
    void registerCreatesUserWithHashedPasswordAndOmitsPasswordFromResponse() throws Exception {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            assertThat(u.getPassword()).isNotEqualTo("StrongPass123!");
            assertThat(passwordEncoder.matches("StrongPass123!", u.getPassword())).isTrue();
            u.setId("user-1");
            u.setCreatedAt(Instant.parse("2026-11-25T15:00:00Z"));
            return u;
        });

        String payload = """
                {
                  "name": "Alice",
                  "email": "Alice@Example.com",
                  "password": "StrongPass123!",
                  "confirmPassword": "StrongPass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString("StrongPass123!"))))
                .andExpect(content().string(not(containsString("$2"))));
    }

    @Test
    void registerRejectsDuplicateEmailWithConflict() throws Exception {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        String payload = """
                {
                  "name": "Alice",
                  "email": "ALICE@example.com",
                  "password": "StrongPass123!",
                  "confirmPassword": "StrongPass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void registerRejectsPasswordMismatchAndValidationErrors() throws Exception {
        String mismatchPayload = """
                {
                  "name": "Alice",
                  "email": "alice@example.com",
                  "password": "StrongPass123!",
                  "confirmPassword": "DifferentPass123!"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mismatchPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password and confirm password do not match"));

        String invalidPayload = """
                {
                  "name": "",
                  "email": "not-an-email",
                  "password": "short",
                  "confirmPassword": ""
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void loginRejectsIncorrectPassword() throws Exception {
        User stored = new User("Alice", "alice@example.com", passwordEncoder.encode("StrongPass123!"));
        stored.setId("user-1");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(stored));

        String payload = """
                {
                  "email": "alice@example.com",
                  "password": "WrongPassword!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void loginMeAndLogoutRevocationFlowWorksEndToEnd() throws Exception {
        User stored = new User("Alice", "alice@example.com", passwordEncoder.encode("StrongPass123!"));
        stored.setId("user-1");
        stored.setCreatedAt(Instant.parse("2026-11-25T15:00:00Z"));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(stored));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(stored));

        AtomicBoolean revoked = new AtomicBoolean(false);
        when(revokedTokenRepository.existsByTokenId(anyString())).thenAnswer(inv -> revoked.get());
        when(revokedTokenRepository.save(any(RevokedToken.class))).thenAnswer(inv -> {
            revoked.set(true);
            return inv.getArgument(0);
        });

        String loginPayload = """
                {
                  "email": "alice@example.com",
                  "password": "StrongPass123!"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.id").value("user-1"))
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asString();

        clearInvocations(userRepository);

        // Access protected /api/auth/me with valid token -> 200 OK for user-1 only
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userRepository).findById("user-1");

        // Logout revokes token
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Revoked token is rejected on subsequent protected requests
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Access token has been revoked"));

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRejectMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid access token"));
    }
}

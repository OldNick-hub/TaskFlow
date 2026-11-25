package com.taskflow.service;

import com.taskflow.config.AuthenticatedUser;
import com.taskflow.dto.AuthResponse;
import com.taskflow.dto.LoginRequest;
import com.taskflow.dto.MessageResponse;
import com.taskflow.dto.RegisterRequest;
import com.taskflow.dto.UserResponse;
import com.taskflow.exception.DuplicateEmailException;
import com.taskflow.exception.InvalidTokenException;
import com.taskflow.exception.PasswordMismatchException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.model.User;
import com.taskflow.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Handles user registration, BCrypt login, JWT issuance, token revocation logout, and profile lookup.
 */
@Service
public class AuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            TokenRevocationService tokenRevocationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.tokenRevocationService = tokenRevocationService;
    }

    public UserResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new PasswordMismatchException("Password and confirm password do not match");
        }

        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("An account with this email already exists");
        }

        User user = new User(
                request.name().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.password()));

        try {
            User savedUser = userRepository.save(user);
            return UserResponse.from(savedUser);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateEmailException("An account with this email already exists");
        }
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        JwtService.GeneratedToken tokenDetails = jwtService.generateToken(user);
        return new AuthResponse(
                tokenDetails.token(),
                "Bearer",
                tokenDetails.expiresInSeconds(),
                UserResponse.from(user));
    }

    public MessageResponse logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        JwtService.JwtClaims claims = jwtService.parseAndValidate(token);
        if (tokenRevocationService.isRevoked(claims.tokenId())) {
            throw new InvalidTokenException("Access token has been revoked");
        }
        tokenRevocationService.revokeToken(claims);
        return new MessageResponse("Logged out successfully");
    }

    public UserResponse getCurrentUser(AuthenticatedUser principal) {
        if (principal == null || principal.getId() == null) {
            throw new InvalidTokenException("Authentication is required");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserResponse.from(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidTokenException("Missing or invalid Authorization header");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new InvalidTokenException("Missing access token");
        }
        return token;
    }
}

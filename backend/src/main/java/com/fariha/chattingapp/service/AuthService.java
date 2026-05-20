package com.fariha.chattingapp.service;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountRepository userRepository;
    private final AuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final long tokenHours;

    public AuthService(
            UserAccountRepository userRepository,
            AuthTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.token-hours:168}") long tokenHours
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHours = tokenHours;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        UserAccount user = new UserAccount(
                username,
                request.displayName().trim(),
                passwordEncoder.encode(request.password())
        );
        user.setOnline(true);
        user.setLastSeenAt(Instant.now());

        return issueToken(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByUsernameIgnoreCase(normalizeUsername(request.username()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        user.setOnline(true);
        user.setLastSeenAt(Instant.now());
        return issueToken(userRepository.save(user));
    }

    public Optional<UserAccount> authenticateAuthorizationHeader(String authorizationHeader) {
        return extractBearerToken(authorizationHeader)
                .flatMap(this::authenticateToken);
    }

    public Optional<UserAccount> authenticateToken(String token) {
        return tokenRepository.findByTokenAndExpiresAtAfter(token, Instant.now())
                .flatMap(authToken -> userRepository.findById(authToken.getUserId()));
    }

    public void logout(String authorizationHeader) {
        extractBearerToken(authorizationHeader).ifPresent(token -> tokenRepository.findByToken(token)
                .ifPresent(authToken -> {
                    userRepository.findById(authToken.getUserId()).ifPresent(user -> {
                        user.setOnline(false);
                        user.setLastSeenAt(Instant.now());
                        userRepository.save(user);
                    });
                    tokenRepository.deleteByToken(token);
                }));
    }

    private AuthResponse issueToken(UserAccount user) {
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(tokenHours, ChronoUnit.HOURS);
        tokenRepository.save(new AuthToken(token, user.getId(), expiresAt));
        return new AuthResponse(token, UserDto.from(user));
    }

    private static Optional<String> extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}

package com.fariha.chattingapp.service;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userRepository;
    private final AuthTokenRepository tokenRepository;
    private final RegistrationVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;
    private final long tokenHours;
    private final long otpMinutes;
    private final long resendCooldownSeconds;
    private final int dailyRequestLimit;
    private final int maxAttempts;
    private final boolean directRegistrationEnabled;

    public AuthService(
            UserAccountRepository userRepository,
            AuthTokenRepository tokenRepository,
            RegistrationVerificationRepository verificationRepository,
            PasswordEncoder passwordEncoder,
            SmsSender smsSender,
            @Value("${app.auth.token-hours:168}") long tokenHours,
            @Value("${app.verification.otp-minutes:10}") long otpMinutes,
            @Value("${app.verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.verification.daily-limit:10}") int dailyRequestLimit,
            @Value("${app.verification.max-attempts:5}") int maxAttempts,
            @Value("${app.auth.direct-registration-enabled:false}") boolean directRegistrationEnabled
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.verificationRepository = verificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsSender = smsSender;
        this.tokenHours = tokenHours;
        this.otpMinutes = otpMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.dailyRequestLimit = dailyRequestLimit;
        this.maxAttempts = maxAttempts;
        this.directRegistrationEnabled = directRegistrationEnabled;
    }

    public AuthResponse register(RegisterRequest request) {
        if (!directRegistrationEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use /api/auth/register/start and /api/auth/register/verify for SMS registration"
            );
        }
        String username = normalizeUsername(request.username());
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already registered");
        }

        UserAccount user = new UserAccount(
                username,
                request.displayName().trim(),
                passwordEncoder.encode(request.password())
        );
        user.setPhoneNumber(phoneNumber);
        user.setOnline(true);
        user.setLastSeenAt(Instant.now());

        return issueToken(userRepository.save(user));
    }

    public RegistrationStartResponse startRegistration(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already registered");
        }

        Instant now = Instant.now();
        enforceVerificationLimits(username, phoneNumber, now);
        expirePendingVerifications(username, phoneNumber);

        String code = generateCode();
        Instant expiresAt = now.plus(otpMinutes, ChronoUnit.MINUTES);
        RegistrationVerification verification = new RegistrationVerification(
                username,
                request.displayName().trim(),
                phoneNumber,
                passwordEncoder.encode(request.password()),
                passwordEncoder.encode(code),
                expiresAt
        );

        RegistrationVerification saved = verificationRepository.save(verification);
        try {
            smsSender.sendVerificationCode(phoneNumber, code);
        } catch (RuntimeException exception) {
            saved.setUsed(true);
            verificationRepository.save(saved);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to send verification code");
        }

        return new RegistrationStartResponse(
                saved.getId(),
                saved.getExpiresAt(),
                smsSender.exposesDebugCode() ? code : null
        );
    }

    public AuthResponse verifyRegistration(VerifyRegistrationRequest request) {
        RegistrationVerification verification = verificationRepository.findById(request.verificationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification request was not found"));

        if (verification.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code was already used");
        }
        if (verification.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code has expired");
        }
        if (verification.getAttempts() >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many verification attempts");
        }
        if (!passwordEncoder.matches(request.code(), verification.getCodeHash())) {
            verification.incrementAttempts();
            verificationRepository.save(verification);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code");
        }
        if (userRepository.existsByUsernameIgnoreCase(verification.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (userRepository.existsByPhoneNumber(verification.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already registered");
        }

        UserAccount user = new UserAccount(
                verification.getUsername(),
                verification.getDisplayName(),
                verification.getPasswordHash()
        );
        user.setPhoneNumber(verification.getPhoneNumber());
        user.setOnline(true);
        user.setLastSeenAt(Instant.now());

        verification.setUsed(true);
        verificationRepository.save(verification);

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
        return tokenRepository.findByTokenHashAndExpiresAtAfter(hashToken(token), Instant.now())
                .flatMap(authToken -> userRepository.findById(authToken.getUserId()));
    }

    public void logout(String authorizationHeader) {
        extractBearerToken(authorizationHeader).ifPresent(token -> tokenRepository.findByTokenHash(hashToken(token))
                .ifPresent(authToken -> {
                    userRepository.findById(authToken.getUserId()).ifPresent(user -> {
                        user.setOnline(false);
                        user.setLastSeenAt(Instant.now());
                        userRepository.save(user);
                    });
                    tokenRepository.deleteByTokenHash(authToken.getTokenHash());
                }));
    }

    private AuthResponse issueToken(UserAccount user) {
        String token = generateToken();
        Instant expiresAt = Instant.now().plus(tokenHours, ChronoUnit.HOURS);
        tokenRepository.save(new AuthToken(hashToken(token), user.getId(), expiresAt));
        return new AuthResponse(token, UserDto.from(user));
    }

    private void enforceVerificationLimits(String username, String phoneNumber, Instant now) {
        verificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter(verification -> verification.getCreatedAt().isAfter(now.minus(resendCooldownSeconds, ChronoUnit.SECONDS)))
                .ifPresent(verification -> {
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code");
                });

        Instant dailyWindow = now.minus(1, ChronoUnit.DAYS);
        if (verificationRepository.countByPhoneNumberAndCreatedAtAfter(phoneNumber, dailyWindow) >= dailyRequestLimit
                || verificationRepository.countByUsernameIgnoreCaseAndCreatedAtAfter(username, dailyWindow) >= dailyRequestLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many verification requests today");
        }
    }

    private void expirePendingVerifications(String username, String phoneNumber) {
        verificationRepository.findByPhoneNumberAndUsedFalse(phoneNumber).forEach(verification -> {
            verification.setUsed(true);
            verificationRepository.save(verification);
        });
        verificationRepository.findByUsernameIgnoreCaseAndUsedFalse(username).forEach(verification -> {
            verification.setUsed(true);
            verificationRepository.save(verification);
        });
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

    private static String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.trim().replace(" ", "");
    }

    private static String generateCode() {
        int value = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

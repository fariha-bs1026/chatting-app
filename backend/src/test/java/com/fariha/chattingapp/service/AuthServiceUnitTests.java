package com.fariha.chattingapp.service;

import com.fariha.chattingapp.TestIds;
import com.fariha.chattingapp.dto.AuthResponse;
import com.fariha.chattingapp.dto.LoginRequest;
import com.fariha.chattingapp.dto.RegisterRequest;
import com.fariha.chattingapp.dto.RegistrationStartResponse;
import com.fariha.chattingapp.dto.VerifyRegistrationRequest;
import com.fariha.chattingapp.entity.AuthToken;
import com.fariha.chattingapp.entity.RegistrationVerification;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.repository.AuthTokenRepository;
import com.fariha.chattingapp.repository.RegistrationVerificationRepository;
import com.fariha.chattingapp.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTests {
    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private AuthTokenRepository tokenRepository;

    @Mock
    private RegistrationVerificationRepository verificationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SmsSender smsSender;

    @Mock
    private MediaStorageService mediaStorageService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = authService(false, 2);
    }

    @Test
    void registerRequiresSmsFlowWhenDirectRegistrationIsDisabled() {
        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        verify(userRepository, never()).save(any());
    }

    @Test
    void directRegistrationNormalizesUsernameAndPhoneAndIssuesToken() {
        AuthService service = authService(true, 2);
        when(passwordEncoder.encode("secret123")).thenReturn("password-hash");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount user = invocation.getArgument(0);
            return TestIds.withId(user, "user-1");
        });
        when(tokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = service.register(new RegisterRequest(
                " Alice ",
                " Alice Doe ",
                "+8801711111111 ",
                "secret123"
        ));

        assertThat(response.token()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().displayName()).isEqualTo("Alice Doe");
        assertThat(response.user().phoneNumber()).isEqualTo("+8801711111111");
        assertThat(response.user().online()).isTrue();
        verify(tokenRepository).save(any(AuthToken.class));
    }

    @Test
    void startRegistrationSendsDebugCodeAndExpiresPendingRequests() {
        RegistrationVerification pendingByPhone = verification("pending-phone", "alice", "+8801711111111", "111111", false);
        RegistrationVerification pendingByUsername = verification("pending-name", "alice", "+8801722222222", "222222", false);
        when(verificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc("+8801711111111")).thenReturn(Optional.empty());
        when(verificationRepository.countByPhoneNumberAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(0L);
        when(verificationRepository.countByUsernameIgnoreCaseAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(0L);
        when(verificationRepository.findByPhoneNumberAndUsedFalse("+8801711111111")).thenReturn(List.of(pendingByPhone));
        when(verificationRepository.findByUsernameIgnoreCaseAndUsedFalse("alice")).thenReturn(List.of(pendingByUsername));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(verificationRepository.save(any(RegistrationVerification.class))).thenAnswer(invocation -> {
            RegistrationVerification verification = invocation.getArgument(0);
            return verification.getId() == null ? TestIds.withId(verification, "verification-1") : verification;
        });
        when(smsSender.exposesDebugCode()).thenReturn(true);

        RegistrationStartResponse response = authService.startRegistration(new RegisterRequest(
                " Alice ",
                "Alice Doe",
                "+8801711111111",
                "secret123"
        ));

        assertThat(response.verificationId()).isEqualTo("verification-1");
        assertThat(response.debugCode()).hasSize(6).containsOnlyDigits();
        assertThat(pendingByPhone.isUsed()).isTrue();
        assertThat(pendingByUsername.isUsed()).isTrue();
        verify(smsSender).sendVerificationCode("+8801711111111", response.debugCode());
    }

    @Test
    void startRegistrationMarksVerificationUsedWhenSmsFails() {
        when(verificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc("+8801711111111")).thenReturn(Optional.empty());
        when(verificationRepository.countByPhoneNumberAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(0L);
        when(verificationRepository.countByUsernameIgnoreCaseAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(0L);
        when(verificationRepository.findByPhoneNumberAndUsedFalse("+8801711111111")).thenReturn(List.of());
        when(verificationRepository.findByUsernameIgnoreCaseAndUsedFalse("alice")).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(verificationRepository.save(any(RegistrationVerification.class))).thenAnswer(invocation -> {
            RegistrationVerification verification = invocation.getArgument(0);
            return verification.getId() == null ? TestIds.withId(verification, "verification-1") : verification;
        });
        org.mockito.Mockito.doThrow(new RuntimeException("sms down"))
                .when(smsSender)
                .sendVerificationCode(anyString(), anyString());

        assertThatThrownBy(() -> authService.startRegistration(new RegisterRequest(
                "Alice",
                "Alice Doe",
                "+8801711111111",
                "secret123"
        )))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("502 BAD_GATEWAY");

        ArgumentCaptor<RegistrationVerification> captor = ArgumentCaptor.forClass(RegistrationVerification.class);
        verify(verificationRepository, atLeast(2)).save(captor.capture());
        assertThat(captor.getAllValues().getLast().isUsed()).isTrue();
    }

    @Test
    void verifyRegistrationRejectsInvalidCodeAndIncrementsAttempts() {
        RegistrationVerification verification = verification("verification-1", "alice", "+8801711111111", "code-hash", false);
        when(verificationRepository.findById("verification-1")).thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("000000", "code-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyRegistration(new VerifyRegistrationRequest("verification-1", "000000")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        assertThat(verification.getAttempts()).isEqualTo(1);
        verify(verificationRepository).save(verification);
    }

    @Test
    void verifyRegistrationCreatesUserAndTokenForValidCode() {
        RegistrationVerification verification = verification("verification-1", "alice", "+8801711111111", "code-hash", false);
        when(verificationRepository.findById("verification-1")).thenReturn(Optional.of(verification));
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+8801711111111")).thenReturn(false);
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> TestIds.withId(invocation.getArgument(0), "user-1"));
        when(tokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.verifyRegistration(new VerifyRegistrationRequest("verification-1", "123456"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.user().id()).isEqualTo("user-1");
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(verification.isUsed()).isTrue();
        verify(verificationRepository).save(verification);
    }

    @Test
    void loginSucceedsAndFailedAttemptsEventuallyThrottle() {
        UserAccount user = user("user-1", "alice", "+8801711111111");
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);
        when(tokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest(" Alice ", "secret123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.user().online()).isTrue();

        AuthService throttledService = authService(false, 2);
        when(userRepository.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> throttledService.login(new LoginRequest("missing", "bad")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
        assertThatThrownBy(() -> throttledService.login(new LoginRequest("missing", "bad")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
        assertThatThrownBy(() -> throttledService.login(new LoginRequest("missing", "bad")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
    }

    @Test
    void authenticateAndLogoutUseTokenRepository() {
        UserAccount user = user("user-1", "alice", "+8801711111111");
        AuthToken token = new AuthToken("hash", "user-1", Instant.now().plusSeconds(60));
        when(tokenRepository.findByTokenHashAndExpiresAtAfter(anyString(), any(Instant.class))).thenReturn(Optional.of(token));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThat(authService.bearerToken("Bearer token-value")).contains("token-value");
        assertThat(authService.authenticateAuthorizationHeader("Bearer token-value")).contains(user);

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(userRepository.save(user)).thenReturn(user);
        authService.logout("Bearer token-value");

        assertThat(user.isOnline()).isFalse();
        verify(tokenRepository).deleteByTokenHash("hash");
    }

    @Test
    void tokenMaxAgeUsesConfiguredHours() {
        assertThat(authService.tokenMaxAgeSeconds()).isEqualTo(7200);
    }

    private AuthService authService(boolean directRegistrationEnabled, int loginMaxAttempts) {
        return new AuthService(
                userRepository,
                tokenRepository,
                verificationRepository,
                passwordEncoder,
                smsSender,
                mediaStorageService,
                2,
                10,
                60,
                10,
                5,
                directRegistrationEnabled,
                loginMaxAttempts,
                300
        );
    }

    private static RegisterRequest registerRequest() {
        return new RegisterRequest("alice", "Alice", "+8801711111111", "secret123");
    }

    private static RegistrationVerification verification(
            String id,
            String username,
            String phoneNumber,
            String codeHash,
            boolean used
    ) {
        RegistrationVerification verification = TestIds.withId(new RegistrationVerification(
                username,
                "Alice",
                phoneNumber,
                "password-hash",
                codeHash,
                Instant.now().plusSeconds(600)
        ), id);
        verification.setUsed(used);
        return verification;
    }

    private static UserAccount user(String id, String username, String phoneNumber) {
        UserAccount user = TestIds.withId(new UserAccount(username, "Alice", "hash"), id);
        user.setPhoneNumber(phoneNumber);
        return user;
    }
}

package com.fariha.chattingapp;

import com.fariha.chattingapp.dto.AuthResponse;
import com.fariha.chattingapp.dto.RegisterRequest;
import com.fariha.chattingapp.dto.RegistrationStartResponse;
import com.fariha.chattingapp.dto.VerifyRegistrationRequest;
import com.fariha.chattingapp.entity.AuthToken;
import com.fariha.chattingapp.entity.RegistrationVerification;
import com.fariha.chattingapp.repository.AuthTokenRepository;
import com.fariha.chattingapp.repository.RegistrationVerificationRepository;
import com.fariha.chattingapp.repository.UserAccountRepository;
import com.fariha.chattingapp.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthServiceIntegrationTests {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private RegistrationVerificationRepository verificationRepository;

    @Autowired
    private AuthTokenRepository tokenRepository;

    @BeforeEach
    void cleanDatabase() {
        tokenRepository.deleteAll();
        verificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void smsRegistrationCreatesUserAndStoresHashedToken() {
        RegistrationStartResponse start = authService.startRegistration(new RegisterRequest(
                "sms_user",
                "SMS User",
                "+8801711111111",
                "secret123"
        ));

        AuthResponse auth = authService.verifyRegistration(new VerifyRegistrationRequest(
                start.verificationId(),
                start.debugCode()
        ));

        List<AuthToken> tokens = tokenRepository.findAll();
        assertThat(auth.user().username()).isEqualTo("sms_user");
        assertThat(auth.token()).isNotBlank();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getTokenHash()).isNotEqualTo(auth.token());
        assertThat(authService.authenticateToken(auth.token())).isPresent();
        assertThat(userRepository.existsByPhoneNumber("+8801711111111")).isTrue();
    }

    @Test
    void invalidVerificationCodeIncrementsAttempts() {
        RegistrationStartResponse start = authService.startRegistration(new RegisterRequest(
                "wrong_code",
                "Wrong Code",
                "+8801722222222",
                "secret123"
        ));
        String wrongCode = "000000".equals(start.debugCode()) ? "111111" : "000000";

        assertThatThrownBy(() -> authService.verifyRegistration(new VerifyRegistrationRequest(
                start.verificationId(),
                wrongCode
        ))).isInstanceOf(ResponseStatusException.class);

        RegistrationVerification verification = verificationRepository.findById(start.verificationId()).orElseThrow();
        assertThat(verification.getAttempts()).isEqualTo(1);
    }

    @Test
    void immediateVerificationResendIsBlocked() {
        RegisterRequest request = new RegisterRequest(
                "resend_user",
                "Resend User",
                "+8801733333333",
                "secret123"
        );

        authService.startRegistration(request);

        assertThatThrownBy(() -> authService.startRegistration(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429 TOO_MANY_REQUESTS");
    }
}

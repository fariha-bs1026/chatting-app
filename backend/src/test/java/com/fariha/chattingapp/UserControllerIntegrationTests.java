package com.fariha.chattingapp;

import com.fariha.chattingapp.dto.AuthResponse;
import com.fariha.chattingapp.dto.RegisterRequest;
import com.fariha.chattingapp.dto.RegistrationStartResponse;
import com.fariha.chattingapp.dto.VerifyRegistrationRequest;
import com.fariha.chattingapp.repository.AuthTokenRepository;
import com.fariha.chattingapp.repository.RegistrationVerificationRepository;
import com.fariha.chattingapp.repository.UserAccountRepository;
import com.fariha.chattingapp.service.AuthService;
import com.fariha.chattingapp.service.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private RegistrationVerificationRepository verificationRepository;

    @Autowired
    private AuthTokenRepository tokenRepository;

    @MockBean
    private MediaStorageService mediaStorageService;

    @BeforeEach
    void cleanDatabase() {
        tokenRepository.deleteAll();
        verificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void authenticatedUserCanUpdateProfileDetailsAndAvatar() throws Exception {
        String token = token("profile_user", "Profile User", "+8801710101010");
        when(mediaStorageService.isOwnedBy(anyString(), anyString())).thenReturn(true);
        when(mediaStorageService.createReadUrl(anyString())).thenReturn("http://localhost:9000/avatar.png");

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Updated User",
                                  "phoneNumber": "+8801710101011",
                                  "avatarKey": "users/profile/avatar.png",
                                  "removeAvatar": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated User"))
                .andExpect(jsonPath("$.phoneNumber").value("+8801710101011"))
                .andExpect(jsonPath("$.avatarUrl").value("http://localhost:9000/avatar.png"));

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Updated User",
                                  "phoneNumber": "+8801710101011",
                                  "avatarKey": null,
                                  "removeAvatar": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").isEmpty());
    }

    @Test
    void profileUpdateRejectsDuplicatePhoneNumber() throws Exception {
        String token = token("first_user", "First User", "+8801720202020");
        token("second_user", "Second User", "+8801730303030");

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "First User",
                                  "phoneNumber": "+8801730303030",
                                  "avatarKey": null,
                                  "removeAvatar": false
                                }
                                """))
                .andExpect(status().isConflict());
    }

    private String token(String username, String displayName, String phoneNumber) {
        RegistrationStartResponse start = authService.startRegistration(new RegisterRequest(
                username,
                displayName,
                phoneNumber,
                "secret123"
        ));
        AuthResponse auth = authService.verifyRegistration(new VerifyRegistrationRequest(
                start.verificationId(),
                start.debugCode()
        ));
        return auth.token();
    }
}

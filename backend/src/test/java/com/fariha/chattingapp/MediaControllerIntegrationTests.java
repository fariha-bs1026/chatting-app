package com.fariha.chattingapp;

import com.fariha.chattingapp.dto.AuthResponse;
import com.fariha.chattingapp.dto.MediaUploadResponse;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MediaControllerIntegrationTests {
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
    void authenticatedUserCanUploadMedia() throws Exception {
        String token = token();
        when(mediaStorageService.uploadMedia(any(MultipartFile.class), any()))
                .thenReturn(new MediaUploadResponse(
                        "users/user-id/2026/05/image.png",
                        "http://localhost:9000/chat-media/users/user-id/2026/05/image.png",
                        "image/png",
                        8
                ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}
        );

        mockMvc.perform(multipart("/api/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("users/user-id/2026/05/image.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void uploadRequiresAFileAndUsesRequestedLocale() throws Exception {
        mockMvc.perform(multipart("/api/media")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "bn"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ফাইল প্রয়োজন"));
    }

    private String token() {
        RegistrationStartResponse start = authService.startRegistration(new RegisterRequest(
                "media_user",
                "Media User",
                "+8801766666666",
                "secret123"
        ));
        AuthResponse auth = authService.verifyRegistration(new VerifyRegistrationRequest(
                start.verificationId(),
                start.debugCode()
        ));
        return auth.token();
    }
}

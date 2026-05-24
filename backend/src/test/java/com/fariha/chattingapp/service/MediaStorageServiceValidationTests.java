package com.fariha.chattingapp.service;

import com.fariha.chattingapp.entity.UserAccount;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStorageServiceValidationTests {
    private final MinioClient minioClient = MinioClient.builder()
            .endpoint("http://localhost:9000")
            .credentials("minioadmin", "minioadmin")
            .build();

    private final MediaStorageService mediaStorageService = new MediaStorageService(
            minioClient,
            minioClient,
            "chat-media",
            1024,
            30
    );

    @Test
    void rejectsUnsupportedContentTypeBeforeCallingStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "hello".getBytes()
        );

        assertThatThrownBy(() -> mediaStorageService.uploadImage(file, user()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void rejectsSpoofedImageBytesBeforeCallingStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                "not a real png".getBytes()
        );

        assertThatThrownBy(() -> mediaStorageService.uploadImage(file, user()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void detectsPngSignature() {
        byte[] pngHeader = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };

        assertThat(MediaStorageService.detectImageContentType(pngHeader)).isEqualTo("image/png");
    }

    private static UserAccount user() {
        return new UserAccount("media_user", "Media User", "hash");
    }
}

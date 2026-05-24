package com.fariha.chattingapp.service;

import com.fariha.chattingapp.dto.MediaUploadResponse;
import com.fariha.chattingapp.entity.UserAccount;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MediaStorageService {
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;
    private final String bucket;
    private final long maxBytes;
    private final int presignedUrlMinutes;
    private volatile boolean bucketReady;

    public MediaStorageService(
            @Qualifier("minioClient") MinioClient minioClient,
            @Qualifier("publicMinioClient") MinioClient publicMinioClient,
            @Value("${app.media.minio.bucket}") String bucket,
            @Value("${app.media.max-bytes:10485760}") long maxBytes,
            @Value("${app.media.presigned-url-minutes:30}") int presignedUrlMinutes
    ) {
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
        this.bucket = bucket;
        this.maxBytes = maxBytes;
        this.presignedUrlMinutes = presignedUrlMinutes;
    }

    public MediaUploadResponse uploadImage(MultipartFile file, UserAccount owner) {
        validateFile(file);
        byte[] bytes = readBytes(file);
        String contentType = detectImageContentType(bytes);
        if (contentType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is not a supported image");
        }

        String objectKey = objectKey(owner.getId(), contentType);
        try {
            ensureBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L)
                            .contentType(contentType)
                            .build()
            );
            return new MediaUploadResponse(objectKey, createReadUrl(objectKey), contentType, bytes.length);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to store media");
        }
    }

    public String createReadUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try {
            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Http.Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(presignedUrlMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to create media URL");
        }
    }

    public boolean isOwnedBy(String objectKey, String userId) {
        return objectKey != null && objectKey.startsWith("users/" + userId + "/");
    }

    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            ensureBucket();
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to delete media");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large");
        }
        String declaredType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(declaredType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded file");
        }
    }

    private synchronized void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketReady = true;
    }

    private static String objectKey(String userId, String contentType) {
        LocalDate today = LocalDate.now();
        return "users/%s/%04d/%02d/%s.%s".formatted(
                userId,
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                extensionFor(contentType)
        );
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    static String detectImageContentType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9')
                && bytes[5] == 'a') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}

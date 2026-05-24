package com.fariha.chattingapp.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    @Bean
    public MinioClient minioClient(
            @Value("${app.media.minio.endpoint}") String endpoint,
            @Value("${app.media.minio.access-key}") String accessKey,
            @Value("${app.media.minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region("us-east-1")
                .build();
    }

    @Bean
    public MinioClient publicMinioClient(
            @Value("${app.media.minio.public-endpoint}") String endpoint,
            @Value("${app.media.minio.access-key}") String accessKey,
            @Value("${app.media.minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region("us-east-1")
                .build();
    }
}

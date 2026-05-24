package com.fariha.chattingapp.dto;

public record MediaUploadResponse(
        String objectKey,
        String assetUrl,
        String contentType,
        long sizeBytes
) {
}

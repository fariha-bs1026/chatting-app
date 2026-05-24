package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.MediaUploadResponse;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.service.MediaStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaStorageService mediaStorageService;

    public MediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @PostMapping
    @Operation(summary = "Upload an image file", security = @SecurityRequirement(name = "bearerAuth"))
    public MediaUploadResponse uploadImage(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return mediaStorageService.uploadImage(file, currentUser);
    }
}

package com.fariha.chattingapp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class SystemController {
    @GetMapping("/")
    public Map<String, String> root() {
        return apiStatus();
    }

    @GetMapping("/api/health")
    public Map<String, String> apiStatus() {
        return Map.of(
                "application", "chatting-app",
                "status", "running",
                "timestamp", Instant.now().toString()
        );
    }
}

package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import com.fariha.chattingapp.service.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/presence")
public class PresenceController {
    private final UserAccountRepository userRepository;

    public PresenceController(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{userId}")
    public PresenceDto getPresence(@PathVariable String userId) {
        return userRepository.findById(userId)
                .map(PresenceDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

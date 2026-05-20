package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import com.fariha.chattingapp.service.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> listUsers(
            @RequestParam(defaultValue = "") String search,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return userService.searchUsers(search, currentUser.getId());
    }
}

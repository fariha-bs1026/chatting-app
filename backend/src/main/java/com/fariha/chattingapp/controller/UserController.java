package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.CurrentUserDto;
import com.fariha.chattingapp.dto.UpdateProfileRequest;
import com.fariha.chattingapp.dto.UserDto;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.service.ConversationUpdateBroadcaster;
import com.fariha.chattingapp.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
    private final UserService userService;
    private final ConversationUpdateBroadcaster conversationUpdateBroadcaster;

    public UserController(UserService userService, ConversationUpdateBroadcaster conversationUpdateBroadcaster) {
        this.userService = userService;
        this.conversationUpdateBroadcaster = conversationUpdateBroadcaster;
    }

    @GetMapping
    public List<UserDto> listUsers(
            @RequestParam(defaultValue = "") @Size(max = 80, message = "{search.size}") String search,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        return userService.searchUsers(search, currentUser.getId());
    }

    @PatchMapping("/me")
    public CurrentUserDto updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserAccount currentUser
    ) {
        CurrentUserDto user = userService.updateProfile(request, currentUser);
        conversationUpdateBroadcaster.broadcastParticipantConversations(currentUser.getId());
        return user;
    }
}

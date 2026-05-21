package com.fariha.chattingapp.service;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserAccountRepository userRepository;

    public UserService(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserDto> searchUsers(String search, String currentUserId) {
        String term = search == null ? "" : search.trim();
        if (term.length() < 2) {
            return List.of();
        }

        List<UserAccount> users = userRepository
                .findTop25ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(term, term);

        return users.stream()
                .filter(user -> !user.getId().equals(currentUserId))
                .map(UserDto::from)
                .toList();
    }
}

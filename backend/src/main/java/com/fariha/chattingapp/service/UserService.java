package com.fariha.chattingapp.service;

import com.fariha.chattingapp.dto.CurrentUserDto;
import com.fariha.chattingapp.dto.UpdateProfileRequest;
import com.fariha.chattingapp.dto.UserDto;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserService {
    private final UserAccountRepository userRepository;
    private final MediaStorageService mediaStorageService;

    public UserService(UserAccountRepository userRepository, MediaStorageService mediaStorageService) {
        this.userRepository = userRepository;
        this.mediaStorageService = mediaStorageService;
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
                .map(this::toUserDto)
                .toList();
    }

    public CurrentUserDto currentUser(UserAccount currentUser) {
        UserAccount user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toCurrentUserDto(user);
    }

    public CurrentUserDto updateProfile(UpdateProfileRequest request, UserAccount currentUser) {
        UserAccount user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String previousAvatarKey = user.getAvatarKey();

        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        userRepository.findByPhoneNumber(phoneNumber)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone number is already registered");
                });

        user.setDisplayName(request.displayName().trim());
        user.setPhoneNumber(phoneNumber);

        String avatarKey = request.avatarKey() == null ? null : request.avatarKey().trim();
        if (request.removeAvatar()) {
            user.setAvatarKey(null);
            user.setAvatarUrl(null);
        } else if (avatarKey != null && !avatarKey.isBlank()) {
            if (!mediaStorageService.isOwnedBy(avatarKey, user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot use another user's media as your profile picture");
            }
            if (!mediaStorageService.isImageObjectKey(avatarKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile picture must be an image");
            }
            user.setAvatarKey(avatarKey);
            user.setAvatarUrl(null);
        }

        CurrentUserDto updatedUser = toCurrentUserDto(userRepository.save(user));
        deleteReplacedAvatar(previousAvatarKey, user.getAvatarKey());
        return updatedUser;
    }

    public UserDto toUserDto(UserAccount user) {
        return UserDto.from(user, resolveAvatarUrl(user));
    }

    public CurrentUserDto toCurrentUserDto(UserAccount user) {
        return CurrentUserDto.from(user, resolveAvatarUrl(user));
    }

    private String resolveAvatarUrl(UserAccount user) {
        if (user.getAvatarKey() == null || user.getAvatarKey().isBlank()) {
            return user.getAvatarUrl();
        }
        return mediaStorageService.createReadUrl(user.getAvatarKey());
    }

    private static String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.trim().replace(" ", "");
    }

    private void deleteReplacedAvatar(String previousAvatarKey, String currentAvatarKey) {
        if (previousAvatarKey == null || previousAvatarKey.isBlank()) {
            return;
        }
        if (previousAvatarKey.equals(currentAvatarKey)) {
            return;
        }
        mediaStorageService.deleteObject(previousAvatarKey);
    }
}

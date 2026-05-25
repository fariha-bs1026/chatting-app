package com.fariha.chattingapp.service;

import com.fariha.chattingapp.TestIds;
import com.fariha.chattingapp.dto.CurrentUserDto;
import com.fariha.chattingapp.dto.UpdateProfileRequest;
import com.fariha.chattingapp.dto.UserDto;
import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTests {
    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private MediaStorageService mediaStorageService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, mediaStorageService);
    }

    @Test
    void searchUsersRequiresTwoCharactersAndDoesNotQueryRepository() {
        assertThat(userService.searchUsers("a", "current-user")).isEmpty();

        verify(userRepository, never())
                .findTop25ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(any(), any());
    }

    @Test
    void searchUsersFiltersCurrentUserAndResolvesAvatarUrls() {
        UserAccount currentUser = user("current-user", "current", "Current User", "+8801711111111");
        UserAccount otherUser = user("other-user", "other", "Other User", "+8801722222222");
        otherUser.setAvatarKey("users/other-user/avatar.png");
        when(userRepository.findTop25ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc("ot", "ot"))
                .thenReturn(List.of(currentUser, otherUser));
        when(mediaStorageService.createReadUrl("users/other-user/avatar.png")).thenReturn("https://signed/avatar.png");

        List<UserDto> users = userService.searchUsers(" ot ", "current-user");

        assertThat(users).hasSize(1);
        assertThat(users.getFirst().id()).isEqualTo("other-user");
        assertThat(users.getFirst().avatarUrl()).isEqualTo("https://signed/avatar.png");
    }

    @Test
    void updateProfileChangesDetailsAndDeletesReplacedAvatar() {
        UserAccount user = user("user-1", "old", "Old Name", "+8801711111111");
        user.setAvatarKey("users/user-1/old.png");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumber("+8801722222222")).thenReturn(Optional.empty());
        when(mediaStorageService.isOwnedBy("users/user-1/new.png", "user-1")).thenReturn(true);
        when(mediaStorageService.isImageObjectKey("users/user-1/new.png")).thenReturn(true);
        when(mediaStorageService.createReadUrl("users/user-1/new.png")).thenReturn("https://signed/new.png");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CurrentUserDto updated = userService.updateProfile(
                new UpdateProfileRequest(" New Name ", "+8801722222222", "users/user-1/new.png", false),
                user
        );

        assertThat(updated.displayName()).isEqualTo("New Name");
        assertThat(updated.phoneNumber()).isEqualTo("+8801722222222");
        assertThat(updated.avatarUrl()).isEqualTo("https://signed/new.png");
        verify(mediaStorageService).deleteObject("users/user-1/old.png");
    }

    @Test
    void updateProfileCanRemoveAvatar() {
        UserAccount user = user("user-1", "old", "Old Name", "+8801711111111");
        user.setAvatarKey("users/user-1/old.png");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumber("+8801711111111")).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CurrentUserDto updated = userService.updateProfile(
                new UpdateProfileRequest("Old Name", "+8801711111111", null, true),
                user
        );

        assertThat(updated.avatarUrl()).isNull();
        assertThat(user.getAvatarKey()).isNull();
        verify(mediaStorageService).deleteObject("users/user-1/old.png");
    }

    @Test
    void updateProfileRejectsDuplicatePhoneNumber() {
        UserAccount user = user("user-1", "alice", "Alice", "+8801711111111");
        UserAccount existing = user("user-2", "bob", "Bob", "+8801722222222");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumber("+8801722222222")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.updateProfile(
                new UpdateProfileRequest("Alice", "+8801722222222", null, false),
                user
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void updateProfileRejectsForeignOrNonImageAvatar() {
        UserAccount user = user("user-1", "alice", "Alice", "+8801711111111");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.findByPhoneNumber("+8801711111111")).thenReturn(Optional.of(user));
        when(mediaStorageService.isOwnedBy("users/user-2/avatar.png", "user-1")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateProfile(
                new UpdateProfileRequest("Alice", "+8801711111111", "users/user-2/avatar.png", false),
                user
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        when(mediaStorageService.isOwnedBy("users/user-1/voice.mp3", "user-1")).thenReturn(true);
        when(mediaStorageService.isImageObjectKey("users/user-1/voice.mp3")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateProfile(
                new UpdateProfileRequest("Alice", "+8801711111111", "users/user-1/voice.mp3", false),
                user
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    private static UserAccount user(String id, String username, String displayName, String phoneNumber) {
        UserAccount user = TestIds.withId(new UserAccount(username, displayName, "hash"), id);
        user.setPhoneNumber(phoneNumber);
        return user;
    }
}

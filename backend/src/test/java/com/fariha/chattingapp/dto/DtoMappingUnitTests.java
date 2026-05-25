package com.fariha.chattingapp.dto;

import com.fariha.chattingapp.TestIds;
import com.fariha.chattingapp.entity.ChatMessage;
import com.fariha.chattingapp.entity.MessageType;
import com.fariha.chattingapp.entity.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DtoMappingUnitTests {
    @Test
    void userDtosMapProfileAndPresenceFields() {
        UserAccount user = TestIds.withId(new UserAccount("alice", "Alice", "hash"), "user-1");
        user.setPhoneNumber("+8801711111111");
        user.setAvatarUrl("https://cdn/avatar.png");
        user.setOnline(true);

        UserDto userDto = UserDto.from(user, "https://signed/avatar.png");
        CurrentUserDto currentUserDto = CurrentUserDto.from(user, "https://signed/avatar.png");

        assertThat(userDto.id()).isEqualTo("user-1");
        assertThat(userDto.avatarUrl()).isEqualTo("https://signed/avatar.png");
        assertThat(userDto.online()).isTrue();
        assertThat(currentUserDto.phoneNumber()).isEqualTo("+8801711111111");
        assertThat(currentUserDto.avatarUrl()).isEqualTo("https://signed/avatar.png");
    }

    @Test
    void messageDtoIncludesMediaDeletionAndStatusFields() {
        UserAccount sender = TestIds.withId(new UserAccount("alice", "Alice", "hash"), "sender-1");
        ChatMessage message = TestIds.withId(new ChatMessage(
                "conversation-1",
                "sender-1",
                "listen",
                MessageType.AUDIO,
                null,
                "users/sender-1/voice.mp3",
                "audio/mpeg"
        ), "message-1");
        Instant expiresAt = Instant.now().plusSeconds(60);
        message.setExpiresAt(expiresAt);

        MessageDto dto = MessageDto.from(
                message,
                sender,
                "https://signed/voice.mp3",
                "https://signed/avatar.png",
                "READ"
        );

        assertThat(dto.id()).isEqualTo("message-1");
        assertThat(dto.sender().avatarUrl()).isEqualTo("https://signed/avatar.png");
        assertThat(dto.assetUrl()).isEqualTo("https://signed/voice.mp3");
        assertThat(dto.assetContentType()).isEqualTo("audio/mpeg");
        assertThat(dto.type()).isEqualTo("AUDIO");
        assertThat(dto.status()).isEqualTo("READ");
        assertThat(dto.deletedForEveryone()).isFalse();
        assertThat(dto.expiresAt()).isEqualTo(expiresAt);
    }
}

package com.fariha.chattingapp.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChatDomainUnitTests {
    @Test
    void conversationDirectKeyIsStableAndHideStateCanBeCleared() {
        Conversation conversation = new Conversation(true, new LinkedHashSet<>(Set.of("bob", "alice")));

        assertThat(conversation.getDirectKey()).isEqualTo("alice:bob");
        assertThat(conversation.hasParticipant("alice")).isTrue();

        Instant beforeHide = conversation.getUpdatedAt();
        conversation.hideFor("alice");

        assertThat(conversation.isHiddenFor("alice")).isTrue();
        assertThat(conversation.getUpdatedAt()).isAfterOrEqualTo(beforeHide);

        conversation.unhideFor("alice");
        assertThat(conversation.isHiddenFor("alice")).isFalse();

        conversation.hideFor("alice");
        conversation.unhideForParticipants();
        assertThat(conversation.isHiddenFor("alice")).isFalse();
    }

    @Test
    void messageCanBeHiddenExpiredAndDeletedForEveryone() {
        ChatMessage message = new ChatMessage(
                "conversation-1",
                "sender-1",
                "hello",
                MessageType.IMAGE,
                null,
                "users/sender-1/photo.png",
                "image/png"
        );
        Instant expiresAt = Instant.now().minusSeconds(1);

        message.hideFor("viewer-1");
        message.setExpiresAt(expiresAt);

        assertThat(message.isHiddenFor("viewer-1")).isTrue();
        assertThat(message.isExpiredAt(Instant.now())).isTrue();

        message.deleteForEveryone("sender-1", expiresAt, true);

        assertThat(message.isDeletedForEveryone()).isTrue();
        assertThat(message.isExpired()).isTrue();
        assertThat(message.getDeletedByUserId()).isEqualTo("sender-1");
        assertThat(message.getDeletedAt()).isEqualTo(expiresAt);
        assertThat(message.getContent()).isEmpty();
        assertThat(message.getAssetKey()).isNull();
        assertThat(message.getAssetContentType()).isNull();
        assertThat(message.getType()).isEqualTo(MessageType.TEXT);
        assertThat(message.isExpiredAt(Instant.now())).isFalse();
    }

    @Test
    void messageReceiptStatusUpdatesTimestamp() {
        MessageReceipt receipt = new MessageReceipt(
                "message-1",
                "conversation-1",
                "viewer-1",
                MessageStatus.SENT
        );
        Instant originalUpdatedAt = receipt.getUpdatedAt();

        receipt.setStatus(MessageStatus.READ);

        assertThat(receipt.getStatus()).isEqualTo(MessageStatus.READ);
        assertThat(receipt.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }
}

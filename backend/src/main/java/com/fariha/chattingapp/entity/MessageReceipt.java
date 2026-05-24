package com.fariha.chattingapp.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "message_receipts")
@CompoundIndex(name = "receipt_message_user_idx", def = "{'messageId': 1, 'userId': 1}", unique = true)
@CompoundIndex(name = "receipt_unread_idx", def = "{'conversationId': 1, 'userId': 1, 'status': 1}")
public class MessageReceipt {
    @Id
    private String id;

    @Indexed
    private String messageId;

    @Indexed
    private String conversationId;

    @Indexed
    private String userId;

    private MessageStatus status = MessageStatus.SENT;
    private Instant updatedAt = Instant.now();

    protected MessageReceipt() {
    }

    public MessageReceipt(String messageId, String conversationId, String userId, MessageStatus status) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.userId = userId;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}

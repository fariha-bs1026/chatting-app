package com.fariha.chattingapp.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "messages")
@CompoundIndex(name = "conversation_created_idx", def = "{'conversationId': 1, 'createdAt': 1}")
public class ChatMessage {
    @Id
    private String id;

    @Indexed
    private String conversationId;

    @Indexed
    private String senderId;

    private String content;
    private String assetUrl;
    private MessageType type = MessageType.TEXT;
    private MessageStatus status = MessageStatus.SENT;
    private Instant createdAt = Instant.now();

    protected ChatMessage() {
    }

    public ChatMessage(String conversationId, String senderId, String content, MessageType type, String assetUrl) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.type = type;
        this.assetUrl = assetUrl;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public String getAssetUrl() {
        return assetUrl;
    }

    public MessageType getType() {
        return type;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }
}

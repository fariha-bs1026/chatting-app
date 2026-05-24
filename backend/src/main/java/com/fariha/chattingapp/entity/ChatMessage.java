package com.fariha.chattingapp.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private String assetKey;
    private String assetContentType;
    private MessageType type = MessageType.TEXT;
    private MessageStatus status = MessageStatus.SENT;
    private Set<String> hiddenForUserIds = new LinkedHashSet<>();
    private boolean deletedForEveryone;
    private boolean expired;
    private String deletedByUserId;
    private Instant deletedAt;
    private Instant expiresAt;
    private Instant createdAt = Instant.now();

    protected ChatMessage() {
    }

    public ChatMessage(String conversationId, String senderId, String content, MessageType type, String assetUrl) {
        this(conversationId, senderId, content, type, assetUrl, null, null);
    }

    public ChatMessage(
            String conversationId,
            String senderId,
            String content,
            MessageType type,
            String assetUrl,
            String assetKey,
            String assetContentType
    ) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.type = type;
        this.assetUrl = assetUrl;
        this.assetKey = assetKey;
        this.assetContentType = assetContentType;
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

    public String getAssetKey() {
        return assetKey;
    }

    public String getAssetContentType() {
        return assetContentType;
    }

    public MessageType getType() {
        return type;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public Set<String> getHiddenForUserIds() {
        if (hiddenForUserIds == null) {
            hiddenForUserIds = new LinkedHashSet<>();
        }
        return hiddenForUserIds;
    }

    public boolean isDeletedForEveryone() {
        return deletedForEveryone;
    }

    public boolean isExpired() {
        return expired;
    }

    public String getDeletedByUserId() {
        return deletedByUserId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public boolean isHiddenFor(String userId) {
        return getHiddenForUserIds().contains(userId);
    }

    public void hideFor(String userId) {
        getHiddenForUserIds().add(userId);
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isExpiredAt(Instant now) {
        return !deletedForEveryone && expiresAt != null && !expiresAt.isAfter(now);
    }

    public void deleteForEveryone(String userId, Instant when, boolean expired) {
        this.deletedForEveryone = true;
        this.expired = expired;
        this.deletedByUserId = userId;
        this.deletedAt = when;
        this.content = "";
        this.assetUrl = null;
        this.assetKey = null;
        this.assetContentType = null;
        this.type = MessageType.TEXT;
    }
}

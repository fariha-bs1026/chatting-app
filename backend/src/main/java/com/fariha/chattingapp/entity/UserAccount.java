package com.fariha.chattingapp.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
public class UserAccount {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String displayName;
    private String passwordHash;
    private String avatarUrl;
    private Instant createdAt = Instant.now();
    private Instant lastSeenAt;
    private boolean online;

    protected UserAccount() {
    }

    public UserAccount(String username, String displayName, String passwordHash) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.lastSeenAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean isOnline() {
        return online;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}

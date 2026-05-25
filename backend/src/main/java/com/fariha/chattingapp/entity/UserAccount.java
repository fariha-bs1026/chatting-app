package com.fariha.chattingapp.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "users")
public class UserAccount {
    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String displayName;
    @Indexed(unique = true, sparse = true)
    @Setter
    private String phoneNumber;
    private String passwordHash;
    @Setter
    private String avatarUrl;
    @Setter
    private String avatarKey;
    private Instant createdAt = Instant.now();
    @Setter
    private Instant lastSeenAt;
    @Setter
    private boolean online;

    public UserAccount(String username, String displayName, String passwordHash) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.lastSeenAt = Instant.now();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

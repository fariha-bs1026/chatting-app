package com.fariha.chattingapp.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "registration_verifications")
public class RegistrationVerification {
    @Id
    private String id;

    @Indexed
    private String username;

    @Indexed
    private String phoneNumber;

    private String displayName;
    private String passwordHash;
    private String codeHash;
    private int attempts;
    private boolean used;
    private Instant createdAt = Instant.now();

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    protected RegistrationVerification() {
    }

    public RegistrationVerification(
            String username,
            String displayName,
            String phoneNumber,
            String passwordHash,
            String codeHash,
            Instant expiresAt
    ) {
        this.username = username;
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
        this.passwordHash = passwordHash;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isUsed() {
        return used;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void incrementAttempts() {
        attempts++;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }
}

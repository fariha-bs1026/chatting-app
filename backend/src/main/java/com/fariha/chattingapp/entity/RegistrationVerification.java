package com.fariha.chattingapp.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "registration_verifications")
@CompoundIndex(name = "verification_phone_created_idx", def = "{'phoneNumber': 1, 'createdAt': -1}")
@CompoundIndex(name = "verification_username_created_idx", def = "{'username': 1, 'createdAt': -1}")
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
    @Setter
    private boolean used;
    private Instant createdAt = Instant.now();

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

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

    public void incrementAttempts() {
        attempts++;
    }
}

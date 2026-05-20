package com.fariha.chattingapp.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Document(collection = "conversations")
public class Conversation {
    @Id
    private String id;

    private boolean direct;
    private String name;
    private String description;
    private String creatorId;

    @Indexed
    private Set<String> participantIds = new LinkedHashSet<>();

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected Conversation() {
    }

    public Conversation(boolean direct, Set<String> participantIds) {
        this.direct = direct;
        this.participantIds = new LinkedHashSet<>(participantIds);
    }

    public Conversation(boolean direct, String name, String description, String creatorId, Set<String> participantIds) {
        this.direct = direct;
        this.name = name;
        this.description = description;
        this.creatorId = creatorId;
        this.participantIds = new LinkedHashSet<>(participantIds);
    }

    public String getId() {
        return id;
    }

    public boolean isDirect() {
        return direct;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public Set<String> getParticipantIds() {
        return participantIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasParticipant(String userId) {
        return participantIds.contains(userId);
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}

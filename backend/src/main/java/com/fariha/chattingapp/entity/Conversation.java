package com.fariha.chattingapp.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "conversations")
@CompoundIndex(name = "participant_updated_idx", def = "{'participantIds': 1, 'updatedAt': -1}")
public class Conversation {
    @Id
    private String id;

    private boolean direct;
    private String name;
    private String description;
    private String creatorId;

    @Indexed(unique = true, sparse = true)
    private String directKey;

    @Indexed
    private Set<String> participantIds = new LinkedHashSet<>();

    @Indexed
    private Set<String> hiddenForUserIds = new LinkedHashSet<>();

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Conversation(boolean direct, Set<String> participantIds) {
        this.direct = direct;
        this.participantIds = new LinkedHashSet<>(participantIds);
        this.directKey = direct ? directKeyFor(participantIds) : null;
    }

    public Conversation(boolean direct, String name, String description, String creatorId, Set<String> participantIds) {
        this.direct = direct;
        this.name = name;
        this.description = description;
        this.creatorId = creatorId;
        this.participantIds = new LinkedHashSet<>(participantIds);
    }

    public Set<String> getHiddenForUserIds() {
        if (hiddenForUserIds == null) {
            hiddenForUserIds = new LinkedHashSet<>();
        }
        return hiddenForUserIds;
    }

    public boolean hasParticipant(String userId) {
        return participantIds.contains(userId);
    }

    public boolean isHiddenFor(String userId) {
        return getHiddenForUserIds().contains(userId);
    }

    public void hideFor(String userId) {
        getHiddenForUserIds().add(userId);
        touch();
    }

    public void unhideFor(String userId) {
        getHiddenForUserIds().remove(userId);
    }

    public void unhideForParticipants() {
        getHiddenForUserIds().removeAll(participantIds);
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public static String directKeyFor(Set<String> participantIds) {
        return participantIds.stream()
                .sorted()
                .collect(Collectors.joining(":"));
    }
}

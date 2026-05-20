package com.fariha.chattingapp.repository;

import com.fariha.chattingapp.entity.Conversation;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ConversationRepository extends MongoRepository<Conversation, String> {
    @Query("{ 'direct': true, 'participantIds': { $all: ?0, $size: 2 } }")
    Optional<Conversation> findDirectConversation(Set<String> userIds);

    @Query("{ 'participantIds': ?0 }")
    List<Conversation> findForParticipant(String userId, Sort sort);
}

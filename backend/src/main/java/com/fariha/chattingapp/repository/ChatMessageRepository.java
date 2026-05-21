package com.fariha.chattingapp.repository;

import com.fariha.chattingapp.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<ChatMessage> findByConversationId(String conversationId, Pageable pageable);

    List<ChatMessage> findByConversationIdAndCreatedAtBefore(String conversationId, Instant before, Pageable pageable);

    Optional<ChatMessage> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);
}

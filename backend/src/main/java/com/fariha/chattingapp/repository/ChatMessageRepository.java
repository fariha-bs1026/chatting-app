package com.fariha.chattingapp.repository;

import com.fariha.chattingapp.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    Optional<ChatMessage> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);
}

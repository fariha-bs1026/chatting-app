package com.fariha.chattingapp.repository;

import com.fariha.chattingapp.entity.MessageReceipt;
import com.fariha.chattingapp.entity.MessageStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MessageReceiptRepository extends MongoRepository<MessageReceipt, String> {
    List<MessageReceipt> findByMessageId(String messageId);

    Optional<MessageReceipt> findByMessageIdAndUserId(String messageId, String userId);

    long countByConversationIdAndUserIdAndStatusNot(String conversationId, String userId, MessageStatus status);

    void deleteByMessageId(String messageId);

    void deleteByConversationId(String conversationId);
}

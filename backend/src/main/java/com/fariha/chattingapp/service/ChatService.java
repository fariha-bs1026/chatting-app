package com.fariha.chattingapp.service;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatService {
    private final UserAccountRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public ChatService(
            UserAccountRepository userRepository,
            ConversationRepository conversationRepository,
            ChatMessageRepository messageRepository
    ) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public ConversationDto createDirectConversation(String otherUserId, UserAccount currentUser) {
        if (currentUser.getId().equals(otherUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create a conversation with yourself");
        }

        UserAccount current = getUser(currentUser.getId());
        UserAccount other = getUser(otherUserId);
        Set<String> participantIds = Set.of(current.getId(), other.getId());

        Conversation conversation = conversationRepository.findDirectConversation(participantIds)
                .orElseGet(() -> conversationRepository.save(new Conversation(true, participantIds)));

        return toConversationDto(conversation);
    }

    public ConversationDto createGroup(CreateGroupRequest request, UserAccount currentUser) {
        UserAccount creator = getUser(currentUser.getId());
        String name = request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        Set<String> memberIds = new LinkedHashSet<>();
        memberIds.add(creator.getId());
        if (request.memberIds() != null) {
            memberIds.addAll(request.memberIds());
        }
        memberIds.forEach(this::getUser);

        Conversation conversation = conversationRepository.save(
                new Conversation(false, name, description, creator.getId(), memberIds)
        );

        return toConversationDto(conversation);
    }

    public List<ConversationDto> listConversations(UserAccount currentUser) {
        return conversationRepository.findForParticipant(currentUser.getId(), Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .map(this::toConversationDto)
                .toList();
    }

    public List<MessageDto> getMessages(String conversationId, UserAccount currentUser) {
        requireParticipant(conversationId, currentUser.getId());
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toMessageDto)
                .toList();
    }

    public MessageDto sendMessage(SendMessageRequest request, UserAccount sender) {
        Conversation conversation = requireParticipant(request.conversationId(), sender.getId());
        UserAccount managedSender = getUser(sender.getId());
        MessageType type = parseType(request.type());
        String content = request.content() == null ? "" : request.content().trim();
        String assetUrl = request.assetUrl() == null || request.assetUrl().isBlank() ? null : request.assetUrl().trim();
        if (content.isBlank() && assetUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content or asset URL is required");
        }

        ChatMessage message = messageRepository.save(new ChatMessage(
                conversation.getId(),
                managedSender.getId(),
                content,
                type,
                assetUrl
        ));
        conversation.touch();
        conversationRepository.save(conversation);

        return MessageDto.from(message, managedSender);
    }

    public MessageDto updateMessageStatus(String messageId, MessageStatus status, UserAccount currentUser) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        Conversation conversation = requireParticipant(message.getConversationId(), currentUser.getId());
        message.setStatus(status);
        ChatMessage savedMessage = messageRepository.save(message);
        conversation.touch();
        conversationRepository.save(conversation);
        return toMessageDto(savedMessage);
    }

    public ConversationDto getConversation(String conversationId, UserAccount currentUser) {
        Conversation conversation = requireParticipant(conversationId, currentUser.getId());
        return toConversationDto(conversation);
    }

    private ConversationDto toConversationDto(Conversation conversation) {
        List<UserDto> participants = userRepository.findAllById(conversation.getParticipantIds())
                .stream()
                .map(UserDto::from)
                .toList();

        MessageDto lastMessage = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(this::toMessageDto)
                .orElse(null);

        return new ConversationDto(
                conversation.getId(),
                conversation.isDirect(),
                conversation.getName(),
                conversation.getDescription(),
                participants,
                lastMessage,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private MessageDto toMessageDto(ChatMessage message) {
        UserAccount sender = getUser(message.getSenderId());
        return MessageDto.from(message, sender);
    }

    private Conversation requireParticipant(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.hasParticipant(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this conversation");
        }
        return conversation;
    }

    private UserAccount getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private static MessageType parseType(String value) {
        if (value == null || value.isBlank()) {
            return MessageType.TEXT;
        }
        try {
            return MessageType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported message type");
        }
    }

    public static MessageStatus parseStatus(String value) {
        try {
            return MessageStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported message status");
        }
    }
}

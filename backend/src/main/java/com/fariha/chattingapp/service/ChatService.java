package com.fariha.chattingapp.service;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatService {
    public record ConversationBroadcast(String username, ConversationDto conversation) {
    }

    private final UserAccountRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final MessageReceiptRepository receiptRepository;
    private final MediaStorageService mediaStorageService;

    public ChatService(
            UserAccountRepository userRepository,
            ConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            MessageReceiptRepository receiptRepository,
            MediaStorageService mediaStorageService
    ) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.receiptRepository = receiptRepository;
        this.mediaStorageService = mediaStorageService;
    }

    public ConversationDto createDirectConversation(String otherUserId, UserAccount currentUser) {
        if (currentUser.getId().equals(otherUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create a conversation with yourself");
        }

        UserAccount current = getUser(currentUser.getId());
        UserAccount other = getUser(otherUserId);
        Set<String> participantIds = Set.of(current.getId(), other.getId());
        String directKey = Conversation.directKeyFor(participantIds);

        Conversation conversation = conversationRepository.findByDirectKey(directKey)
                .or(() -> conversationRepository.findDirectConversation(participantIds))
                .orElseGet(() -> conversationRepository.save(new Conversation(true, participantIds)));
        if (conversation.isHiddenFor(current.getId())) {
            conversation.unhideFor(current.getId());
            conversation.touch();
            conversation = conversationRepository.save(conversation);
        }

        return toConversationDto(conversation, current.getId());
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

        return toConversationDto(conversation, creator.getId());
    }

    public List<ConversationDto> listConversations(UserAccount currentUser) {
        return conversationRepository.findForParticipant(currentUser.getId(), Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .filter(conversation -> !conversation.isHiddenFor(currentUser.getId()))
                .map(conversation -> toConversationDto(conversation, currentUser.getId()))
                .toList();
    }

    public MessagePageResponse getMessages(String conversationId, Instant before, Integer requestedLimit, UserAccount currentUser) {
        requireVisibleParticipant(conversationId, currentUser.getId());
        int limit = normalizeLimit(requestedLimit);
        PageRequest pageRequest = PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ChatMessage> page = before == null
                ? messageRepository.findByConversationId(conversationId, pageRequest)
                : messageRepository.findByConversationIdAndCreatedAtBefore(conversationId, before, pageRequest);

        boolean hasMore = page.size() > limit;
        List<ChatMessage> currentPage = new ArrayList<>(hasMore ? page.subList(0, limit) : page);
        refreshExpiredMessages(currentPage);
        Collections.reverse(currentPage);

        List<MessageDto> messages = currentPage
                .stream()
                .filter(message -> !message.isHiddenFor(currentUser.getId()))
                .map(message -> toMessageDto(message, currentUser.getId()))
                .toList();
        Instant nextBefore = hasMore && !currentPage.isEmpty() ? currentPage.get(0).getCreatedAt() : null;

        return new MessagePageResponse(messages, nextBefore, hasMore);
    }

    public MessageDto sendMessage(SendMessageRequest request, UserAccount sender) {
        Conversation conversation = requireParticipant(request.conversationId(), sender.getId());
        UserAccount managedSender = getUser(sender.getId());
        MessageType type = parseType(request.type());
        String content = request.content() == null ? "" : request.content().trim();
        String assetUrl = request.assetUrl() == null || request.assetUrl().isBlank() ? null : request.assetUrl().trim();
        String assetKey = request.assetKey() == null || request.assetKey().isBlank() ? null : request.assetKey().trim();
        if (assetUrl != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "External media URLs are not supported");
        }
        if (assetKey != null && !mediaStorageService.isOwnedBy(assetKey, managedSender.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot attach media uploaded by another user");
        }
        if (content.isBlank() && assetKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content or media is required");
        }
        if (type == MessageType.TEXT && assetKey != null) {
            type = MessageType.IMAGE;
        }
        if (type != MessageType.TEXT && assetKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media is required for image or file messages");
        }

        ChatMessage message = messageRepository.save(new ChatMessage(
                conversation.getId(),
                managedSender.getId(),
                content,
                type,
                null,
                assetKey,
                null
        ));
        message.setExpiresAt(expiresAt(request.expiresInSeconds()));
        message = messageRepository.save(message);
        createReceipts(message, conversation, managedSender.getId());
        conversation.touch();
        conversation.unhideForParticipants();
        conversationRepository.save(conversation);

        return toMessageDto(message, managedSender.getId());
    }

    public MessageDto updateMessageStatus(String messageId, MessageStatus status, UserAccount currentUser) {
        ChatMessage message = refreshExpiredMessage(messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found")));
        Conversation conversation = requireParticipant(message.getConversationId(), currentUser.getId());
        if (message.isDeletedForEveryone() || message.isHiddenFor(currentUser.getId())) {
            return toMessageDto(message, currentUser.getId());
        }
        if (message.getSenderId().equals(currentUser.getId()) && status != MessageStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot update status of your own message");
        }
        MessageReceipt receipt = receiptRepository.findByMessageIdAndUserId(message.getId(), currentUser.getId())
                .orElseGet(() -> receiptRepository.save(
                        new MessageReceipt(message.getId(), message.getConversationId(), currentUser.getId(), MessageStatus.SENT)
                ));
        if (status.ordinal() < receipt.getStatus().ordinal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message status cannot move backward");
        }
        receipt.setStatus(status);
        receiptRepository.save(receipt);

        message.setStatus(aggregateStatus(message));
        ChatMessage savedMessage = messageRepository.save(message);
        conversation.touch();
        conversationRepository.save(conversation);
        return toMessageDto(savedMessage, currentUser.getId());
    }

    public MessageDeletionResponse deleteMessageForMe(String messageId, UserAccount currentUser) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        requireParticipant(message.getConversationId(), currentUser.getId());
        message.hideFor(currentUser.getId());
        messageRepository.save(message);
        markReceiptRead(message.getId(), currentUser.getId());
        return new MessageDeletionResponse(message.getId(), message.getConversationId(), "ME");
    }

    public MessageDto deleteMessageForEveryone(String messageId, UserAccount currentUser) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        Conversation conversation = requireParticipant(message.getConversationId(), currentUser.getId());
        if (!message.getSenderId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender can delete this message for everyone");
        }
        if (!message.isDeletedForEveryone()) {
            message.deleteForEveryone(currentUser.getId(), Instant.now(), false);
            message = messageRepository.save(message);
            markAllReceiptsRead(message.getId());
            conversation.touch();
            conversationRepository.save(conversation);
        }
        return toMessageDto(message, currentUser.getId());
    }

    public MessageStatus messageAggregateStatus(String messageId) {
        ChatMessage message = refreshExpiredMessage(messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found")));
        return aggregateStatus(message);
    }

    public ConversationDto getConversation(String conversationId, UserAccount currentUser) {
        Conversation conversation = requireVisibleParticipant(conversationId, currentUser.getId());
        return toConversationDto(conversation, currentUser.getId());
    }

    public void deleteConversationForUser(String conversationId, UserAccount currentUser) {
        Conversation conversation = requireParticipant(conversationId, currentUser.getId());
        conversation.hideFor(currentUser.getId());
        conversationRepository.save(conversation);
    }

    public boolean isParticipant(String conversationId, String userId) {
        return conversationRepository.findById(conversationId)
                .map(conversation -> conversation.hasParticipant(userId))
                .orElse(false);
    }

    public List<ConversationBroadcast> conversationBroadcasts(String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        return conversationBroadcasts(conversation);
    }

    public List<ConversationBroadcast> visibleConversationBroadcastsForParticipant(String userId) {
        return conversationRepository.findForParticipant(userId, Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .flatMap(conversation -> conversationBroadcasts(conversation).stream())
                .toList();
    }

    private List<ConversationBroadcast> conversationBroadcasts(Conversation conversation) {
        return userRepository.findAllById(conversation.getParticipantIds())
                .stream()
                .filter(user -> !conversation.isHiddenFor(user.getId()))
                .map(user -> new ConversationBroadcast(user.getUsername(), toConversationDto(conversation, user.getId())))
                .toList();
    }

    private ConversationDto toConversationDto(Conversation conversation, String viewerId) {
        List<UserDto> participants = userRepository.findAllById(conversation.getParticipantIds())
                .stream()
                .map(this::toUserDto)
                .toList();

        MessageDto lastMessage = findLastVisibleMessage(conversation.getId(), viewerId)
                .map(message -> toMessageDto(message, viewerId))
                .orElse(null);
        long unreadCount = receiptRepository.countByConversationIdAndUserIdAndStatusNot(
                conversation.getId(),
                viewerId,
                MessageStatus.READ
        );

        return new ConversationDto(
                conversation.getId(),
                conversation.isDirect(),
                conversation.getName(),
                conversation.getDescription(),
                participants,
                lastMessage,
                unreadCount,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private MessageDto toMessageDto(ChatMessage message, String viewerId) {
        message = refreshExpiredMessage(message);
        UserAccount sender = getUser(message.getSenderId());
        String assetUrl = message.isDeletedForEveryone()
                ? null
                : message.getAssetKey() == null
                ? message.getAssetUrl()
                : mediaStorageService.createReadUrl(message.getAssetKey());
        MessageStatus status = messageStatusForViewer(message, viewerId);
        return MessageDto.from(message, sender, assetUrl, resolveAvatarUrl(sender), status.name());
    }

    private Optional<ChatMessage> findLastVisibleMessage(String conversationId, String viewerId) {
        List<ChatMessage> recentMessages = messageRepository.findByConversationId(
                conversationId,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        refreshExpiredMessages(recentMessages);
        return recentMessages.stream()
                .filter(message -> !message.isHiddenFor(viewerId))
                .findFirst();
    }

    private void createReceipts(ChatMessage message, Conversation conversation, String senderId) {
        conversation.getParticipantIds()
                .stream()
                .filter(participantId -> !participantId.equals(senderId))
                .map(participantId -> new MessageReceipt(
                        message.getId(),
                        conversation.getId(),
                        participantId,
                        MessageStatus.SENT
                ))
                .forEach(receiptRepository::save);
    }

    private MessageStatus messageStatusForViewer(ChatMessage message, String viewerId) {
        if (message.getSenderId().equals(viewerId)) {
            return aggregateStatus(message);
        }
        return receiptRepository.findByMessageIdAndUserId(message.getId(), viewerId)
                .map(MessageReceipt::getStatus)
                .orElse(message.getStatus());
    }

    private MessageStatus aggregateStatus(ChatMessage message) {
        List<MessageReceipt> receipts = receiptRepository.findByMessageId(message.getId());
        if (receipts.isEmpty()) {
            return message.getStatus();
        }
        if (receipts.stream().allMatch(receipt -> receipt.getStatus() == MessageStatus.READ)) {
            return MessageStatus.READ;
        }
        if (receipts.stream().allMatch(receipt -> receipt.getStatus().ordinal() >= MessageStatus.DELIVERED.ordinal())) {
            return MessageStatus.DELIVERED;
        }
        return MessageStatus.SENT;
    }

    private void refreshExpiredMessages(List<ChatMessage> messages) {
        messages.forEach(this::refreshExpiredMessage);
    }

    private ChatMessage refreshExpiredMessage(ChatMessage message) {
        if (!message.isExpiredAt(Instant.now())) {
            return message;
        }
        message.deleteForEveryone(null, message.getExpiresAt(), true);
        ChatMessage savedMessage = messageRepository.save(message);
        markAllReceiptsRead(savedMessage.getId());
        return savedMessage;
    }

    private void markReceiptRead(String messageId, String userId) {
        receiptRepository.findByMessageIdAndUserId(messageId, userId).ifPresent(receipt -> {
            receipt.setStatus(MessageStatus.READ);
            receiptRepository.save(receipt);
        });
    }

    private void markAllReceiptsRead(String messageId) {
        receiptRepository.findByMessageId(messageId).forEach(receipt -> {
            receipt.setStatus(MessageStatus.READ);
            receiptRepository.save(receipt);
        });
    }

    private static Instant expiresAt(Integer expiresInSeconds) {
        if (expiresInSeconds == null) {
            return null;
        }
        if (expiresInSeconds < 1 || expiresInSeconds > 604800) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Temporary message duration must be between 1 second and 7 days");
        }
        return Instant.now().plusSeconds(expiresInSeconds);
    }

    private UserDto toUserDto(UserAccount user) {
        return UserDto.from(user, resolveAvatarUrl(user));
    }

    private String resolveAvatarUrl(UserAccount user) {
        if (user.getAvatarKey() == null || user.getAvatarKey().isBlank()) {
            return user.getAvatarUrl();
        }
        return mediaStorageService.createReadUrl(user.getAvatarKey());
    }

    private Conversation requireParticipant(String conversationId, String userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if (!conversation.hasParticipant(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant in this conversation");
        }
        return conversation;
    }

    private Conversation requireVisibleParticipant(String conversationId, String userId) {
        Conversation conversation = requireParticipant(conversationId, userId);
        if (conversation.isHiddenFor(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
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

    private static int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return 50;
        }
        return Math.max(1, Math.min(requestedLimit, 100));
    }
}

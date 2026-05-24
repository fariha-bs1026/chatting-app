package com.fariha.chattingapp.config;

import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import com.fariha.chattingapp.service.*;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private final AuthService authService;
    private final UserAccountRepository userRepository;
    private final ConversationRepository conversationRepository;

    public WebSocketAuthChannelInterceptor(
            AuthService authService,
            UserAccountRepository userRepository,
            ConversationRepository conversationRepository
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String username = authService.authenticateAuthorizationHeader(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION))
                    .map(UserAccount::getUsername)
                    .or(() -> usernameFromHandshake(accessor))
                    .orElseThrow(() -> new AccessDeniedException("Invalid WebSocket token"));
            accessor.setUser(new StompPrincipal(username));
        }
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith("/user/")) {
            if (accessor.getUser() == null) {
                throw new AccessDeniedException("WebSocket subscription requires authentication");
            }
            if (!"/user/queue/conversations".equals(destination)) {
                throw new AccessDeniedException("Unsupported user subscription");
            }
            return;
        }

        String conversationId = extractConversationId(destination);
        if (conversationId == null) {
            return;
        }
        if (accessor.getUser() == null) {
            throw new AccessDeniedException("WebSocket subscription requires authentication");
        }

        UserAccount user = userRepository.findByUsernameIgnoreCase(accessor.getUser().getName())
                .orElseThrow(() -> new AccessDeniedException("Invalid WebSocket user"));
        boolean participant = conversationRepository.findById(conversationId)
                .map(conversation -> conversation.hasParticipant(user.getId()))
                .orElse(false);
        if (!participant) {
            throw new AccessDeniedException("You are not a participant in this conversation");
        }
    }

    private static java.util.Optional<String> usernameFromHandshake(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return java.util.Optional.empty();
        }
        Object username = attributes.get(CookieAuthHandshakeInterceptor.USERNAME_ATTRIBUTE);
        return username instanceof String value && !value.isBlank()
                ? java.util.Optional.of(value)
                : java.util.Optional.empty();
    }

    private static String extractConversationId(String destination) {
        String prefix = "/topic/conversations/";
        if (destination == null || !destination.startsWith(prefix)) {
            return null;
        }
        String remainder = destination.substring(prefix.length());
        if (remainder.isBlank()) {
            return null;
        }
        int separatorIndex = remainder.indexOf('/');
        return separatorIndex == -1 ? remainder : remainder.substring(0, separatorIndex);
    }
}

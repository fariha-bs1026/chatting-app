package com.fariha.chattingapp.config;

import com.fariha.chattingapp.entity.*;
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

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {
    private final AuthService authService;

    public WebSocketAuthChannelInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            UserAccount user = authService.authenticateAuthorizationHeader(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION))
                    .orElseThrow(() -> new AccessDeniedException("Invalid WebSocket token"));
            accessor.setUser(new StompPrincipal(user.getUsername()));
        }
        return message;
    }
}

package com.fariha.chattingapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketAuthChannelInterceptor authChannelInterceptor;
    private final CookieAuthHandshakeInterceptor cookieAuthHandshakeInterceptor;
    private final String allowedOrigins;

    public WebSocketConfig(
            WebSocketAuthChannelInterceptor authChannelInterceptor,
            CookieAuthHandshakeInterceptor cookieAuthHandshakeInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins
    ) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.cookieAuthHandshakeInterceptor = cookieAuthHandshakeInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(parseAllowedOrigins())
                .addInterceptors(cookieAuthHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(WebSocketDestinations.TOPIC_PREFIX, WebSocketDestinations.QUEUE_PREFIX);
        registry.setApplicationDestinationPrefixes(WebSocketDestinations.APPLICATION_PREFIX);
        registry.setUserDestinationPrefix(WebSocketDestinations.USER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    private String[] parseAllowedOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
    }
}

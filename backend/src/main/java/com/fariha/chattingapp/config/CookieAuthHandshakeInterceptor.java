package com.fariha.chattingapp.config;

import com.fariha.chattingapp.entity.UserAccount;
import com.fariha.chattingapp.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class CookieAuthHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USERNAME_ATTRIBUTE = "chatflowUsername";

    private final AuthService authService;

    public CookieAuthHandshakeInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            authenticateCookie(servletRequest.getServletRequest(), attributes);
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private void authenticateCookie(HttpServletRequest request, Map<String, Object> attributes) {
        if (request.getCookies() == null) {
            return;
        }
        for (Cookie cookie : request.getCookies()) {
            if (TokenAuthenticationFilter.AUTH_COOKIE_NAME.equals(cookie.getName())) {
                authService.authenticateToken(cookie.getValue())
                        .map(UserAccount::getUsername)
                        .ifPresent(username -> attributes.put(USERNAME_ATTRIBUTE, username));
                return;
            }
        }
    }
}

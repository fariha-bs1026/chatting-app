package com.fariha.chattingapp.config;

import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.service.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTH_COOKIE_NAME = "CHATFLOW_AUTH";

    private final AuthService authService;

    public TokenAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authService.authenticateAuthorizationHeader(request.getHeader(HttpHeaders.AUTHORIZATION))
                    .or(() -> authenticateCookie(request))
                    .ifPresent(this::authenticate);
        }
        filterChain.doFilter(request, response);
    }

    private java.util.Optional<UserAccount> authenticateCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return java.util.Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                return authService.authenticateToken(cookie.getValue());
            }
        }
        return java.util.Optional.empty();
    }

    private void authenticate(UserAccount user) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

package com.fariha.chattingapp.controller;

import com.fariha.chattingapp.dto.*;
import com.fariha.chattingapp.entity.*;
import com.fariha.chattingapp.repository.*;
import com.fariha.chattingapp.service.*;

import com.fariha.chattingapp.config.TokenAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;
    private final boolean secureAuthCookie;

    public AuthController(
            AuthService authService,
            UserService userService,
            @Value("${app.auth.cookie-secure:false}") boolean secureAuthCookie
    ) {
        this.authService = authService;
        this.userService = userService;
        this.secureAuthCookie = secureAuthCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authResponse(authService.register(request));
    }

    @PostMapping("/register/start")
    public RegistrationStartResponse startRegistration(@Valid @RequestBody RegisterRequest request) {
        return authService.startRegistration(request);
    }

    @PostMapping("/register/verify")
    public ResponseEntity<AuthResponse> verifyRegistration(@Valid @RequestBody VerifyRegistrationRequest request) {
        return authResponse(authService.verifyRegistration(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authResponse(authService.login(request));
    }

    @GetMapping("/me")
    public CurrentUserDto me(@AuthenticationPrincipal UserAccount currentUser) {
        return userService.currentUser(currentUser);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletRequest request
    ) {
        authService.logout(authorizationHeader);
        authTokenCookie(request).ifPresent(authService::logoutToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredAuthCookie().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> authResponse(AuthResponse auth) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookie(auth.token()).toString())
                .body(new AuthResponse(null, auth.user()));
    }

    private ResponseCookie authCookie(String token) {
        return ResponseCookie.from(TokenAuthenticationFilter.AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureAuthCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(authService.tokenMaxAgeSeconds())
                .build();
    }

    private ResponseCookie expiredAuthCookie() {
        return ResponseCookie.from(TokenAuthenticationFilter.AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureAuthCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }

    private java.util.Optional<String> authTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return java.util.Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (TokenAuthenticationFilter.AUTH_COOKIE_NAME.equals(cookie.getName())) {
                return java.util.Optional.ofNullable(cookie.getValue());
            }
        }
        return java.util.Optional.empty();
    }
}

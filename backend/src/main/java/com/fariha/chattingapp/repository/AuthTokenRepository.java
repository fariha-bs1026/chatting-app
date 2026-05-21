package com.fariha.chattingapp.repository;

import com.fariha.chattingapp.entity.AuthToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends MongoRepository<AuthToken, String> {
    Optional<AuthToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    Optional<AuthToken> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}

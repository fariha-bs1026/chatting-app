package com.fariha.chattingapp.repository;

import com.fariha.chattingapp.entity.RegistrationVerification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RegistrationVerificationRepository extends MongoRepository<RegistrationVerification, String> {
    void deleteByUsernameIgnoreCase(String username);

    void deleteByPhoneNumber(String phoneNumber);

    Optional<RegistrationVerification> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, Instant createdAfter);

    long countByUsernameIgnoreCaseAndCreatedAtAfter(String username, Instant createdAfter);

    List<RegistrationVerification> findByPhoneNumberAndUsedFalse(String phoneNumber);

    List<RegistrationVerification> findByUsernameIgnoreCaseAndUsedFalse(String username);
}

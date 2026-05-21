package com.fariha.chattingapp.repository;

import com.fariha.chattingapp.entity.UserAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends MongoRepository<UserAccount, String> {
    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByPhoneNumber(String phoneNumber);

    List<UserAccount> findTop25ByIdNotOrderByDisplayNameAsc(String id);

    List<UserAccount> findTop25ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByDisplayNameAsc(
            String username,
            String displayName
    );
}

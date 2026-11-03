package com.ludoarena.repository;

import com.ludoarena.model.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}

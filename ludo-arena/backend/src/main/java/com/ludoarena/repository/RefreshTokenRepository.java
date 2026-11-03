package com.ludoarena.repository;

import com.ludoarena.model.RefreshToken;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}

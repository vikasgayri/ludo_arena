package com.ludoarena.repository;

import com.ludoarena.model.Wallet;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WalletRepository extends MongoRepository<Wallet, String> {
    Optional<Wallet> findByUserId(String userId);
}

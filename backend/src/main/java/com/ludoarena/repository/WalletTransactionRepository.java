package com.ludoarena.repository;

import com.ludoarena.model.WalletTransaction;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Deliberately NOT a CrudRepository: the ledger is append-only, so delete/update-by-save operations
 * are not exposed. Inserts and status transitions go through WalletService (MongoTemplate, conditional updates).
 */
public interface WalletTransactionRepository extends Repository<WalletTransaction, String> {
    Optional<WalletTransaction> findByReferenceId(String referenceId);

    Optional<WalletTransaction> findById(String id);

    Page<WalletTransaction> findByUserId(String userId, Pageable pageable);
}

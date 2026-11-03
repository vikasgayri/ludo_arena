package com.ludoarena.repository;

import com.ludoarena.model.InventoryItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryRepository extends MongoRepository<InventoryItem, String> {
    List<InventoryItem> findByUserId(String userId);

    Page<InventoryItem> findByUserId(String userId, Pageable pageable);

    Optional<InventoryItem> findByUserIdAndItemId(String userId, String itemId);

    boolean existsByUserIdAndItemId(String userId, String itemId);

    long countByUserId(String userId);
}

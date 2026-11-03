package com.ludoarena.repository;

import com.ludoarena.model.StoreItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoreItemRepository extends MongoRepository<StoreItem, String> {
    Optional<StoreItem> findBySku(String sku);

    List<StoreItem> findByActiveTrue();
}

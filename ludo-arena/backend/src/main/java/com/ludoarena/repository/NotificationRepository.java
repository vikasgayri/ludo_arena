package com.ludoarena.repository;

import com.ludoarena.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    Page<Notification> findByUserId(String userId, Pageable pageable);

    long countByUserIdAndReadFalse(String userId);
}

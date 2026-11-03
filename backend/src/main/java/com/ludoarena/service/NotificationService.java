package com.ludoarena.service;

import com.ludoarena.dto.Dtos.NotificationDto;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.UnreadCount;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Notification;
import com.ludoarena.repository.NotificationRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repo;
    private final MongoTemplate mongo;
    private final SimpMessagingTemplate messaging;

    public NotificationService(NotificationRepository repo, MongoTemplate mongo, SimpMessagingTemplate messaging) {
        this.repo = repo;
        this.mongo = mongo;
        this.messaging = messaging;
    }

    /** Persists a notification and pushes it live to the user's open sessions. Never throws. */
    public void notify(String userId, String type, String title, String message) {
        try {
            Notification n = new Notification();
            n.userId = userId;
            n.type = type;
            n.title = title;
            n.message = message;
            n.createdAt = Instant.now();
            n = repo.save(n);
            messaging.convertAndSendToUser(userId, "/queue/notifications", NotificationDto.from(n));
        } catch (RuntimeException e) {
            log.warn("Could not create notification for {}: {}", userId, e.getMessage());
        }
    }

    public PageResponse<NotificationDto> list(String userId, boolean unreadOnly, int page, int size) {
        Criteria c = Criteria.where("userId").is(userId);
        if (unreadOnly) c = c.and("read").is(false);
        Page<Notification> p = MongoPaging.page(mongo, c, MongoPaging.newestFirst(page, size), Notification.class);
        return PageResponse.of(p, NotificationDto::from);
    }

    public UnreadCount unread(String userId) {
        return new UnreadCount(repo.countByUserIdAndReadFalse(userId));
    }

    public void markRead(String userId, String id) {
        var res = mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("userId").is(userId)),
                new Update().set("read", true), Notification.class);
        if (res.getMatchedCount() == 0) throw ApiException.notFound("NOT_FOUND", "Notification not found");
    }

    public long markAllRead(String userId) {
        return mongo.updateMulti(Query.query(Criteria.where("userId").is(userId).and("read").is(false)),
                new Update().set("read", true), Notification.class).getModifiedCount();
    }
}

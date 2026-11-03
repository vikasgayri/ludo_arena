package com.ludoarena.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notifications")
@CompoundIndex(name = "user_read_createdAt", def = "{'userId': 1, 'read': 1, 'createdAt': -1}")
public class Notification {
    @Id
    public String id;
    public String userId;
    public String type;
    public String title;
    public String message;
    public boolean read;
    public Instant createdAt;
}

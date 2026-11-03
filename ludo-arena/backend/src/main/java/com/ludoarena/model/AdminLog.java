package com.ludoarena.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("adminLogs")
public class AdminLog {
    @Id
    public String id;
    public String adminId;
    public String adminEmail;
    public String action;
    public String targetType;
    public String targetId;
    public Map<String, Object> details = new LinkedHashMap<>();
    @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING)
    public Instant createdAt;
}

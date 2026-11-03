package com.ludoarena.service;

import com.ludoarena.model.AdminLog;
import com.ludoarena.repository.AdminLogRepository;
import com.ludoarena.security.AuthUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Append-only audit trail of every administrative action. */
@Service
public class AdminLogService {
    private final AdminLogRepository repo;

    public AdminLogService(AdminLogRepository repo) {
        this.repo = repo;
    }

    public void log(AuthUser admin, String action, String targetType, String targetId, Map<String, Object> details) {
        AdminLog l = new AdminLog();
        l.adminId = admin.id();
        l.adminEmail = admin.email();
        l.action = action;
        l.targetType = targetType;
        l.targetId = targetId;
        l.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
        l.createdAt = Instant.now();
        repo.save(l);
    }
}

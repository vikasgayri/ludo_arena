package com.ludoarena.repository;

import com.ludoarena.model.AdminLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminLogRepository extends MongoRepository<AdminLog, String> {
}

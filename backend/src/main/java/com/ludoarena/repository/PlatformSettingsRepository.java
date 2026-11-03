package com.ludoarena.repository;

import com.ludoarena.model.PlatformSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlatformSettingsRepository extends MongoRepository<PlatformSettings, String> {
}

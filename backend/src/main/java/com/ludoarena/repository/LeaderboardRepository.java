package com.ludoarena.repository;

import com.ludoarena.model.LeaderboardEntry;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LeaderboardRepository extends MongoRepository<LeaderboardEntry, String> {
    Optional<LeaderboardEntry> findByUserId(String userId);

    long countByXpGreaterThan(long xp);
}

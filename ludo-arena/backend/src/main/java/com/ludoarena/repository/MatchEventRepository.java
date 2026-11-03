package com.ludoarena.repository;

import com.ludoarena.model.MatchEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchEventRepository extends MongoRepository<MatchEvent, String> {
    Page<MatchEvent> findByMatchId(String matchId, Pageable pageable);
}

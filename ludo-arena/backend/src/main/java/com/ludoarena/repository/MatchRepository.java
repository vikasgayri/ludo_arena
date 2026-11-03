package com.ludoarena.repository;

import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Match;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchRepository extends MongoRepository<Match, String> {
    Page<Match> findByPlayerIds(String userId, Pageable pageable);

    Optional<Match> findFirstByPlayerIdsAndStatus(String userId, MatchStatus status);

    List<Match> findByStatus(MatchStatus status);

    long countByStatus(MatchStatus status);
}

package com.ludoarena.service;

import com.ludoarena.dto.Dtos.LeaderboardEntryDto;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.model.LeaderboardEntry;
import com.ludoarena.model.User;
import com.ludoarena.repository.LeaderboardRepository;
import com.ludoarena.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {
    private final MongoTemplate mongo;
    private final LeaderboardRepository repo;
    private final UserRepository users;

    public LeaderboardService(MongoTemplate mongo, LeaderboardRepository repo, UserRepository users) {
        this.mongo = mongo;
        this.repo = repo;
        this.users = users;
    }

    /** Called once per player when a match is finalised. Atomic upsert with $inc. */
    public void record(String userId, String name, String avatar, boolean won, long xp) {
        Update u = new Update()
                .inc("xp", xp).inc("totalMatches", 1).inc("wins", won ? 1 : 0)
                .set("name", name).set("avatar", avatar).set("updatedAt", Instant.now());
        mongo.findAndModify(Query.query(Criteria.where("userId").is(userId)), u,
                FindAndModifyOptions.options().upsert(true).returnNew(true), LeaderboardEntry.class);
    }

    public PageResponse<LeaderboardEntryDto> top(int page, int size) {
        var pageable = MongoPaging.pageable(page, size, Sort.by(Sort.Direction.DESC, "xp").and(Sort.by(Sort.Direction.DESC, "wins")));
        Page<LeaderboardEntry> p = MongoPaging.page(mongo, null, pageable, LeaderboardEntry.class);
        List<LeaderboardEntryDto> items = new ArrayList<>();
        for (int i = 0; i < p.getContent().size(); i++) {
            LeaderboardEntry e = p.getContent().get(i);
            // ties share a rank: rank = number of players with strictly more XP + 1
            items.add(LeaderboardEntryDto.from(e, repo.countByXpGreaterThan(e.xp) + 1));
        }
        return PageResponse.of(items, p.getNumber(), p.getSize(), p.getTotalElements());
    }

    public LeaderboardEntryDto me(String userId) {
        LeaderboardEntry e = repo.findByUserId(userId).orElse(null);
        if (e == null) {
            User u = users.findById(userId).orElseThrow();
            e = new LeaderboardEntry();
            e.userId = userId;
            e.name = u.name;
            e.avatar = u.avatar;
        }
        long rank = e.totalMatches == 0 ? 0 : repo.countByXpGreaterThan(e.xp) + 1;
        return LeaderboardEntryDto.from(e, rank);
    }

    public void syncProfile(String userId, String name, String avatar) {
        mongo.updateFirst(Query.query(Criteria.where("userId").is(userId)),
                new Update().set("name", name).set("avatar", avatar), LeaderboardEntry.class);
    }
}

package com.ludoarena.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("leaderboards")
@CompoundIndex(name = "xp_wins", def = "{'xp': -1, 'wins': -1}")
public class LeaderboardEntry {
    @Id
    public String id;
    @Indexed(unique = true)
    public String userId;
    public String name;
    public String avatar;
    public long xp;
    public int wins;
    public int totalMatches;
    public Instant updatedAt;
}

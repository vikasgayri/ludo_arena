package com.ludoarena.model;

import com.ludoarena.game.model.GameState;
import com.ludoarena.model.Enums.MatchStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("matches")
@CompoundIndex(name = "players_createdAt", def = "{'playerIds': 1, 'createdAt': -1}")
@CompoundIndex(name = "status_deadline", def = "{'status': 1, 'game.turnDeadlineEpochMs': 1}")
public class Match {
    @Id
    public String id;
    @Indexed
    public String roomId;
    public int mode;
    public List<String> playerIds = new ArrayList<>();
    public MatchStatus status = MatchStatus.ACTIVE;
    public String winnerId;
    public GameState game;
    /** last processed client action ids (idempotency window) */
    public List<String> processedActionIds = new ArrayList<>();
    public Instant createdAt;
    public Instant startedAt;
    public Instant finishedAt;
    /** set exactly once (atomically) when stats, rewards and notifications for a finished match were applied */
    public boolean finalized;
    @Version
    public Long version;
}

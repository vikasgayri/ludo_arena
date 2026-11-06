package com.ludoarena.model;

import com.ludoarena.game.model.GameState;
import com.ludoarena.model.Enums.MatchStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("matches")
@CompoundIndex(name = "players_createdAt", def = "{'playerIds': 1, 'createdAt': -1}")
@CompoundIndex(name = "status_deadline", def = "{'status': 1, 'game.turnDeadlineEpochMs': 1}")
@CompoundIndex(name = "status_botAct", def = "{'status': 1, 'game.botActAtEpochMs': 1}")
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
    /** players whose stats / XP / notification were already applied (makes finalization safely re-runnable) */
    public List<String> finalizedPlayers = new ArrayList<>();
    /** "Play with Computer": one human against bots. playerIds holds only the human. */
    public boolean vsComputer;
    public String botDifficulty;

    /**
     * Deep copy written to MongoDB by the background writer, so the live in-memory match can keep changing while the
     * snapshot is being saved. (No @Version: every match has exactly one writer - its lock plus an ordered write queue.)
     */
    public Match copyForPersistence() {
        Match c = new Match();
        c.id = id;
        c.roomId = roomId;
        c.mode = mode;
        c.playerIds = new ArrayList<>(playerIds);
        c.status = status;
        c.winnerId = winnerId;
        c.game = game == null ? null : game.copy();
        c.processedActionIds = new ArrayList<>(processedActionIds);
        c.createdAt = createdAt;
        c.startedAt = startedAt;
        c.finishedAt = finishedAt;
        c.finalized = finalized;
        c.finalizedPlayers = new ArrayList<>(finalizedPlayers);
        c.vsComputer = vsComputer;
        c.botDifficulty = botDifficulty;
        return c;
    }
}

package com.ludoarena.service;

import com.ludoarena.dto.Dtos.EventDto;
import com.ludoarena.dto.Dtos.MatchStateDto;
import com.ludoarena.dto.Dtos.MatchSummaryDto;
import com.ludoarena.dto.Dtos.MatchUpdate;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.RoomDto;
import com.ludoarena.dto.Dtos.RoomUpdate;
import com.ludoarena.dto.Dtos.StateSnapshot;
import com.ludoarena.exception.ApiException;
import com.ludoarena.game.LudoEngineService;
import com.ludoarena.game.bot.BotBrain;
import com.ludoarena.game.bot.BotDifficulty;
import com.ludoarena.game.bot.BotProfiles;
import com.ludoarena.game.model.EngineException;
import com.ludoarena.game.model.GameEvent;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.game.model.SeatInfo;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Enums.RoomStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.Match;
import com.ludoarena.model.MatchEvent;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.model.Room;
import com.ludoarena.model.User;
import com.ludoarena.repository.MatchEventRepository;
import com.ludoarena.repository.MatchRepository;
import com.ludoarena.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Orchestrates matches: persistence, per-match serialisation, idempotency, broadcasting and match completion.
 * All rule decisions are delegated to {@link LudoEngineService}; this class never decides what a move means.
 *
 * Concurrency model: every mutation of a match happens while holding that match's in-process lock, so two
 * actions can never interleave on one server instance. (Horizontal scaling needs a distributed lock or sticky
 * routing per match - see README "Scaling notes".)
 */
@Service
public class MatchService {
    private static final Logger log = LoggerFactory.getLogger(MatchService.class);
    private static final int ACTION_ID_WINDOW = 100;
    /** "thinking" pauses so a bot turn is visible to the human: base delay + random jitter (ms) */
    static final long BOT_ROLL_DELAY_MS = 900;
    static final long BOT_MOVE_DELAY_MS = 700;
    private static final long BOT_JITTER_MS = 400;

    private final MatchRepository matches;
    private final MatchEventRepository eventRepo;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final LudoEngineService engine;
    private final PlatformSettingsService settings;
    private final WalletService wallets;
    private final NotificationService notifications;
    private final LeaderboardService leaderboard;
    private final SimpMessagingTemplate messaging;
    private final BotBrain botBrain;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public MatchService(MatchRepository matches, MatchEventRepository eventRepo, UserRepository users, MongoTemplate mongo,
                        LudoEngineService engine, PlatformSettingsService settings, WalletService wallets,
                        NotificationService notifications, LeaderboardService leaderboard, SimpMessagingTemplate messaging,
                        BotBrain botBrain) {
        this.matches = matches;
        this.eventRepo = eventRepo;
        this.users = users;
        this.mongo = mongo;
        this.engine = engine;
        this.settings = settings;
        this.wallets = wallets;
        this.notifications = notifications;
        this.leaderboard = leaderboard;
        this.messaging = messaging;
        this.botBrain = botBrain;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Creates the match for a room that has just transitioned to IN_PROGRESS. */
    public Match startMatch(Room room) {
        long now = System.currentTimeMillis();
        List<SeatInfo> seats = new ArrayList<>();
        for (Room.RoomPlayer p : room.players) seats.add(new SeatInfo(p.userId, p.name, p.avatar));
        GameState game = engine.startGame(seats, settings.gameRules(), now);
        GameEvent started = engine.startedEvent(game, now);

        Match m = new Match();
        m.roomId = room.id;
        m.mode = seats.size();
        for (SeatInfo s : seats) m.playerIds.add(s.userId());
        m.game = game;
        m.status = MatchStatus.ACTIVE;
        m.createdAt = Instant.ofEpochMilli(now);
        m.startedAt = m.createdAt;
        m = matches.save(m);
        persistEvents(m.id, List.of(started));
        return m;
    }

    /**
     * "Play with Computer": seat 0 is the human, the other seats are bots. Bots are game seats only: they are NOT added
     * to {@link Match#playerIds}, so they never get history, stats, wallet entries or leaderboard rows.
     */
    public Match startBotMatch(Room room, BotDifficulty difficulty) {
        long now = System.currentTimeMillis();
        Room.RoomPlayer human = room.players.get(0);
        List<SeatInfo> seats = new ArrayList<>();
        seats.add(new SeatInfo(human.userId, human.name, human.avatar));
        for (int seat = 1; seat < room.maxPlayers; seat++) {
            seats.add(new SeatInfo(BotProfiles.botUserId(room.id, seat), BotProfiles.name(difficulty, seat), BotProfiles.avatar(seat)));
        }
        GameState game = engine.startGame(seats, settings.gameRules(), now);
        for (PlayerState p : game.players) {
            if (BotProfiles.isBotId(p.userId)) {
                p.bot = true;
                p.botDifficulty = difficulty.name();
            }
        }
        GameEvent started = engine.startedEvent(game, now);

        Match m = new Match();
        m.roomId = room.id;
        m.mode = seats.size();
        m.playerIds.add(human.userId);
        m.vsComputer = true;
        m.botDifficulty = difficulty.name();
        m.game = game;
        m.status = MatchStatus.ACTIVE;
        m.createdAt = Instant.ofEpochMilli(now);
        m.startedAt = m.createdAt;
        scheduleBot(m, now);
        m = matches.save(m);
        persistEvents(m.id, List.of(started));
        return m;
    }

    // -------------------------------------------------------------------- actions

    public MatchStateDto roll(String matchId, String userId, String actionId) {
        return act(matchId, userId, actionId, (g, now) -> engine.rollDice(g, userId, now));
    }

    public MatchStateDto move(String matchId, String userId, int tokenIndex, String actionId) {
        return act(matchId, userId, actionId, (g, now) -> engine.moveToken(g, userId, tokenIndex, now));
    }

    private MatchStateDto act(String matchId, String userId, String actionId, BiFunction<GameState, Long, List<GameEvent>> action) {
        if (actionId == null || actionId.isBlank()) {
            throw ApiException.badRequest("MISSING_ACTION_ID", "actionId is required");
        }
        return withLock(matchId, () -> {
            Match m = load(matchId);
            requireParticipant(m, userId);
            if (m.processedActionIds.contains(actionId)) {
                return toState(m); // duplicate delivery: acknowledge with the current state, change nothing
            }
            if (m.status != MatchStatus.ACTIVE) {
                throw new EngineException("MATCH_FINISHED", "The match is already finished");
            }
            List<GameEvent> events = action.apply(m.game, System.currentTimeMillis());
            m.processedActionIds.add(actionId);
            while (m.processedActionIds.size() > ACTION_ID_WINDOW) m.processedActionIds.remove(0);
            return commit(m, events);
        });
    }

    /** Player leaves / is removed. Only applies to running matches. */
    public void forfeit(String matchId, String userId) {
        withLock(matchId, () -> {
            Match m = load(matchId);
            requireParticipant(m, userId);
            if (m.status != MatchStatus.ACTIVE) return null;
            List<GameEvent> events = engine.forfeit(m.game, userId, System.currentTimeMillis());
            if (!events.isEmpty()) commit(m, events);
            return null;
        });
    }

    /** Used when an admin suspends a user: removes them from any running match. */
    public void forfeitActiveMatchOf(String userId) {
        matches.findFirstByPlayerIdsAndStatus(userId, MatchStatus.ACTIVE).ifPresent(m -> forfeit(m.id, userId));
    }

    // ---------------------------------------------------------- reconnect / presence

    /**
     * Returns the full state for a participant and issues a fresh reconnect token. If the client presents a token
     * it must match the one last issued for this seat.
     */
    public StateSnapshot requestState(String matchId, String userId, String reconnectToken) {
        return withLock(matchId, () -> {
            Match m = load(matchId);
            requireParticipant(m, userId);
            PlayerState p = m.game.player(userId);
            if (reconnectToken != null && !reconnectToken.isBlank() && p.reconnectTokenHash != null
                    && !Hashing.safeEquals(Hashing.sha256(reconnectToken), p.reconnectTokenHash)) {
                throw ApiException.forbidden("INVALID_RECONNECT_TOKEN", "Reconnect token is not valid for this seat");
            }
            if (m.status != MatchStatus.ACTIVE) {
                return new StateSnapshot(toState(m), null);
            }
            String fresh = Hashing.randomToken(24);
            p.reconnectTokenHash = Hashing.sha256(fresh);
            List<GameEvent> events = new ArrayList<>();
            if (!p.connected && !p.forfeited) {
                events.add(engine.connectionEvent(m.game, userId, true, System.currentTimeMillis()));
            }
            if (events.isEmpty()) {
                m = matches.save(m);
                return new StateSnapshot(toState(m), fresh);
            }
            MatchStateDto state = commit(m, events);
            return new StateSnapshot(state, fresh);
        });
    }

    public void onUserDisconnected(String userId) {
        setConnection(userId, false);
    }

    public void onUserConnected(String userId) {
        setConnection(userId, true);
    }

    private void setConnection(String userId, boolean connected) {
        Match active = matches.findFirstByPlayerIdsAndStatus(userId, MatchStatus.ACTIVE).orElse(null);
        if (active == null) return;
        withLock(active.id, () -> {
            Match m = load(active.id);
            PlayerState p = m.game.player(userId);
            if (m.status != MatchStatus.ACTIVE || p == null || p.forfeited || p.connected == connected) return null;
            commit(m, List.of(engine.connectionEvent(m.game, userId, connected, System.currentTimeMillis())));
            return null;
        });
    }

    // -------------------------------------------------------------------- timeouts

    @Scheduled(fixedDelay = 1000)
    public void processTimeouts() {
        long now = System.currentTimeMillis();
        Query q = Query.query(Criteria.where("status").is(MatchStatus.ACTIVE).and("game.turnDeadlineEpochMs").lt(now));
        q.fields().include("id");
        List<Match> due = mongo.find(q, Match.class);
        for (Match d : due) {
            try {
                withLock(d.id, () -> {
                    Match m = load(d.id);
                    if (m.status != MatchStatus.ACTIVE) return null;
                    List<GameEvent> events = engine.handleTimeout(m.game, System.currentTimeMillis());
                    if (!events.isEmpty()) commit(m, events);
                    return null;
                });
            } catch (RuntimeException e) {
                log.error("Timeout processing failed for match {}", d.id, e);
            }
        }
    }

    // ------------------------------------------------------------------- bot turns

    /**
     * Plays due computer turns. One action (roll OR move) per due match per tick, always under the match lock and
     * always through the normal engine entry points, so bot turns can never overlap with each other or with a human
     * action. The due time is persisted in the game state, so pending bot turns resume after a restart.
     */
    @Scheduled(fixedDelay = 250)
    public void processBotTurns() {
        Query q = Query.query(Criteria.where("status").is(MatchStatus.ACTIVE).and("vsComputer").is(true)
                .and("game.botActAtEpochMs").lte(System.currentTimeMillis()));
        q.fields().include("id");
        for (Match d : mongo.find(q, Match.class)) {
            try {
                withLock(d.id, () -> {
                    playBotTurn(load(d.id), System.currentTimeMillis());
                    return null;
                });
            } catch (RuntimeException e) {
                log.error("Bot turn failed for match {}", d.id, e);
            }
        }
    }

    /** Caller holds the lock. Package-private for tests. */
    void playBotTurn(Match m, long now) {
        GameState g = m.game;
        if (m.status != MatchStatus.ACTIVE || !m.vsComputer || g.botActAtEpochMs == null || g.botActAtEpochMs > now) return;
        g.botActAtEpochMs = null; // commit() schedules the next bot step afresh
        PlayerState p = g.current();
        if (!p.bot || p.forfeited || g.isFinished()) {
            matches.save(m);
            return;
        }
        List<GameEvent> events = g.awaitingMove
                ? engine.moveToken(g, p.userId, botBrain.chooseMove(g, p, BotDifficulty.parse(p.botDifficulty)), now)
                : engine.rollDice(g, p.userId, now);
        commit(m, events);
    }

    /** Sets when the bot to move acts next; clears it when a human (or nobody) is to move. */
    private void scheduleBot(Match m, long now) {
        if (!m.vsComputer) return;
        GameState g = m.game;
        if (g.isFinished() || g.current().forfeited || !g.current().bot) {
            g.botActAtEpochMs = null;
            return;
        }
        if (g.botActAtEpochMs == null) {
            long base = g.awaitingMove ? BOT_MOVE_DELAY_MS : BOT_ROLL_DELAY_MS;
            g.botActAtEpochMs = now + base + ThreadLocalRandom.current().nextLong(BOT_JITTER_MS);
        }
    }

    /** A computer match without any human left in it ends without a winner (no rewards, no stats). */
    private List<GameEvent> abandonIfNoHumanLeft(Match m, List<GameEvent> events, long now) {
        if (!m.vsComputer) return events;
        GameState g = m.game;
        boolean humanLeft = g.players.stream().anyMatch(p -> !p.bot && !p.forfeited);
        if (humanLeft || m.status != MatchStatus.ACTIVE) return events;
        g.status = GameState.Status.FINISHED;
        g.awaitingMove = false;
        g.diceValue = null;
        g.validMoves = new ArrayList<>();
        g.winnerUserId = null;
        g.botActAtEpochMs = null;
        List<GameEvent> all = new ArrayList<>(events);
        all.add(engine.event(g, GameEvent.MATCH_ABANDONED, null, now));
        return all;
    }

    // ---------------------------------------------------------------------- commit

    /** Persists the mutated match + its events, broadcasts the update and runs completion handling. Caller holds the lock. */
    private MatchStateDto commit(Match m, List<GameEvent> events) {
        long now = System.currentTimeMillis();
        events = abandonIfNoHumanLeft(m, events, now);
        GameState g = m.game;
        boolean justFinished = g.isFinished() && m.status == MatchStatus.ACTIVE;
        scheduleBot(m, now);
        if (justFinished) {
            m.status = g.winnerUserId == null ? MatchStatus.ABANDONED : MatchStatus.FINISHED;
            m.winnerId = g.winnerUserId;
            m.finishedAt = Instant.now();
        }
        m = matches.save(m);
        persistEvents(m.id, events);
        countCaptures(events);

        MatchStateDto state = toState(m);
        List<EventDto> dtos = new ArrayList<>();
        for (GameEvent e : events) dtos.add(EventDto.from(e));
        messaging.convertAndSend("/topic/match/" + m.id, new MatchUpdate(m.id, dtos, state));

        if (justFinished) finalizeMatch(m);
        return state;
    }

    private void persistEvents(String matchId, List<GameEvent> events) {
        List<MatchEvent> docs = new ArrayList<>();
        for (GameEvent e : events) {
            MatchEvent d = new MatchEvent();
            d.matchId = matchId;
            d.seq = e.seq;
            d.eventId = e.eventId;
            d.type = e.type;
            d.userId = e.userId;
            d.data = e.data;
            d.at = e.at;
            docs.add(d);
        }
        eventRepo.saveAll(docs);
    }

    private void countCaptures(List<GameEvent> events) {
        Map<String, Integer> perUser = new HashMap<>();
        for (GameEvent e : events) {
            if (GameEvent.TOKEN_CAPTURED.equals(e.type) && e.userId != null && !BotProfiles.isBotId(e.userId)) {
                perUser.merge(e.userId, 1, Integer::sum);
            }
        }
        perUser.forEach((uid, n) -> mongo.updateFirst(Query.query(Criteria.where("id").is(uid)),
                new Update().inc("stats.captures", n), User.class));
    }

    /**
     * Stats, XP, leaderboard, coin rewards, notifications, room release. Safe to run repeatedly (crash recovery):
     *  - coin rewards go through the idempotent ledger (reference MATCH:{match}:{user}), so they are applied exactly once
     *    and are retried until they succeed;
     *  - stats / XP / notification are guarded by a per-player marker claimed atomically, so they are applied at most once;
     *  - the match is flagged {@code finalized} only after every player was processed; until then the recovery sweep retries.
     * Caller holds the match lock.
     */
    void finalizeMatch(Match m) {
        releaseRoom(m);
        Match fresh = mongo.findById(m.id, Match.class);
        java.util.Set<String> done = new java.util.HashSet<>(fresh != null && fresh.finalizedPlayers != null ? fresh.finalizedPlayers : List.of());
        PlatformSettings s = settings.get();
        boolean allOk = true;
        for (PlayerState p : m.game.players) {
            if (p.bot || done.contains(p.userId)) continue;
            try {
                settlePlayer(m, p, s);
            } catch (RuntimeException e) {
                allOk = false;
                log.error("Finalization failed for player {} in match {} (will be retried)", p.userId, m.id, e);
            }
        }
        if (!allOk) return;
        var claim = mongo.updateFirst(Query.query(Criteria.where("id").is(m.id).and("finalized").ne(true)),
                new Update().set("finalized", true), Match.class);
        if (claim.getModifiedCount() == 0) return;
        Room room = mongo.findById(m.roomId, Room.class);
        if (room != null) {
            messaging.convertAndSend("/topic/room/" + m.roomId, new RoomUpdate("ROOM_FINISHED", RoomDto.from(room, null), m.id));
        }
    }

    private void settlePlayer(Match m, PlayerState p, PlatformSettings s) {
        boolean won = p.userId.equals(m.winnerId);
        if (m.vsComputer) {
            settlePracticePlayer(m, p, won);
            return;
        }
        long reward = won ? s.matchWinRewardCoins : (p.forfeited ? 0 : s.matchParticipationRewardCoins);
        if (reward > 0) {
            wallets.credit(p.userId, TxType.MATCH_REWARD, reward, "MATCH:" + m.id + ":" + p.userId,
                    won ? "Match victory reward" : "Match participation reward");
        }
        var claim = mongo.updateFirst(Query.query(Criteria.where("id").is(m.id).and("finalizedPlayers").ne(p.userId)),
                new Update().addToSet("finalizedPlayers", p.userId), Match.class);
        if (claim.getModifiedCount() == 0) return; // stats already applied by an earlier attempt
        long xp = (won ? 100 : p.forfeited ? 5 : 25) + 5L * p.finishedTokens();
        mongo.updateFirst(Query.query(Criteria.where("id").is(p.userId)),
                new Update().inc("stats.totalMatches", 1).inc(won ? "stats.wins" : "stats.losses", 1).inc("stats.xp", xp),
                User.class);
        leaderboard.record(p.userId, p.name, p.avatar, won, xp);
        notifications.notify(p.userId, "MATCH_FINISHED", won ? "You won!" : "Match finished",
                (won ? "Victory! +" : "Good game. +") + xp + " XP" + (reward > 0 ? " and " + reward + " coins." : "."));
    }

    /** Matches against the computer: no coins, no XP, no leaderboard (prevents farming); separate practice counters. */
    private void settlePracticePlayer(Match m, PlayerState p, boolean won) {
        var claim = mongo.updateFirst(Query.query(Criteria.where("id").is(m.id).and("finalizedPlayers").ne(p.userId)),
                new Update().addToSet("finalizedPlayers", p.userId), Match.class);
        if (claim.getModifiedCount() == 0 || m.status == MatchStatus.ABANDONED) return;
        Update stats = new Update().inc("stats.botGames", 1);
        if (won) stats.inc("stats.botWins", 1);
        mongo.updateFirst(Query.query(Criteria.where("id").is(p.userId)), stats, User.class);
        notifications.notify(p.userId, "MATCH_FINISHED", won ? "You beat the computer!" : "Practice match finished",
                "Practice matches against the computer do not award coins or leaderboard XP.");
    }

    private void releaseRoom(Match m) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(m.roomId)),
                new Update().set("status", RoomStatus.FINISHED).set("updatedAt", Instant.now()), Room.class);
        mongo.updateMulti(Query.query(Criteria.where("activeRoomId").is(m.roomId)),
                new Update().unset("activeRoomId"), User.class);
    }

    /** Crash recovery: finished matches whose finalization did not complete are retried every minute. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void recoverUnfinalizedMatches() {
        Query q = Query.query(Criteria.where("status").in(MatchStatus.FINISHED, MatchStatus.ABANDONED).and("finalized").ne(true)
                .and("finishedAt").lt(Instant.now().minusSeconds(30)));
        q.fields().include("id");
        for (Match d : mongo.find(q, Match.class)) {
            try {
                withLock(d.id, () -> {
                    Match m = load(d.id);
                    if (m.status != MatchStatus.ACTIVE && !m.finalized) finalizeMatch(m);
                    return null;
                });
            } catch (RuntimeException e) {
                log.error("Recovery of match {} failed", d.id, e);
            }
        }
    }

    // ----------------------------------------------------------------------- reads

    public MatchStateDto stateFor(String matchId, String viewerId, boolean admin) {
        Match m = load(matchId);
        if (!admin) requireParticipant(m, viewerId);
        return toState(m);
    }

    public boolean isParticipant(String matchId, String userId) {
        return mongo.exists(Query.query(Criteria.where("id").is(matchId).and("playerIds").is(userId)), Match.class);
    }

    public PageResponse<MatchSummaryDto> listForUser(String userId, MatchStatus status, int page, int size) {
        Criteria c = Criteria.where("playerIds").is(userId);
        if (status != null) c = c.and("status").is(status);
        Page<Match> p = MongoPaging.page(mongo, c, MongoPaging.newestFirst(page, size), Match.class);
        return PageResponse.of(p, m -> MatchSummaryDto.from(m, userId));
    }

    public PageResponse<MatchSummaryDto> listAll(MatchStatus status, int page, int size) {
        Criteria c = status == null ? null : Criteria.where("status").is(status);
        Page<Match> p = MongoPaging.page(mongo, c, MongoPaging.newestFirst(page, size), Match.class);
        return PageResponse.of(p, m -> MatchSummaryDto.from(m, null));
    }

    public PageResponse<EventDto> events(String matchId, String viewerId, boolean admin, int page, int size) {
        Match m = load(matchId);
        if (!admin) requireParticipant(m, viewerId);
        Page<MatchEvent> p = eventRepo.findByMatchId(matchId, MongoPaging.pageable(page, size, Sort.by(Sort.Direction.ASC, "seq")));
        return PageResponse.of(p, EventDto::from);
    }

    public String activeMatchId(String userId) {
        return matches.findFirstByPlayerIdsAndStatus(userId, MatchStatus.ACTIVE).map(m -> m.id).orElse(null);
    }

    // --------------------------------------------------------------------- helpers

    private Match load(String matchId) {
        return matches.findById(matchId).orElseThrow(() -> ApiException.notFound("MATCH_NOT_FOUND", "Match not found"));
    }

    private void requireParticipant(Match m, String userId) {
        if (!m.playerIds.contains(userId)) {
            throw ApiException.forbidden("NOT_A_PLAYER", "You are not part of this match");
        }
    }

    private MatchStateDto toState(Match m) {
        Map<String, User.Cosmetics> cosmetics = new HashMap<>();
        for (User u : users.findAllById(m.playerIds)) cosmetics.put(u.id, u.cosmetics);
        return MatchStateDto.from(m, cosmetics);
    }

    private <T> T withLock(String matchId, Supplier<T> body) {
        ReentrantLock lock = locks.computeIfAbsent(matchId, k -> new ReentrantLock());
        lock.lock();
        try {
            return body.get();
        } finally {
            lock.unlock();
        }
    }
}

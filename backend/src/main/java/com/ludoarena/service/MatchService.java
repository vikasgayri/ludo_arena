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
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public MatchService(MatchRepository matches, MatchEventRepository eventRepo, UserRepository users, MongoTemplate mongo,
                        LudoEngineService engine, PlatformSettingsService settings, WalletService wallets,
                        NotificationService notifications, LeaderboardService leaderboard, SimpMessagingTemplate messaging) {
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

    // ---------------------------------------------------------------------- commit

    /** Persists the mutated match + its events, broadcasts the update and runs completion handling. Caller holds the lock. */
    private MatchStateDto commit(Match m, List<GameEvent> events) {
        GameState g = m.game;
        boolean justFinished = g.isFinished() && m.status == MatchStatus.ACTIVE;
        if (justFinished) {
            m.status = MatchStatus.FINISHED;
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
            if (GameEvent.TOKEN_CAPTURED.equals(e.type) && e.userId != null) perUser.merge(e.userId, 1, Integer::sum);
        }
        perUser.forEach((uid, n) -> mongo.updateFirst(Query.query(Criteria.where("id").is(uid)),
                new Update().inc("stats.captures", n), User.class));
    }

    /** Stats, XP, leaderboard, coin rewards, notifications, room release. Runs exactly once per match. */
    private void finalizeMatch(Match m) {
        var claim = mongo.updateFirst(Query.query(Criteria.where("id").is(m.id).and("finalized").ne(true)),
                new Update().set("finalized", true), Match.class);
        if (claim.getModifiedCount() == 0) return;

        PlatformSettings s = settings.get();
        for (PlayerState p : m.game.players) {
            boolean won = p.userId.equals(m.winnerId);
            long xp = (won ? 100 : p.forfeited ? 5 : 25) + 5L * p.finishedTokens();
            try {
                mongo.updateFirst(Query.query(Criteria.where("id").is(p.userId)),
                        new Update().inc("stats.totalMatches", 1).inc(won ? "stats.wins" : "stats.losses", 1).inc("stats.xp", xp),
                        User.class);
                leaderboard.record(p.userId, p.name, p.avatar, won, xp);
                long reward = won ? s.matchWinRewardCoins : (p.forfeited ? 0 : s.matchParticipationRewardCoins);
                if (reward > 0) {
                    wallets.credit(p.userId, TxType.MATCH_REWARD, reward, "MATCH:" + m.id + ":" + p.userId,
                            won ? "Match victory reward" : "Match participation reward");
                }
                notifications.notify(p.userId, "MATCH_FINISHED", won ? "You won!" : "Match finished",
                        (won ? "Victory! +" : "Good game. +") + xp + " XP" + (reward > 0 ? " and " + reward + " coins." : "."));
            } catch (RuntimeException e) {
                log.error("Finalization failed for player {} in match {}", p.userId, m.id, e);
            }
        }
        mongo.updateFirst(Query.query(Criteria.where("id").is(m.roomId)),
                new Update().set("status", RoomStatus.FINISHED).set("updatedAt", Instant.now()), Room.class);
        mongo.updateMulti(Query.query(Criteria.where("activeRoomId").is(m.roomId)),
                new Update().unset("activeRoomId"), User.class);
        Room room = mongo.findById(m.roomId, Room.class);
        if (room != null) {
            messaging.convertAndSend("/topic/room/" + m.roomId, new RoomUpdate("ROOM_FINISHED", RoomDto.from(room, null), m.id));
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

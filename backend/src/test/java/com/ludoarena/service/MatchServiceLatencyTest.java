package com.ludoarena.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ludoarena.game.DiceService;
import com.ludoarena.game.LudoEngineService;
import com.ludoarena.game.LudoRulesService;
import com.ludoarena.game.TokenMovementService;
import com.ludoarena.game.TurnManagerService;
import com.ludoarena.game.bot.BotBrain;
import com.ludoarena.game.bot.BotDifficulty;
import com.ludoarena.model.Match;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.model.Room;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.repository.MatchEventRepository;
import com.ludoarena.repository.MatchRepository;
import com.ludoarena.repository.UserRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Hot-path latency guarantees: a player's action is broadcast without waiting for MongoDB, the database copy is
 * written behind it strictly in order, and a running match is served from memory (no read per action).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceLatencyTest {
    @Mock MatchRepository matches;
    @Mock MatchEventRepository eventRepo;
    @Mock UserRepository users;
    @Mock MongoTemplate mongo;
    @Mock PlatformSettingsService settings;
    @Mock WalletService wallets;
    @Mock NotificationService notifications;
    @Mock LeaderboardService leaderboard;
    @Mock SimpMessagingTemplate messaging;

    private MatchService service;
    /** writes queued by the service; the test decides when "the database" runs them */
    private final Deque<Runnable> pendingWrites = new ArrayDeque<>();
    private final List<Match> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        LudoRulesService rules = new LudoRulesService();
        LudoEngineService engine = new LudoEngineService(new DiceService(), rules, new TokenMovementService(rules), new TurnManagerService());
        service = new MatchService(matches, eventRepo, users, mongo, engine, settings, wallets, notifications, leaderboard,
                messaging, new BotBrain(rules));
        service.useWriterExecutor(pendingWrites::add);
        when(settings.gameRules()).thenReturn(new GameRules());
        when(settings.get()).thenReturn(new PlatformSettings());
        // atomic Mongo updates (finalization claims, room release) report one matched document
        when(mongo.updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.UpdateDefinition.class), any(Class.class)))
                .thenReturn(com.mongodb.client.result.UpdateResult.acknowledged(1L, 1L, null));
        when(matches.save(any(Match.class))).thenAnswer(i -> {
            Match m = i.getArgument(0);
            if (m.id == null) m.id = "m1";
            saved.add(m);
            return m;
        });
    }

    private Match humanToMove() {
        Room r = new Room();
        r.id = "r1";
        r.maxPlayers = 2;
        r.vsComputer = true;
        Room.RoomPlayer human = new Room.RoomPlayer();
        human.userId = "human";
        human.name = "Human";
        human.avatar = "avatar-01";
        human.ready = true;
        r.players.add(human);
        Match m = service.startBotMatch(r, BotDifficulty.EASY);
        m.game.turnIndex = 0; // the human
        m.game.botActAtEpochMs = null;
        saved.clear();
        return m;
    }

    private void drainWrites() {
        while (!pendingWrites.isEmpty()) pendingWrites.poll().run();
    }

    @Test
    void anActionIsBroadcastBeforeAnythingIsWrittenToTheDatabase() {
        Match m = humanToMove();

        service.roll(m.id, "human", "a1");

        verify(messaging).convertAndSend(eq("/topic/match/m1"), any(Object.class));
        assertTrue(saved.isEmpty(), "no database write may happen before the players are told");
        assertEquals(1, pendingWrites.size());

        drainWrites();
        assertEquals(1, saved.size());
        assertNotSame(m, saved.get(0), "a snapshot is written, never the live object");
        assertEquals(m.game.lastDiceValue, saved.get(0).game.lastDiceValue);
    }

    @Test
    void writesOfOneMatchHappenInTheOrderTheStateChanged() {
        Match m = humanToMove();
        List<Long> versions = new ArrayList<>();
        int actions = 0;
        for (int i = 0; i < 12; i++) {
            if (m.game.current().bot) {
                m.game.botActAtEpochMs = 0L;
                service.playBotTurn(m, System.currentTimeMillis());
            } else if (!m.game.awaitingMove) {
                service.roll(m.id, "human", "r" + i);
            } else {
                service.move(m.id, "human", m.game.validMoves.get(0), "m" + i);
            }
            actions++;
            versions.add(m.game.seq);
        }
        assertTrue(saved.isEmpty());

        drainWrites();

        assertEquals(actions, saved.size());
        for (int i = 0; i < actions; i++) {
            assertEquals(versions.get(i), saved.get(i).game.seq, "write #" + i + " is out of order");
        }
    }

    @Test
    void aRunningMatchIsServedFromMemoryWithoutDatabaseReads() {
        Match m = humanToMove();

        service.roll(m.id, "human", "a1");
        if (m.game.awaitingMove && !m.game.current().bot) service.move(m.id, "human", m.game.validMoves.get(0), "a2");
        service.stateFor(m.id, "human", false);
        drainWrites();

        verify(matches, never()).findById(anyString());
        verify(users, times(1)).findAllById(any()); // cosmetics are read once per match, not per update
    }

    @Test
    void theLiveMatchKeepsChangingWhileAQueuedSnapshotStaysAsItWas() {
        Match m = humanToMove();
        service.roll(m.id, "human", "a1");
        long seqAtRoll = m.game.seq;

        m.game.seq += 100; // later mutation of the in-memory match
        m.game.players.get(0).tokens[0] = 5;
        drainWrites();

        assertEquals(seqAtRoll, saved.get(0).game.seq);
        assertEquals(-1, saved.get(0).game.players.get(0).tokens[0]);
    }

    @Test
    void finalizationRunsOnlyAfterTheFinalSnapshotIsStored() {
        Match m = humanToMove();
        InOrder order = Mockito.inOrder(messaging, matches, mongo);

        service.forfeit(m.id, "human"); // only human leaves -> the computer match is abandoned and finalized

        order.verify(messaging).convertAndSend(eq("/topic/match/m1"), any(Object.class));
        verify(mongo, never()).findById(anyString(), eq(Match.class)); // finalize has not run yet
        drainWrites();
        order.verify(matches).save(any(Match.class));
        order.verify(mongo).findById("m1", Match.class); // finalizeMatch reads the stored copy
    }
}

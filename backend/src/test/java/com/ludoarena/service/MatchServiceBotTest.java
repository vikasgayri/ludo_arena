package com.ludoarena.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ludoarena.game.DiceService;
import com.ludoarena.game.LudoEngineService;
import com.ludoarena.game.LudoRulesService;
import com.ludoarena.game.TokenMovementService;
import com.ludoarena.game.TurnManagerService;
import com.ludoarena.game.bot.BotBrain;
import com.ludoarena.game.bot.BotDifficulty;
import com.ludoarena.game.bot.BotProfiles;
import com.ludoarena.game.model.GameEvent;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Match;
import com.ludoarena.model.MatchEvent;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.model.Room;
import com.ludoarena.model.User;
import com.ludoarena.repository.MatchEventRepository;
import com.ludoarena.repository.MatchRepository;
import com.ludoarena.repository.UserRepository;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** "Play with Computer" wiring in MatchService, using the real engine and bot brain (persistence is mocked). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceBotTest {
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
    private final List<Match> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        LudoRulesService rules = new LudoRulesService();
        LudoEngineService engine = new LudoEngineService(new DiceService(), rules, new TokenMovementService(rules), new TurnManagerService());
        service = new MatchService(matches, eventRepo, users, mongo, engine, settings, wallets, notifications, leaderboard,
                messaging, new BotBrain(rules));
        service.useWriterExecutor(Runnable::run); // database writes run inline in these tests
        when(settings.gameRules()).thenReturn(new GameRules());
        when(settings.get()).thenReturn(new PlatformSettings());
        when(matches.save(any(Match.class))).thenAnswer(i -> {
            Match m = i.getArgument(0);
            if (m.id == null) m.id = "m1";
            saved.add(m);
            return m;
        });
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Match.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
    }

    private static Room room(int seats) {
        Room r = new Room();
        r.id = "r1";
        r.maxPlayers = seats;
        r.vsComputer = true;
        Room.RoomPlayer human = new Room.RoomPlayer();
        human.userId = "human";
        human.name = "Human";
        human.avatar = "avatar-01";
        human.ready = true;
        r.players.add(human);
        return r;
    }

    private Match start(int seats, BotDifficulty d) {
        Match m = service.startBotMatch(room(seats), d);
        when(matches.findById(m.id)).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void botsAreSeatsButNeverPlayersOfRecord() {
        Match m = start(4, BotDifficulty.HARD);

        assertEquals(List.of("human"), m.playerIds);
        assertTrue(m.vsComputer);
        assertEquals(4, m.game.players.size());
        long bots = m.game.players.stream().filter(p -> p.bot).count();
        assertEquals(3, bots);
        for (PlayerState p : m.game.players) {
            if (p.bot) {
                assertTrue(BotProfiles.isBotId(p.userId));
                assertEquals("HARD", p.botDifficulty);
            } else {
                assertEquals("human", p.userId);
            }
        }
    }

    @Test
    void aBotTurnIsScheduledOnlyWhenABotIsToMove() {
        for (int i = 0; i < 20; i++) { // first player is random: check both outcomes
            Match m = start(2, BotDifficulty.EASY);
            if (m.game.current().bot) assertNotNull(m.game.botActAtEpochMs);
            else assertNull(m.game.botActAtEpochMs);
        }
    }

    @Test
    void aDueBotTurnRollsThroughTheEngineAndIsBroadcast() {
        Match m = start(2, BotDifficulty.MEDIUM);
        m.game.turnIndex = 1; // the bot
        m.game.botActAtEpochMs = 0L;
        String bot = m.game.current().userId;

        service.playBotTurn(m, System.currentTimeMillis());

        assertEquals(bot, m.game.lastDiceUserId); // the bot rolled (dice from the server's DiceService)
        verify(messaging, atLeastOnce()).convertAndSend(eq("/topic/match/m1"), any(Object.class));
        if (m.game.current().bot && !m.game.isFinished()) assertNotNull(m.game.botActAtEpochMs, "next bot step must be scheduled");
    }

    @Test
    void nothingHappensWhenTheBotTurnIsNotDueYet() {
        Match m = start(2, BotDifficulty.MEDIUM);
        m.game.turnIndex = 1;
        m.game.botActAtEpochMs = System.currentTimeMillis() + 60_000;
        int before = saved.size();

        service.playBotTurn(m, System.currentTimeMillis());

        assertNull(m.game.lastDiceUserId);
        assertEquals(before, saved.size());
    }

    @Test
    void aBotNeverActsOnAHumanTurn() {
        Match m = start(2, BotDifficulty.HARD);
        m.game.turnIndex = 0; // human
        m.game.botActAtEpochMs = 0L; // stale value

        service.playBotTurn(m, System.currentTimeMillis());

        assertNull(m.game.lastDiceUserId);
        assertNull(m.game.botActAtEpochMs);
    }

    @Test
    void botsPlayAWholeGameWhileTheHumanMovesThroughTheNormalApi() {
        Match m = start(4, BotDifficulty.HARD);
        GameState g = m.game;
        int guard = 0;
        while (m.status == MatchStatus.ACTIVE && guard++ < 20_000) {
            PlayerState p = g.current();
            if (p.bot) {
                g.botActAtEpochMs = 0L; // skip the "thinking" pause in the test
                service.playBotTurn(m, System.currentTimeMillis());
            } else if (!g.awaitingMove) {
                service.roll(m.id, "human", "a" + guard);
            } else {
                service.move(m.id, "human", g.validMoves.get(0), "a" + guard);
            }
        }
        assertEquals(MatchStatus.FINISHED, m.status, "game should finish");
        assertNotNull(m.winnerId);
        verify(wallets, never()).credit(anyString(), any(), anyLong(), anyString(), anyString());
        verify(leaderboard, never()).record(anyString(), anyString(), anyString(), anyBoolean(), anyLong());
    }

    @Test
    void humanLeavingAComputerMatchAbandonsItWithoutWinnerOrRewards() {
        Match m = start(4, BotDifficulty.EASY);

        service.forfeit(m.id, "human");

        assertEquals(MatchStatus.ABANDONED, m.status);
        assertNull(m.winnerId);
        assertTrue(m.game.isFinished());
        assertNull(m.game.botActAtEpochMs);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<MatchEvent>> events = ArgumentCaptor.forClass(Iterable.class);
        verify(eventRepo, atLeastOnce()).saveAll(events.capture());
        boolean abandoned = false;
        for (Iterable<MatchEvent> batch : events.getAllValues()) {
            for (MatchEvent e : batch) abandoned |= GameEvent.MATCH_ABANDONED.equals(e.type);
        }
        assertTrue(abandoned, "a MATCH_ABANDONED event must be persisted");
        verify(wallets, never()).credit(anyString(), any(), anyLong(), anyString(), anyString());
        verify(mongo, never()).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(User.class));
    }

    @Test
    void winningAgainstTheComputerOnlyCountsPracticeStats() {
        Match m = start(2, BotDifficulty.EASY);
        m.status = MatchStatus.FINISHED;
        m.winnerId = "human";
        m.game.winnerUserId = "human";

        service.finalizeMatch(m);

        ArgumentCaptor<UpdateDefinition> userUpdates = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo, atLeastOnce()).updateFirst(any(Query.class), userUpdates.capture(), eq(User.class));
        String all = userUpdates.getAllValues().toString();
        assertTrue(all.contains("stats.botGames") && all.contains("stats.botWins"), all);
        assertFalse(all.contains("stats.xp"), "no XP for practice matches");
        verify(wallets, never()).credit(anyString(), any(), anyLong(), anyString(), anyString());
        verify(leaderboard, never()).record(anyString(), anyString(), anyString(), anyBoolean(), anyLong());
        verify(notifications).notify(eq("human"), eq("MATCH_FINISHED"), anyString(), anyString());
    }
}

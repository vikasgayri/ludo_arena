package com.ludoarena.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ludoarena.game.LudoEngineService;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.Match;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.repository.MatchEventRepository;
import com.ludoarena.repository.MatchRepository;
import com.ludoarena.repository.UserRepository;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
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

/** Crash-safety of match finalization: rewards exactly once, stats at most once, retry until every player is done. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceFinalizeTest {
    @Mock MatchRepository matches;
    @Mock MatchEventRepository eventRepo;
    @Mock UserRepository users;
    @Mock MongoTemplate mongo;
    @Mock LudoEngineService engine;
    @Mock PlatformSettingsService settings;
    @Mock WalletService wallets;
    @Mock NotificationService notifications;
    @Mock LeaderboardService leaderboard;
    @Mock SimpMessagingTemplate messaging;
    @Mock com.ludoarena.game.bot.BotBrain botBrain;

    private MatchService service;
    private Match match;

    private static UpdateResult modified(long n) {
        return UpdateResult.acknowledged(1L, n, null);
    }

    private static PlayerState player(String id) {
        PlayerState p = new PlayerState();
        p.userId = id;
        p.name = id;
        p.avatar = "avatar-01";
        return p;
    }

    @BeforeEach
    void setUp() {
        service = new MatchService(matches, eventRepo, users, mongo, engine, settings, wallets, notifications, leaderboard, messaging, botBrain);
        when(settings.get()).thenReturn(new PlatformSettings());
        match = new Match();
        match.id = "m1";
        match.roomId = "room1";
        match.status = MatchStatus.FINISHED;
        match.winnerId = "p1";
        match.game = new GameState();
        match.game.players = new ArrayList<>(List.of(player("p1"), player("p2")));
        Match fresh = new Match();
        fresh.id = "m1";
        when(mongo.findById("m1", Match.class)).thenReturn(fresh);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Match.class))).thenReturn(modified(1));
    }

    @Test
    void everyPlayerGetsRewardStatsAndNotificationOnce() {
        service.finalizeMatch(match);

        verify(wallets).credit(eq("p1"), eq(TxType.MATCH_REWARD), eq(50L), eq("MATCH:m1:p1"), anyString());
        verify(wallets).credit(eq("p2"), eq(TxType.MATCH_REWARD), eq(10L), eq("MATCH:m1:p2"), anyString());
        verify(leaderboard, times(2)).record(anyString(), anyString(), anyString(), anyBoolean(), anyLong());
        verify(notifications, times(2)).notify(anyString(), eq("MATCH_FINISHED"), anyString(), anyString());
    }

    @Test
    void playersAlreadyFinalizedAreSkipped() {
        Match fresh = new Match();
        fresh.id = "m1";
        fresh.finalizedPlayers.add("p1");
        when(mongo.findById("m1", Match.class)).thenReturn(fresh);

        service.finalizeMatch(match);

        verify(wallets, never()).credit(eq("p1"), any(), anyLong(), anyString(), anyString());
        verify(wallets).credit(eq("p2"), any(), anyLong(), anyString(), anyString());
        verify(notifications, times(1)).notify(eq("p2"), anyString(), anyString(), anyString());
    }

    @Test
    void whenTheStatsClaimWasAlreadyTakenStatsAndNotificationAreNotRepeated() {
        // the per-player claim matches nothing (an earlier attempt applied the stats but crashed before finishing)
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Match.class))).thenReturn(modified(0));

        service.finalizeMatch(match);

        verify(wallets, times(2)).credit(anyString(), any(), anyLong(), anyString(), anyString()); // ledger is idempotent
        verify(leaderboard, never()).record(anyString(), anyString(), anyString(), anyBoolean(), anyLong());
        verify(notifications, never()).notify(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aFailingPlayerDoesNotBlockOthersAndKeepsTheMatchUnfinalizedForRetry() {
        doThrow(new RuntimeException("db down")).when(wallets).credit(eq("p1"), any(), anyLong(), anyString(), anyString());

        service.finalizeMatch(match);

        verify(wallets).credit(eq("p2"), any(), anyLong(), anyString(), anyString());
        ArgumentCaptor<UpdateDefinition> updates = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo, org.mockito.Mockito.atLeast(0)).updateFirst(any(Query.class), updates.capture(), eq(Match.class));
        assertFalse(updates.getAllValues().stream().anyMatch(u -> u.getUpdateObject().toString().contains("finalized=true")),
                "match must not be flagged finalized while a player is still unsettled");
    }

    @Test
    void theMatchIsFlaggedFinalizedOnceEveryoneIsSettled() {
        service.finalizeMatch(match);

        ArgumentCaptor<UpdateDefinition> updates = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo, org.mockito.Mockito.atLeastOnce()).updateFirst(any(Query.class), updates.capture(), eq(Match.class));
        assertTrue(updates.getAllValues().stream().anyMatch(u -> u.getUpdateObject().toString().contains("finalized=true")));
    }
}

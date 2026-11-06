package com.ludoarena.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludoarena.game.GameFixtures.ScriptedDice;
import com.ludoarena.game.model.Board;
import com.ludoarena.game.model.Color;
import com.ludoarena.game.model.EngineException;
import com.ludoarena.game.model.GameEvent;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.game.model.SeatInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LudoEngineServiceTest {
    private ScriptedDice dice;
    private LudoEngineService engine;
    private GameState g;
    private PlayerState u1;
    private PlayerState u2;

    @BeforeEach
    void setUp() {
        dice = new ScriptedDice();
        engine = GameFixtures.engine(dice);
        g = GameFixtures.twoPlayers(engine, new GameRules());
        u1 = g.player("u1");
        u2 = g.player("u2");
    }

    private static boolean has(List<GameEvent> events, String type) {
        for (GameEvent e : events) {
            if (e.type.equals(type)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ setup

    @Test
    void twoPlayerGameUsesOppositeColours() {
        assertEquals(Color.RED, u1.color);
        assertEquals(Color.YELLOW, u2.color);
        assertEquals("u1", g.current().userId);
        assertNull(g.diceValue);
        assertTrue(g.turnDeadlineEpochMs > 0);
    }

    @Test
    void fourPlayerGameUsesAllColours() {
        GameState four = GameFixtures.fourPlayers(engine);
        assertEquals(Color.RED, four.players.get(0).color);
        assertEquals(Color.GREEN, four.players.get(1).color);
        assertEquals(Color.YELLOW, four.players.get(2).color);
        assertEquals(Color.BLUE, four.players.get(3).color);
    }

    @Test
    void twoToFourPlayersAreAllowed() {
        assertThrows(EngineException.class, () -> engine.startGame(List.of(new SeatInfo("a", "A", null)), new GameRules(), 0L));
        assertThrows(EngineException.class, () -> engine.startGame(List.of(
                new SeatInfo("a", "A", null), new SeatInfo("b", "B", null), new SeatInfo("c", "C", null),
                new SeatInfo("d", "D", null), new SeatInfo("e", "E", null)), new GameRules(), 0L));
    }

    @Test
    void threePlayersTakeRedGreenYellowAndTurnsRotateOverAllThree() {
        GameState three = engine.startGame(List.of(
                new SeatInfo("a", "A", null), new SeatInfo("b", "B", null), new SeatInfo("c", "C", null)), new GameRules(), 0L);
        assertEquals(List.of(Color.RED, Color.GREEN, Color.YELLOW), three.players.stream().map(p -> p.color).toList());
        // everyone is still in base, so a 1 has no legal move and passes the turn: a -> b -> c -> a
        dice.with(1, 1, 1);
        assertEquals("a", three.current().userId);
        engine.rollDice(three, "a", 1L);
        assertEquals("b", three.current().userId);
        engine.rollDice(three, "b", 2L);
        assertEquals("c", three.current().userId);
        engine.rollDice(three, "c", 3L);
        assertEquals("a", three.current().userId);
    }

    @Test
    void everyPlayerStartsWithFourTokensInBase() {
        for (PlayerState p : g.players) {
            assertEquals(4, p.tokens.length);
            for (int t : p.tokens) assertEquals(Board.BASE, t);
        }
    }

    // ------------------------------------------------------- rolling / leaving

    @Test
    void nonSixWithAllTokensInBasePassesTheTurn() {
        dice.with(3);
        List<GameEvent> ev = engine.rollDice(g, "u1", 1000L);
        assertTrue(has(ev, GameEvent.NO_MOVES));
        assertEquals("u2", g.current().userId);
        assertFalse(g.awaitingMove);
        assertEquals(3, g.lastDiceValue.intValue());
    }

    @Test
    void sixLetsTokenLeaveBaseAndGrantsExtraTurn() {
        dice.with(6);
        engine.rollDice(g, "u1", 1000L);
        assertTrue(g.awaitingMove);
        assertEquals(List.of(0, 1, 2, 3), g.validMoves);
        List<GameEvent> ev = engine.moveToken(g, "u1", 0, 1100L);
        assertEquals(0, u1.tokens[0]);
        assertTrue(has(ev, GameEvent.TOKEN_MOVED));
        assertEquals("u1", g.current().userId, "six grants another roll");
        assertNull(g.diceValue);
        assertFalse(g.awaitingMove);
    }

    @Test
    void tokenAdvancesByTheRolledValue() {
        u1.tokens[0] = 10;
        dice.with(4);
        engine.rollDice(g, "u1", 0L);
        assertEquals(List.of(0), g.validMoves);
        engine.moveToken(g, "u1", 0, 0L);
        assertEquals(14, u1.tokens[0]);
        assertEquals("u2", g.current().userId, "no extra turn for a plain move");
    }

    @Test
    void extraTurnOnSixCanBeDisabled() {
        GameRules r = new GameRules();
        r.extraTurnOnSix = false;
        g = GameFixtures.twoPlayers(engine, r);
        dice.with(6);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L);
        assertEquals("u2", g.current().userId);
    }

    @Test
    void thirdConsecutiveSixForfeitsTheTurn() {
        dice.with(6, 6, 6);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L);
        assertEquals(6, u1.tokens[0]);
        List<GameEvent> ev = engine.rollDice(g, "u1", 0L);
        assertTrue(has(ev, GameEvent.TRIPLE_SIX));
        assertEquals("u2", g.current().userId);
        assertEquals(6, u1.tokens[0], "no move is made on the penalty roll");
        assertEquals(0, g.consecutiveSixes);
    }

    @Test
    void sixesStreakIsBrokenByANonSix() {
        u1.tokens[0] = 10;
        dice.with(6, 2, 6);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L); // 16
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L); // 18, turn passes
        assertEquals("u2", g.current().userId);
        assertEquals(0, g.consecutiveSixes);
    }

    // ----------------------------------------------------------------- capture

    @Test
    void landingOnAnOpponentOnANormalCellCapturesItAndGrantsExtraTurn() {
        u1.tokens[0] = 3;
        u2.tokens[0] = 31; // global 5
        dice.with(2);
        engine.rollDice(g, "u1", 0L);
        List<GameEvent> ev = engine.moveToken(g, "u1", 0, 0L);
        assertEquals(5, u1.tokens[0]);
        assertEquals(Board.BASE, u2.tokens[0]);
        assertTrue(has(ev, GameEvent.TOKEN_CAPTURED));
        assertEquals("u1", g.current().userId);
    }

    @Test
    void captureDoesNotGiveExtraTurnWhenDisabled() {
        GameRules r = new GameRules();
        r.extraTurnOnCapture = false;
        g = GameFixtures.twoPlayers(engine, r);
        g.player("u1").tokens[0] = 3;
        g.player("u2").tokens[0] = 31;
        dice.with(2);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L);
        assertEquals("u2", g.current().userId);
    }

    @Test
    void starCellProtectsTheOpponent() {
        u1.tokens[0] = 6;
        u2.tokens[0] = 34; // global 8 = star
        dice.with(2);
        engine.rollDice(g, "u1", 0L);
        List<GameEvent> ev = engine.moveToken(g, "u1", 0, 0L);
        assertEquals(8, u1.tokens[0]);
        assertEquals(34, u2.tokens[0]);
        assertFalse(has(ev, GameEvent.TOKEN_CAPTURED));
    }

    @Test
    void startCellProtectsTheOpponent() {
        u1.tokens[0] = 24;
        u2.tokens[0] = 0; // yellow sitting on its own start (global 26)
        dice.with(2);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L);
        assertEquals(26, (Color.RED.startIndex() + u1.tokens[0]) % 52);
        assertEquals(0, u2.tokens[0]);
    }

    @Test
    void stackedOpponentTokensAreAllCaptured() {
        u1.tokens[0] = 3;
        u2.tokens[0] = 31;
        u2.tokens[1] = 31;
        dice.with(2);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L);
        assertEquals(Board.BASE, u2.tokens[0]);
        assertEquals(Board.BASE, u2.tokens[1]);
    }

    // ------------------------------------------------------------------- home

    @Test
    void overshootingHomeGivesNoMoveAndPassesTurn() {
        u1.tokens[0] = 54;
        dice.with(3);
        List<GameEvent> ev = engine.rollDice(g, "u1", 0L);
        assertTrue(has(ev, GameEvent.NO_MOVES));
        assertEquals("u2", g.current().userId);
        assertEquals(54, u1.tokens[0]);
    }

    @Test
    void exactRollFinishesATokenAndGrantsExtraTurn() {
        u1.tokens[0] = 54;
        dice.with(2);
        engine.rollDice(g, "u1", 0L);
        List<GameEvent> ev = engine.moveToken(g, "u1", 0, 0L);
        assertEquals(Board.HOME, u1.tokens[0]);
        assertTrue(has(ev, GameEvent.TOKEN_HOME));
        assertEquals("u1", g.current().userId);
    }

    @Test
    void finishedTokensAreNotOfferedAsMoves() {
        u1.tokens = new int[] {Board.HOME, 20, Board.BASE, Board.BASE};
        dice.with(3);
        engine.rollDice(g, "u1", 0L);
        assertEquals(List.of(1), g.validMoves);
    }

    // -------------------------------------------------------------------- win

    @Test
    void bringingTheFourthTokenHomeWinsTheMatch() {
        u1.tokens = new int[] {56, 56, 56, 54};
        u2.tokens = new int[] {10, 0, Board.BASE, Board.BASE};
        dice.with(2);
        engine.rollDice(g, "u1", 0L);
        List<GameEvent> ev = engine.moveToken(g, "u1", 3, 0L);
        assertTrue(g.isFinished());
        assertEquals("u1", g.winnerUserId);
        assertEquals(1, u1.rank);
        assertEquals(2, u2.rank);
        assertTrue(has(ev, GameEvent.MATCH_FINISHED));
    }

    @Test
    void noActionsAreAllowedAfterTheMatchEnds() {
        u1.tokens = new int[] {56, 56, 56, 54};
        dice.with(2, 4);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 3, 0L);
        EngineException e = assertThrows(EngineException.class, () -> engine.rollDice(g, "u1", 0L));
        assertEquals("MATCH_FINISHED", e.code());
        assertThrows(EngineException.class, () -> engine.rollDice(g, "u2", 0L));
    }

    // -------------------------------------------------------------- validation

    @Test
    void rollingOutOfTurnIsRejected() {
        dice.with(6);
        EngineException e = assertThrows(EngineException.class, () -> engine.rollDice(g, "u2", 0L));
        assertEquals("NOT_YOUR_TURN", e.code());
    }

    @Test
    void strangersCannotAct() {
        dice.with(6);
        EngineException e = assertThrows(EngineException.class, () -> engine.rollDice(g, "intruder", 0L));
        assertEquals("NOT_A_PLAYER", e.code());
        assertThrows(EngineException.class, () -> engine.moveToken(g, "intruder", 0, 0L));
    }

    @Test
    void cannotMoveBeforeRolling() {
        EngineException e = assertThrows(EngineException.class, () -> engine.moveToken(g, "u1", 0, 0L));
        assertEquals("NOT_AWAITING_MOVE", e.code());
    }

    @Test
    void cannotRollTwiceInARow() {
        u1.tokens[0] = 10;
        dice.with(4, 4);
        engine.rollDice(g, "u1", 0L);
        EngineException e = assertThrows(EngineException.class, () -> engine.rollDice(g, "u1", 0L));
        assertEquals("DICE_ALREADY_ROLLED", e.code());
    }

    @Test
    void movingAnIllegalTokenIsRejectedAndStateIsUnchanged() {
        u1.tokens = new int[] {Board.BASE, 20, Board.BASE, Board.BASE};
        dice.with(3);
        engine.rollDice(g, "u1", 0L);
        EngineException e = assertThrows(EngineException.class, () -> engine.moveToken(g, "u1", 0, 0L));
        assertEquals("ILLEGAL_MOVE", e.code());
        assertThrows(EngineException.class, () -> engine.moveToken(g, "u1", 9, 0L));
        assertEquals(20, u1.tokens[1]);
        assertTrue(g.awaitingMove);
    }

    @Test
    void movingTwiceWithOneRollIsRejected() {
        u1.tokens[0] = 10;
        dice.with(4);
        engine.rollDice(g, "u1", 0L);
        engine.moveToken(g, "u1", 0, 0L);
        assertThrows(EngineException.class, () -> engine.moveToken(g, "u1", 0, 0L));
    }

    // --------------------------------------------------------------- sequence

    @Test
    void eventSequenceNumbersIncreaseStrictlyAndIdsAreUnique() {
        u1.tokens[0] = 10;
        dice.with(4);
        List<GameEvent> all = new java.util.ArrayList<>(engine.rollDice(g, "u1", 0L));
        all.addAll(engine.moveToken(g, "u1", 0, 0L));
        long last = 0;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (GameEvent e : all) {
            assertTrue(e.seq > last);
            last = e.seq;
            assertTrue(ids.add(e.eventId));
        }
    }

    // ------------------------------------------------------ forfeit / timeouts

    @Test
    void lastPlayerStandingWinsWhenAnOpponentForfeits() {
        List<GameEvent> ev = engine.forfeit(g, "u1", 0L);
        assertTrue(g.isFinished());
        assertEquals("u2", g.winnerUserId);
        assertTrue(has(ev, GameEvent.PLAYER_FORFEITED));
        assertTrue(has(ev, GameEvent.MATCH_FINISHED));
    }

    @Test
    void forfeitInFourPlayerGameKeepsMatchRunningAndPassesTurn() {
        GameState four = GameFixtures.fourPlayers(engine);
        engine.forfeit(four, "u1", 0L); // u1 holds the current turn
        assertFalse(four.isFinished());
        assertEquals("u2", four.current().userId);
        // u1 is skipped from now on
        four.turnIndex = 3;
        dice.with(2);
        engine.rollDice(four, "u4", 0L);
        assertEquals("u2", four.current().userId);
    }

    @Test
    void timeoutBeforeDeadlineDoesNothing() {
        long deadline = g.turnDeadlineEpochMs;
        List<GameEvent> ev = engine.handleTimeout(g, deadline - 1);
        assertTrue(ev.isEmpty());
        assertEquals(0, u1.missedTurns);
    }

    @Test
    void timeoutAutoRollsAndAutoMovesForTheIdlePlayer() {
        u1.tokens[0] = 10;
        dice.with(4);
        List<GameEvent> ev = engine.handleTimeout(g, g.turnDeadlineEpochMs + 1);
        assertTrue(has(ev, GameEvent.TURN_TIMEOUT));
        assertTrue(has(ev, GameEvent.DICE_ROLLED));
        assertEquals(14, u1.tokens[0]);
        assertEquals(1, u1.missedTurns);
        assertEquals("u2", g.current().userId);
    }

    @Test
    void autoPlayPrefersCapturesOverPlainMoves() {
        u1.tokens[0] = 3;   // would capture on 5
        u1.tokens[1] = 20;  // plain move
        u2.tokens[0] = 31;  // global 5
        dice.with(2);
        engine.handleTimeout(g, g.turnDeadlineEpochMs + 1);
        assertEquals(5, u1.tokens[0]);
        assertEquals(Board.BASE, u2.tokens[0]);
        assertEquals(20, u1.tokens[1]);
    }

    @Test
    void repeatedTimeoutsForfeitThePlayer() {
        GameState four = GameFixtures.fourPlayers(engine);
        four.turnIndex = 0;
        PlayerState idle = four.player("u1");
        idle.missedTurns = 2; // the next timeout is the third
        List<GameEvent> ev = engine.handleTimeout(four, four.turnDeadlineEpochMs + 1);
        assertTrue(idle.forfeited);
        assertTrue(has(ev, GameEvent.PLAYER_FORFEITED));
        assertEquals("u2", four.current().userId);
    }

    @Test
    void voluntaryActionResetsMissedTurnCounter() {
        u1.missedTurns = 2;
        dice.with(3);
        engine.rollDice(g, "u1", 0L);
        assertEquals(0, u1.missedTurns);
    }

    @Test
    void connectionEventsToggleTheConnectedFlag() {
        GameEvent off = engine.connectionEvent(g, "u1", false, 0L);
        assertFalse(u1.connected);
        assertEquals(GameEvent.PLAYER_DISCONNECTED, off.type);
        GameEvent on = engine.connectionEvent(g, "u1", true, 0L);
        assertTrue(u1.connected);
        assertEquals(GameEvent.PLAYER_RECONNECTED, on.type);
    }
}

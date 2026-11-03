package com.ludoarena.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludoarena.game.GameFixtures.ScriptedDice;
import com.ludoarena.game.model.Board;
import com.ludoarena.game.model.EngineException;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.MoveOutcome;
import com.ludoarena.game.model.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenMovementServiceTest {
    private TokenMovementService movement;
    private GameState g;
    private PlayerState red;
    private PlayerState yellow;

    @BeforeEach
    void setUp() {
        LudoRulesService rules = new LudoRulesService();
        movement = new TokenMovementService(rules);
        g = GameFixtures.twoPlayers(GameFixtures.engine(new ScriptedDice()), new GameRules());
        red = g.player("u1");
        yellow = g.player("u2");
    }

    @Test
    void movesTokenForwardByDiceValue() {
        red.tokens[2] = 10;
        MoveOutcome o = movement.apply(g, red, 2, 4);
        assertEquals(14, red.tokens[2]);
        assertEquals(10, o.from());
        assertEquals(14, o.to());
        assertFalse(o.leftBase());
        assertTrue(o.captured().isEmpty());
    }

    @Test
    void leavingBaseLandsOnStartCell() {
        MoveOutcome o = movement.apply(g, red, 0, 6);
        assertEquals(0, red.tokens[0]);
        assertTrue(o.leftBase());
    }

    @Test
    void capturedTokenGoesBackToBase() {
        red.tokens[0] = 3;
        yellow.tokens[1] = 31; // global 5
        MoveOutcome o = movement.apply(g, red, 0, 2);
        assertEquals(1, o.captured().size());
        assertEquals(Board.BASE, yellow.tokens[1]);
        assertEquals(5, red.tokens[0]);
    }

    @Test
    void reachingExactlyHomeIsFlagged() {
        red.tokens[0] = 53;
        MoveOutcome o = movement.apply(g, red, 0, 3);
        assertTrue(o.reachedHome());
        assertEquals(Board.HOME, red.tokens[0]);
    }

    @Test
    void overshootingHomeIsRejectedAndLeavesStateUntouched() {
        red.tokens[0] = 54;
        assertThrows(EngineException.class, () -> movement.apply(g, red, 0, 3));
        assertEquals(54, red.tokens[0]);
    }

    @Test
    void movingTokenInBaseWithoutSixIsRejected() {
        assertThrows(EngineException.class, () -> movement.apply(g, red, 0, 5));
    }

    @Test
    void tokenIndexOutOfRangeIsRejected() {
        assertThrows(EngineException.class, () -> movement.apply(g, red, 4, 6));
        assertThrows(EngineException.class, () -> movement.apply(g, red, -1, 6));
    }

    @Test
    void finishedTokenCannotMoveAgain() {
        red.tokens[0] = Board.HOME;
        assertThrows(EngineException.class, () -> movement.apply(g, red, 0, 1));
    }
}

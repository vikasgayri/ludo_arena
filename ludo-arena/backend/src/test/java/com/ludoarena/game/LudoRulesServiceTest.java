package com.ludoarena.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludoarena.game.GameFixtures.ScriptedDice;
import com.ludoarena.game.model.Board;
import com.ludoarena.game.model.Color;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.game.model.TokenRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class LudoRulesServiceTest {
    private final LudoRulesService rules = new LudoRulesService();

    @Test
    void globalIndexMapsRelativeStepsAndWrapsAround() {
        assertEquals(0, rules.globalIndex(Color.RED, 0));
        assertEquals(26, rules.globalIndex(Color.YELLOW, 0));
        assertEquals(7, rules.globalIndex(Color.BLUE, 20));
        assertEquals(11, rules.globalIndex(Color.GREEN, 50));
        assertEquals(-1, rules.globalIndex(Color.RED, Board.BASE));
        assertEquals(-1, rules.globalIndex(Color.RED, 51));
        assertEquals(-1, rules.globalIndex(Color.RED, Board.HOME));
    }

    @Test
    void safeCellsAreStartsAndStars() {
        for (int safe : new int[] {0, 8, 13, 21, 26, 34, 39, 47}) assertTrue(rules.isSafe(safe));
        for (int normal : new int[] {1, 5, 12, 25, 50}) assertFalse(rules.isSafe(normal));
    }

    @Test
    void sixIsRequiredToLeaveBase() {
        for (int d = 1; d <= 5; d++) assertFalse(rules.canMove(Board.BASE, d));
        assertTrue(rules.canMove(Board.BASE, 6));
        assertEquals(0, rules.targetSteps(Board.BASE, 6));
    }

    @Test
    void exactRollIsRequiredToFinish() {
        assertTrue(rules.canMove(50, 6));
        assertTrue(rules.canMove(54, 2));
        assertFalse(rules.canMove(54, 3));
        assertFalse(rules.canMove(55, 2));
        assertTrue(rules.canMove(55, 1));
        assertFalse(rules.canMove(Board.HOME, 1));
    }

    @Test
    void invalidDiceValuesAreRejected() {
        assertFalse(rules.canMove(10, 0));
        assertFalse(rules.canMove(10, 7));
        assertFalse(rules.canMove(10, -1));
    }

    @Test
    void validMovesListsOnlyLegalTokens() {
        PlayerState p = new PlayerState();
        p.color = Color.RED;
        p.tokens = new int[] {Board.BASE, 10, Board.HOME, 54};
        assertEquals(List.of(1), rules.validMoves(p, 4));
        assertEquals(List.of(0, 1), rules.validMoves(p, 6));
        assertEquals(List.of(1, 3), rules.validMoves(p, 2));
    }

    @Test
    void captureTargetsFindEveryOpponentTokenOnTheCell() {
        GameState g = GameFixtures.twoPlayers(GameFixtures.engine(new ScriptedDice()), new GameRules());
        PlayerState red = g.player("u1");
        PlayerState yellow = g.player("u2");
        yellow.tokens[0] = 31; // global 5
        yellow.tokens[1] = 31; // global 5 (stacked)
        yellow.tokens[2] = 32; // global 6
        List<TokenRef> hit = rules.captureTargets(g, red, 5);
        assertEquals(2, hit.size());
        assertTrue(rules.captureTargets(g, red, 7).isEmpty());
    }

    @Test
    void ownTokensAreNeverCaptured() {
        GameState g = GameFixtures.twoPlayers(GameFixtures.engine(new ScriptedDice()), new GameRules());
        PlayerState red = g.player("u1");
        red.tokens[1] = 5;
        assertTrue(rules.captureTargets(g, red, 5).isEmpty());
    }

    @Test
    void homeColumnIsNeverShared() {
        GameState g = GameFixtures.twoPlayers(GameFixtures.engine(new ScriptedDice()), new GameRules());
        PlayerState red = g.player("u1");
        PlayerState yellow = g.player("u2");
        yellow.tokens[0] = 52;
        assertTrue(rules.captureTargets(g, red, 52).isEmpty());
    }

    @Test
    void winnerNeedsAllFourTokensHome() {
        PlayerState p = new PlayerState();
        p.tokens = new int[] {56, 56, 56, 55};
        assertFalse(rules.hasWon(p));
        p.tokens[3] = 56;
        assertTrue(rules.hasWon(p));
    }
}

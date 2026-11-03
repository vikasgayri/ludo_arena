package com.ludoarena.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludoarena.game.GameFixtures.ScriptedDice;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.MoveOutcome;
import com.ludoarena.game.model.TokenRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnManagerServiceTest {
    private final TurnManagerService turns = new TurnManagerService();

    @Test
    void nextTurnRotatesThroughPlayersAndSkipsForfeited() {
        GameState g = GameFixtures.fourPlayers(GameFixtures.engine(new ScriptedDice()));
        g.turnIndex = 0;
        g.players.get(1).forfeited = true;
        turns.nextTurn(g, 1000L);
        assertEquals(2, g.turnIndex);
        turns.nextTurn(g, 2000L);
        assertEquals(3, g.turnIndex);
        turns.nextTurn(g, 3000L);
        assertEquals(0, g.turnIndex);
    }

    @Test
    void nextTurnClearsDiceAndRestartsTimer() {
        GameState g = GameFixtures.twoPlayers(GameFixtures.engine(new ScriptedDice()), new GameRules());
        g.diceValue = 4;
        g.awaitingMove = true;
        g.consecutiveSixes = 2;
        turns.nextTurn(g, 5000L);
        assertNull(g.diceValue);
        assertFalse(g.awaitingMove);
        assertEquals(0, g.consecutiveSixes);
        assertEquals(5000L + 30_000L, g.turnDeadlineEpochMs);
    }

    @Test
    void extraTurnRulesAreConfigurable() {
        GameRules r = new GameRules();
        MoveOutcome plain = new MoveOutcome(0, 5, 8, List.of(), false, false);
        MoveOutcome capture = new MoveOutcome(0, 5, 8, List.of(new TokenRef("u2", 0)), false, false);
        MoveOutcome home = new MoveOutcome(0, 53, 56, List.of(), true, false);

        assertFalse(turns.grantsExtraTurn(r, 3, plain));
        assertTrue(turns.grantsExtraTurn(r, 6, plain));
        assertTrue(turns.grantsExtraTurn(r, 3, capture));
        assertTrue(turns.grantsExtraTurn(r, 3, home));

        r.extraTurnOnSix = false;
        r.extraTurnOnCapture = false;
        r.extraTurnOnHome = false;
        assertFalse(turns.grantsExtraTurn(r, 6, plain));
        assertFalse(turns.grantsExtraTurn(r, 3, capture));
        assertFalse(turns.grantsExtraTurn(r, 3, home));
    }
}

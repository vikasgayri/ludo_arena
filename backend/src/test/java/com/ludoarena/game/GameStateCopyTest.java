package com.ludoarena.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import org.junit.jupiter.api.Test;

/** The background writer persists copies; a copy must never share mutable state with the live game. */
class GameStateCopyTest {

    @Test
    void copyIsDeepAndIndependent() {
        GameFixtures.ScriptedDice dice = new GameFixtures.ScriptedDice();
        LudoEngineService engine = GameFixtures.engine(dice);
        GameState live = GameFixtures.fourPlayers(engine);
        dice.with(6);
        engine.rollDice(live, live.current().userId, 1L);

        GameState snap = live.copy();
        assertEquals(live.seq, snap.seq);
        assertEquals(live.validMoves, snap.validMoves);
        assertEquals(live.diceValue, snap.diceValue);
        assertNotSame(live.players, snap.players);
        assertNotSame(live.players.get(0), snap.players.get(0));
        assertNotSame(live.players.get(0).tokens, snap.players.get(0).tokens);
        assertNotSame(live.rules, snap.rules);

        // keep playing on the live state: the snapshot must not change
        int before = snap.current().tokens[0];
        engine.moveToken(live, live.current().userId, live.validMoves.get(0), 2L);
        assertEquals(before, snap.players.get(snap.turnIndex).tokens[0]);
        assertFalse(snap.validMoves.isEmpty());
        snap.players.get(1).tokens[2] = 40;
        assertEquals(-1, live.players.get(1).tokens[2]);
    }

    @Test
    void playerCopyKeepsEveryField() {
        PlayerState p = new PlayerState();
        p.userId = "u";
        p.name = "N";
        p.tokens = new int[] {1, 2, 3, 4};
        p.forfeited = true;
        p.missedTurns = 2;
        p.rank = 3;
        p.bot = true;
        p.botDifficulty = "HARD";
        p.reconnectTokenHash = "h";
        PlayerState c = p.copy();
        assertEquals(p.toString(), c.toString());
        assertEquals("HARD", c.botDifficulty);
        assertEquals("h", c.reconnectTokenHash);
        assertEquals(2, c.missedTurns);
        GameRules r = new GameRules();
        r.turnTimeoutSeconds = 45;
        assertEquals(45, r.copy().turnTimeoutSeconds);
    }
}

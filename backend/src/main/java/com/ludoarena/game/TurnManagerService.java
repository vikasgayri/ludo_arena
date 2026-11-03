package com.ludoarena.game;

import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.MoveOutcome;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

@Service
public class TurnManagerService {

    /** Clears the dice and restarts the turn timer for the player whose turn it currently is. */
    public void resetTurn(GameState game, long nowMs) {
        game.diceValue = null;
        game.awaitingMove = false;
        game.validMoves = new ArrayList<>();
        game.turnDeadlineEpochMs = nowMs + game.rules.turnTimeoutSeconds * 1000L;
    }

    /** Passes the turn to the next non-forfeited player. */
    public void nextTurn(GameState game, long nowMs) {
        int n = game.players.size();
        for (int k = 1; k <= n; k++) {
            int idx = (game.turnIndex + k) % n;
            if (!game.players.get(idx).forfeited) {
                game.turnIndex = idx;
                break;
            }
        }
        game.consecutiveSixes = 0;
        resetTurn(game, nowMs);
    }

    /** Whether the player who just moved gets to roll again. */
    public boolean grantsExtraTurn(GameRules rules, int dice, MoveOutcome outcome) {
        if (dice == 6 && rules.extraTurnOnSix) return true;
        if (!outcome.captured().isEmpty() && rules.extraTurnOnCapture) return true;
        return outcome.reachedHome() && rules.extraTurnOnHome;
    }
}

package com.ludoarena.game;

import com.ludoarena.game.model.Board;
import com.ludoarena.game.model.Color;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.game.model.TokenRef;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Pure rule queries. Never mutates state. */
@Service
public class LudoRulesService {

    public boolean isSafe(int globalIndex) {
        return Board.SAFE_CELLS.contains(globalIndex);
    }

    public boolean isOnTrack(int steps) {
        return steps >= 0 && steps <= Board.LAST_TRACK_STEP;
    }

    /** Global track index for a relative step count, or -1 when the token is not on the shared track. */
    public int globalIndex(Color color, int steps) {
        if (!isOnTrack(steps)) return -1;
        return (color.startIndex() + steps) % Board.TRACK_LENGTH;
    }

    /** Whether the token can legally use this dice value (exact roll needed to finish, 6 needed to leave base). */
    public boolean canMove(int steps, int dice) {
        if (dice < 1 || dice > 6) return false;
        if (steps == Board.HOME) return false;
        if (steps == Board.BASE) return dice == 6;
        return steps + dice <= Board.HOME;
    }

    /** Destination steps. Only meaningful when canMove(steps, dice) is true. */
    public int targetSteps(int steps, int dice) {
        return steps == Board.BASE ? 0 : steps + dice;
    }

    public List<Integer> validMoves(PlayerState player, int dice) {
        List<Integer> moves = new ArrayList<>();
        for (int i = 0; i < player.tokens.length; i++) {
            if (canMove(player.tokens[i], dice)) moves.add(i);
        }
        return moves;
    }

    /**
     * Opponent tokens that would be captured by a token of {@code mover} landing on {@code newSteps}.
     * Safe cells (start cells and stars) protect every token on them.
     */
    public List<TokenRef> captureTargets(GameState game, PlayerState mover, int newSteps) {
        List<TokenRef> captured = new ArrayList<>();
        int g = globalIndex(mover.color, newSteps);
        if (g < 0 || isSafe(g)) return captured;
        for (PlayerState other : game.players) {
            if (other == mover || other.forfeited) continue;
            for (int i = 0; i < other.tokens.length; i++) {
                if (globalIndex(other.color, other.tokens[i]) == g) {
                    captured.add(new TokenRef(other.userId, i));
                }
            }
        }
        return captured;
    }

    public boolean hasWon(PlayerState player) {
        return player.finishedTokens() == Board.TOKENS_PER_PLAYER;
    }
}

package com.ludoarena.game.bot;

import com.ludoarena.game.LudoRulesService;
import com.ludoarena.game.model.Board;
import com.ludoarena.game.model.EngineException;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.game.model.TokenRef;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;

/**
 * Decides which token a computer player moves. It only CHOOSES among the moves the engine already computed as legal
 * ({@link GameState#validMoves}); it never rolls dice and never changes state. The caller must apply the choice through
 * the normal engine entry point ({@code LudoEngineService.moveToken}), so bots obey exactly the same rules and
 * validation as humans.
 */
@Service
public class BotBrain {
    private final LudoRulesService rules;
    private RandomGenerator random = new Random();

    public BotBrain(LudoRulesService rules) {
        this.rules = rules;
    }

    /** Tests only: a seeded generator makes EASY choices reproducible. (Single constructor keeps Spring wiring simple.) */
    BotBrain withRandom(RandomGenerator random) {
        this.random = random;
        return this;
    }

    /** @return the token index to move; always one of {@code g.validMoves} */
    public int chooseMove(GameState g, PlayerState me, BotDifficulty difficulty) {
        if (!g.awaitingMove || g.diceValue == null || g.validMoves == null || g.validMoves.isEmpty()) {
            throw new EngineException("NOT_AWAITING_MOVE", "There is no move to choose");
        }
        if (g.current() != me) {
            throw new EngineException("NOT_YOUR_TURN", "It is not this bot's turn");
        }
        List<Integer> moves = g.validMoves;
        if (moves.size() == 1) return moves.get(0);
        return switch (difficulty) {
            case EASY -> moves.get(random.nextInt(moves.size()));
            case MEDIUM -> medium(g, me, g.diceValue, moves);
            case HARD -> hard(g, me, g.diceValue, moves);
        };
    }

    // ---------------------------------------------------------------- MEDIUM

    private int medium(GameState g, PlayerState me, int dice, List<Integer> moves) {
        int best = -1;
        int bestVictim = -1;
        for (int idx : moves) { // 1. capture, preferring the most advanced victim
            int to = rules.targetSteps(me.tokens[idx], dice);
            for (TokenRef v : rules.captureTargets(g, me, to)) {
                int victimSteps = g.player(v.userId()).tokens[v.tokenIndex()];
                if (victimSteps > bestVictim) {
                    bestVictim = victimSteps;
                    best = idx;
                }
            }
        }
        if (best >= 0) return best;
        for (int idx : moves) { // 2. bring a token home
            if (rules.targetSteps(me.tokens[idx], dice) == Board.HOME) return idx;
        }
        for (int idx : moves) { // 3. unlock a token from base
            if (me.tokens[idx] == Board.BASE) return idx;
        }
        // 4. furthest advance that does not land in reach of an opponent; otherwise the furthest advance
        int safeBest = -1;
        int anyBest = -1;
        for (int idx : moves) {
            int to = rules.targetSteps(me.tokens[idx], dice);
            if (anyBest < 0 || to > rules.targetSteps(me.tokens[anyBest], dice)) anyBest = idx;
            if (threats(g, me, to) == 0 && (safeBest < 0 || to > rules.targetSteps(me.tokens[safeBest], dice))) safeBest = idx;
        }
        return safeBest >= 0 ? safeBest : anyBest;
    }

    // ------------------------------------------------------------------ HARD

    private int hard(GameState g, PlayerState me, int dice, List<Integer> moves) {
        int best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (int idx : moves) {
            int score = score(g, me, idx, dice);
            int to = rules.targetSteps(me.tokens[idx], dice);
            int bestTo = rules.targetSteps(me.tokens[best], dice);
            if (score > bestScore || (score == bestScore && to > bestTo)) {
                bestScore = score;
                best = idx;
            }
        }
        return best;
    }

    /** Heuristic value of moving token {@code idx}. Package-private for tests. */
    int score(GameState g, PlayerState me, int idx, int dice) {
        int from = me.tokens[idx];
        int to = rules.targetSteps(from, dice);
        int score = 0;

        for (TokenRef v : rules.captureTargets(g, me, to)) {
            score += 600 + 8 * Math.max(0, g.player(v.userId()).tokens[v.tokenIndex()]);
        }
        if (to == Board.HOME) score += 700;
        if (from == Board.BASE) score += 450;
        if (from <= Board.LAST_TRACK_STEP && to >= Board.HOME_COLUMN_START) score += 300; // enters the private home column

        if (rules.isOnTrack(to) && rules.isSafe(rules.globalIndex(me.color, to))) score += 120;

        int threatAfter = threats(g, me, to);
        if (threatAfter > 0) score -= (180 + 6 * to) * Math.min(threatAfter, 2);

        int threatBefore = from == Board.BASE ? 0 : threats(g, me, from);
        if (threatBefore > 0 && threatAfter == 0) score += 140 + 6 * from; // escapes a chaser

        score += 2 * (to - Math.max(from, 0));
        return score;
    }

    /**
     * Number of opponent tokens that could land on my token at {@code steps} with a single roll. Zero for base, home
     * column, finished tokens and safe cells.
     */
    int threats(GameState g, PlayerState me, int steps) {
        if (!rules.isOnTrack(steps)) return 0;
        int cell = rules.globalIndex(me.color, steps);
        if (rules.isSafe(cell)) return 0;
        int n = 0;
        for (PlayerState other : g.players) {
            if (other == me || other.forfeited) continue;
            for (int s : other.tokens) {
                if (!rules.isOnTrack(s)) continue;
                int distance = Math.floorMod(cell - rules.globalIndex(other.color, s), Board.TRACK_LENGTH);
                if (distance >= 1 && distance <= 6 && s + distance <= Board.LAST_TRACK_STEP) n++;
            }
        }
        return n;
    }
}

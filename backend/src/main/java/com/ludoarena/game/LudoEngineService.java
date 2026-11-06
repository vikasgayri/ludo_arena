package com.ludoarena.game;

import com.ludoarena.game.model.Board;
import com.ludoarena.game.model.Color;
import com.ludoarena.game.model.EngineException;
import com.ludoarena.game.model.GameEvent;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.MoveOutcome;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.game.model.SeatInfo;
import com.ludoarena.game.model.TokenRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Authoritative Ludo game engine. Contains no web, WebSocket or persistence code:
 * every method validates the action against the current state, mutates the state in place
 * and returns the events that describe what happened.
 */
@Service
public class LudoEngineService {
    private final DiceService dice;
    private final LudoRulesService rules;
    private final TokenMovementService movement;
    private final TurnManagerService turns;

    public LudoEngineService(DiceService dice, LudoRulesService rules, TokenMovementService movement, TurnManagerService turns) {
        this.dice = dice;
        this.rules = rules;
        this.movement = movement;
        this.turns = turns;
    }

    // ------------------------------------------------------------------ setup

    public GameState startGame(List<SeatInfo> seats, GameRules gameRules, long nowMs) {
        int n = seats.size();
        if (n < 2 || n > 4) {
            throw new EngineException("INVALID_PLAYER_COUNT", "Ludo supports 2 to 4 players");
        }
        // 2 players sit opposite each other; 3 players take three consecutive colours
        Color[] colors = n == 2 ? new Color[] {Color.RED, Color.YELLOW}
                : n == 3 ? new Color[] {Color.RED, Color.GREEN, Color.YELLOW}
                : new Color[] {Color.RED, Color.GREEN, Color.YELLOW, Color.BLUE};
        GameState g = new GameState();
        g.rules = gameRules.copy();
        for (int i = 0; i < n; i++) {
            SeatInfo s = seats.get(i);
            PlayerState p = new PlayerState();
            p.userId = s.userId();
            p.name = s.name();
            p.avatar = s.avatar();
            p.color = colors[i];
            p.seat = i;
            g.players.add(p);
        }
        g.turnIndex = dice.pick(n);
        turns.resetTurn(g, nowMs);
        return g;
    }

    public GameEvent startedEvent(GameState g, long nowMs) {
        return event(g, GameEvent.MATCH_STARTED, null, nowMs).put("firstUserId", g.current().userId);
    }

    // ---------------------------------------------------------------- actions

    public List<GameEvent> rollDice(GameState g, String userId, long nowMs) {
        requireActive(g);
        PlayerState p = requireTurn(g, userId);
        if (g.awaitingMove || g.diceValue != null) {
            throw new EngineException("DICE_ALREADY_ROLLED", "You already rolled; move a token");
        }
        p.missedTurns = 0;
        return doRoll(g, p, dice.roll(), nowMs);
    }

    public List<GameEvent> moveToken(GameState g, String userId, int tokenIndex, long nowMs) {
        requireActive(g);
        PlayerState p = requireTurn(g, userId);
        if (!g.awaitingMove || g.diceValue == null) {
            throw new EngineException("NOT_AWAITING_MOVE", "Roll the dice first");
        }
        if (!g.validMoves.contains(tokenIndex)) {
            throw new EngineException("ILLEGAL_MOVE", "Token " + tokenIndex + " cannot be moved with this roll");
        }
        p.missedTurns = 0;
        return applyMove(g, p, tokenIndex, nowMs);
    }

    /** Removes a player from the game (leave / too many missed turns). The last remaining player wins. */
    public List<GameEvent> forfeit(GameState g, String userId, long nowMs) {
        List<GameEvent> events = new ArrayList<>();
        PlayerState p = g.player(userId);
        if (p == null) throw new EngineException("NOT_A_PLAYER", "You are not part of this match");
        if (g.isFinished() || p.forfeited) return events;
        boolean wasCurrent = g.current() == p;
        p.forfeited = true;
        p.connected = false;
        java.util.Arrays.fill(p.tokens, Board.BASE);
        events.add(event(g, GameEvent.PLAYER_FORFEITED, userId, nowMs));
        if (g.activePlayers() <= 1) {
            PlayerState winner = g.players.stream().filter(x -> !x.forfeited).findFirst().orElse(null);
            finish(g, winner, events, nowMs);
        } else if (wasCurrent) {
            passTurn(g, events, nowMs);
        }
        return events;
    }

    /** Auto-plays an overdue turn. No-op when the deadline has not passed. */
    public List<GameEvent> handleTimeout(GameState g, long nowMs) {
        List<GameEvent> events = new ArrayList<>();
        if (g.isFinished() || nowMs < g.turnDeadlineEpochMs) return events;
        PlayerState p = g.current();
        p.missedTurns++;
        events.add(event(g, GameEvent.TURN_TIMEOUT, p.userId, nowMs).put("missedTurns", p.missedTurns));
        if (p.missedTurns >= g.rules.maxMissedTurns) {
            events.addAll(forfeit(g, p.userId, nowMs));
            return events;
        }
        if (!g.awaitingMove) {
            events.addAll(doRoll(g, p, dice.roll(), nowMs));
        }
        if (g.awaitingMove && !g.isFinished() && g.current() == p) {
            events.addAll(applyMove(g, p, chooseAutoMove(g, p), nowMs));
        }
        return events;
    }

    public GameEvent connectionEvent(GameState g, String userId, boolean connected, long nowMs) {
        PlayerState p = g.player(userId);
        if (p == null) throw new EngineException("NOT_A_PLAYER", "You are not part of this match");
        p.connected = connected;
        return event(g, connected ? GameEvent.PLAYER_RECONNECTED : GameEvent.PLAYER_DISCONNECTED, userId, nowMs);
    }

    // --------------------------------------------------------------- internals

    private List<GameEvent> doRoll(GameState g, PlayerState p, int value, long nowMs) {
        List<GameEvent> events = new ArrayList<>();
        g.diceValue = value;
        g.lastDiceValue = value;
        g.lastDiceUserId = p.userId;
        g.consecutiveSixes = value == 6 ? g.consecutiveSixes + 1 : 0;
        events.add(event(g, GameEvent.DICE_ROLLED, p.userId, nowMs).put("value", value));

        int max = g.rules.maxConsecutiveSixes;
        if (value == 6 && max > 0 && g.consecutiveSixes >= max) {
            events.add(event(g, GameEvent.TRIPLE_SIX, p.userId, nowMs));
            passTurn(g, events, nowMs);
            return events;
        }
        List<Integer> moves = rules.validMoves(p, value);
        if (moves.isEmpty()) {
            events.add(event(g, GameEvent.NO_MOVES, p.userId, nowMs).put("value", value));
            passTurn(g, events, nowMs);
            return events;
        }
        g.validMoves = moves;
        g.awaitingMove = true;
        g.turnDeadlineEpochMs = nowMs + g.rules.turnTimeoutSeconds * 1000L;
        return events;
    }

    private List<GameEvent> applyMove(GameState g, PlayerState p, int tokenIndex, long nowMs) {
        List<GameEvent> events = new ArrayList<>();
        int roll = g.diceValue;
        MoveOutcome o = movement.apply(g, p, tokenIndex, roll);
        events.add(event(g, GameEvent.TOKEN_MOVED, p.userId, nowMs)
                .put("tokenIndex", o.tokenIndex()).put("from", o.from()).put("to", o.to()).put("dice", roll));
        for (TokenRef ref : o.captured()) {
            events.add(event(g, GameEvent.TOKEN_CAPTURED, p.userId, nowMs)
                    .put("victimUserId", ref.userId()).put("tokenIndex", ref.tokenIndex()));
        }
        if (o.reachedHome()) {
            events.add(event(g, GameEvent.TOKEN_HOME, p.userId, nowMs).put("tokenIndex", o.tokenIndex()));
        }
        if (rules.hasWon(p)) {
            finish(g, p, events, nowMs);
            return events;
        }
        if (turns.grantsExtraTurn(g.rules, roll, o)) {
            turns.resetTurn(g, nowMs);
            events.add(event(g, GameEvent.TURN_CHANGED, p.userId, nowMs)
                    .put("nextUserId", p.userId).put("extraTurn", true));
        } else {
            passTurn(g, events, nowMs);
        }
        return events;
    }

    private void passTurn(GameState g, List<GameEvent> events, long nowMs) {
        turns.nextTurn(g, nowMs);
        events.add(event(g, GameEvent.TURN_CHANGED, null, nowMs)
                .put("nextUserId", g.current().userId).put("extraTurn", false));
    }

    private void finish(GameState g, PlayerState winner, List<GameEvent> events, long nowMs) {
        g.status = GameState.Status.FINISHED;
        g.awaitingMove = false;
        g.diceValue = null;
        g.validMoves = new ArrayList<>();
        g.winnerUserId = winner == null ? null : winner.userId;

        List<PlayerState> others = new ArrayList<>(g.players);
        others.remove(winner);
        others.sort(Comparator.comparing((PlayerState x) -> x.forfeited).thenComparing(Comparator.comparingInt(PlayerState::progress).reversed()));
        int rank = 1;
        if (winner != null) winner.rank = rank++;
        for (PlayerState x : others) x.rank = rank++;

        List<java.util.Map<String, Object>> ranking = new ArrayList<>();
        for (PlayerState x : g.players) {
            ranking.add(java.util.Map.of("userId", x.userId, "rank", x.rank));
        }
        events.add(event(g, GameEvent.MATCH_FINISHED, g.winnerUserId, nowMs)
                .put("winnerId", g.winnerUserId == null ? "" : g.winnerUserId).put("ranking", ranking));
    }

    private int chooseAutoMove(GameState g, PlayerState p) {
        int best = g.validMoves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (int idx : g.validMoves) {
            int from = p.tokens[idx];
            int to = rules.targetSteps(from, g.diceValue);
            int score = to;
            if (to == Board.HOME) score += 1000;
            score += 500 * rules.captureTargets(g, p, to).size();
            if (from == Board.BASE) score += 300;
            if (score > bestScore) {
                bestScore = score;
                best = idx;
            }
        }
        return best;
    }

    private void requireActive(GameState g) {
        if (g.isFinished()) throw new EngineException("MATCH_FINISHED", "The match is already finished");
    }

    private PlayerState requireTurn(GameState g, String userId) {
        PlayerState p = g.player(userId);
        if (p == null) throw new EngineException("NOT_A_PLAYER", "You are not part of this match");
        if (p.forfeited) throw new EngineException("PLAYER_FORFEITED", "You have left this match");
        if (g.current() != p) throw new EngineException("NOT_YOUR_TURN", "It is not your turn");
        return p;
    }

    public GameEvent event(GameState g, String type, String userId, long nowMs) {
        GameEvent e = new GameEvent();
        e.eventId = UUID.randomUUID().toString();
        e.seq = ++g.seq;
        e.type = type;
        e.userId = userId;
        e.at = nowMs;
        return e;
    }
}

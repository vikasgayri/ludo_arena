package com.ludoarena.game.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    public enum Status { ACTIVE, FINISHED }

    public Status status = Status.ACTIVE;
    public List<PlayerState> players = new ArrayList<>();
    /** index into players of the player whose turn it is */
    public int turnIndex = 0;
    public Integer diceValue;
    /** last value rolled (kept after a turn passes so clients can keep showing it) */
    public Integer lastDiceValue;
    public String lastDiceUserId;
    /** true after a roll that produced at least one legal move, until the move is made */
    public boolean awaitingMove = false;
    public List<Integer> validMoves = new ArrayList<>();
    public int consecutiveSixes = 0;
    public String winnerUserId;
    public long turnDeadlineEpochMs;
    /** monotonically increasing event counter */
    public long seq = 0;
    public GameRules rules = new GameRules();
    /** when the computer player whose turn it is acts next (persisted, so bot turns survive a restart); null = no bot due */
    public Long botActAtEpochMs;

    /** Deep copy: mutating the copy never affects this state and vice versa. */
    public GameState copy() {
        GameState c = new GameState();
        c.status = status;
        for (PlayerState p : players) c.players.add(p.copy());
        c.turnIndex = turnIndex;
        c.diceValue = diceValue;
        c.lastDiceValue = lastDiceValue;
        c.lastDiceUserId = lastDiceUserId;
        c.awaitingMove = awaitingMove;
        c.validMoves = new ArrayList<>(validMoves);
        c.consecutiveSixes = consecutiveSixes;
        c.winnerUserId = winnerUserId;
        c.turnDeadlineEpochMs = turnDeadlineEpochMs;
        c.seq = seq;
        c.rules = rules == null ? null : rules.copy();
        c.botActAtEpochMs = botActAtEpochMs;
        return c;
    }

    public PlayerState current() {
        return players.get(turnIndex);
    }

    public PlayerState player(String userId) {
        for (PlayerState p : players) {
            if (p.userId.equals(userId)) return p;
        }
        return null;
    }

    public int activePlayers() {
        int n = 0;
        for (PlayerState p : players) {
            if (!p.forfeited) n++;
        }
        return n;
    }

    public boolean isFinished() {
        return status == Status.FINISHED;
    }
}

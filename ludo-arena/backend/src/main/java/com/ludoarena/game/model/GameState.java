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

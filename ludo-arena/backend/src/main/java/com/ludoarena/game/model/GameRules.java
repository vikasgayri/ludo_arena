package com.ludoarena.game.model;

/** Configurable rule switches. A snapshot is stored with every match so rule changes never affect running games. */
public class GameRules {
    public boolean extraTurnOnSix = true;
    public boolean extraTurnOnCapture = true;
    public boolean extraTurnOnHome = true;
    /** Rolling this many sixes in a row forfeits the turn (no move is made). 0 disables the rule. */
    public int maxConsecutiveSixes = 3;
    public int turnTimeoutSeconds = 30;
    /** Consecutive timed-out turns after which a player is forfeited. */
    public int maxMissedTurns = 3;

    public GameRules copy() {
        GameRules r = new GameRules();
        r.extraTurnOnSix = extraTurnOnSix;
        r.extraTurnOnCapture = extraTurnOnCapture;
        r.extraTurnOnHome = extraTurnOnHome;
        r.maxConsecutiveSixes = maxConsecutiveSixes;
        r.turnTimeoutSeconds = turnTimeoutSeconds;
        r.maxMissedTurns = maxMissedTurns;
        return r;
    }
}

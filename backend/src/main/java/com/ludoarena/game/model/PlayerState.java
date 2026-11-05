package com.ludoarena.game.model;

import java.util.Arrays;

public class PlayerState {
    public String userId;
    public String name;
    public String avatar;
    public Color color;
    public int seat;
    public int[] tokens = new int[] {Board.BASE, Board.BASE, Board.BASE, Board.BASE};
    public boolean connected = true;
    public boolean forfeited = false;
    public int missedTurns = 0;
    /** 1 = winner; others ranked when the match ends. 0 = not ranked yet. */
    public int rank = 0;
    /** SHA-256 (hex) of the reconnect token that was issued to this seat. Never sent to clients. */
    public String reconnectTokenHash;
    /** true for a computer-controlled seat ("Play with Computer"); bots never appear in Match.playerIds */
    public boolean bot = false;
    /** EASY / MEDIUM / HARD for bot seats, null for humans */
    public String botDifficulty;

    public int finishedTokens() {
        int n = 0;
        for (int t : tokens) {
            if (t == Board.HOME) n++;
        }
        return n;
    }

    public int progress() {
        int sum = 0;
        for (int t : tokens) {
            sum += Math.max(t, 0);
        }
        return sum;
    }

    @Override
    public String toString() {
        return color + "[" + userId + "]" + Arrays.toString(tokens);
    }
}

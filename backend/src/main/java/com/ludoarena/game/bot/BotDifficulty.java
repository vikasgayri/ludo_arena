package com.ludoarena.game.bot;

/** Strength of a computer opponent. */
public enum BotDifficulty {
    /** Picks any legal move at random. */
    EASY,
    /** Simple priorities: capture, finish, unlock, then the furthest safe advance; avoids obviously exposed cells. */
    MEDIUM,
    /** Scores every legal move: captures, finishing, safety, threats escaped and created, home-column progress. */
    HARD;

    public static BotDifficulty parse(String value) {
        if (value == null) return MEDIUM;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}

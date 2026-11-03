package com.ludoarena.game.model;

import java.util.Set;

/**
 * Board geometry shared by all game services.
 *
 * Token progress ("steps") is relative to the owning colour:
 *   -1        in base
 *   0..50     on the 52-cell main track (global = (colour.start + steps) % 52)
 *   51..55    in the colour's private home column
 *   56        HOME (finished)
 */
public final class Board {
    public static final int TRACK_LENGTH = 52;
    public static final int BASE = -1;
    public static final int LAST_TRACK_STEP = 50;
    public static final int HOME_COLUMN_START = 51;
    public static final int HOME = 56;
    public static final int TOKENS_PER_PLAYER = 4;
    /** Start cells of every colour plus the star cell 8 steps after each start. */
    public static final Set<Integer> SAFE_CELLS = Set.of(0, 8, 13, 21, 26, 34, 39, 47);

    private Board() {
    }
}

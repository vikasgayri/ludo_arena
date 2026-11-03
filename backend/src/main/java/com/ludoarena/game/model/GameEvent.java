package com.ludoarena.game.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable-by-convention record of something that happened in a match. */
public class GameEvent {
    public static final String MATCH_STARTED = "MATCH_STARTED";
    public static final String DICE_ROLLED = "DICE_ROLLED";
    public static final String TOKEN_MOVED = "TOKEN_MOVED";
    public static final String TOKEN_CAPTURED = "TOKEN_CAPTURED";
    public static final String TOKEN_HOME = "TOKEN_HOME";
    public static final String NO_MOVES = "NO_MOVES";
    public static final String TRIPLE_SIX = "TRIPLE_SIX";
    public static final String TURN_CHANGED = "TURN_CHANGED";
    public static final String TURN_TIMEOUT = "TURN_TIMEOUT";
    public static final String PLAYER_FORFEITED = "PLAYER_FORFEITED";
    public static final String PLAYER_DISCONNECTED = "PLAYER_DISCONNECTED";
    public static final String PLAYER_RECONNECTED = "PLAYER_RECONNECTED";
    public static final String MATCH_FINISHED = "MATCH_FINISHED";

    public String eventId;
    public long seq;
    public String type;
    public String userId;
    public Map<String, Object> data = new LinkedHashMap<>();
    public long at;

    public GameEvent put(String key, Object value) {
        data.put(key, value);
        return this;
    }
}

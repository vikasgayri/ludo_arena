package com.ludoarena.game.bot;

/** Identity (id, display name, avatar) of computer seats. Bot ids can never collide with MongoDB ObjectIds. */
public final class BotProfiles {
    public static final String ID_PREFIX = "BOT:";

    private static final String[][] NAMES = {
            {"Rookie Bot", "Lucky Bot", "Sunny Bot"},     // EASY
            {"Clever Bot", "Swift Bot", "Smart Bot"},     // MEDIUM
            {"Master Bot", "Shadow Bot", "Titan Bot"},    // HARD
    };
    private static final String[] AVATARS = {"avatar-10", "avatar-11", "avatar-12"};

    private BotProfiles() {
    }

    /** @param seat 1..3 (seat 0 is always the human) */
    public static String botUserId(String roomId, int seat) {
        return ID_PREFIX + roomId + ":" + seat;
    }

    public static boolean isBotId(String userId) {
        return userId != null && userId.startsWith(ID_PREFIX);
    }

    public static String name(BotDifficulty difficulty, int seat) {
        String[] names = NAMES[difficulty.ordinal()];
        return names[Math.floorMod(seat - 1, names.length)];
    }

    public static String avatar(int seat) {
        return AVATARS[Math.floorMod(seat - 1, AVATARS.length)];
    }
}
